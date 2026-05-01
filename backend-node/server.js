const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const db = require('./database');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*", // En producción se debería especificar el origen (ej. http://localhost:5173)
        methods: ["GET", "POST"]
    }
});

// Almacena los usuarios conectados en memoria para acceso rápido
const connectedUsers = new Map();

io.on('connection', (socket) => {
    console.log(`Nuevo cliente conectado: ${socket.id}`);

    // Evento para identificar al usuario
    socket.on('register_user', (username) => {
        connectedUsers.set(socket.id, username);
        
        // Guardar en la BD
        db.run(`INSERT OR REPLACE INTO users (id, username) VALUES (?, ?)`, [socket.id, username], (err) => {
            if (err) console.error("Error guardando usuario:", err.message);
        });

        console.log(`Usuario registrado: ${username} (${socket.id})`);
        
        // Notificar a todos sobre el nuevo usuario
        io.emit('user_joined', { id: socket.id, username });
        
        // Enviar historial de mensajes recientes al usuario nuevo
        db.all(`SELECT * FROM messages ORDER BY timestamp DESC LIMIT 50`, (err, rows) => {
            if (err) {
                console.error(err.message);
                return;
            }
            socket.emit('message_history', rows.reverse()); // Enviar ordenado de más antiguo a más reciente
        });
        
        // Enviar la lista de usuarios activos
        const usersList = Array.from(connectedUsers, ([id, name]) => ({ id, name }));
        io.emit('active_users', usersList);
    });

    // Evento para recibir y retransmitir mensajes
    socket.on('send_message', (data) => {
        const username = connectedUsers.get(socket.id) || "Anónimo";
        const messageData = {
            sender_id: socket.id,
            sender_name: username,
            content: data.content,
            timestamp: new Date().toISOString()
        };

        // Guardar en BD
        db.run(`INSERT INTO messages (sender_id, sender_name, content) VALUES (?, ?, ?)`, 
            [messageData.sender_id, messageData.sender_name, messageData.content], 
            function(err) {
                if (err) {
                    console.error("Error guardando mensaje:", err.message);
                    return;
                }
                messageData.id = this.lastID;
                // Retransmitir a todos
                io.emit('receive_message', messageData);
        });
    });

    // Evento de desconexión
    socket.on('disconnect', () => {
        const username = connectedUsers.get(socket.id);
        if (username) {
            console.log(`Cliente desconectado: ${username} (${socket.id})`);
            connectedUsers.delete(socket.id);
            
            // Eliminar de usuarios activos de la DB (o marcar como desconectado, pero por simplicidad borramos)
            db.run(`DELETE FROM users WHERE id = ?`, [socket.id]);
            
            io.emit('user_left', { id: socket.id, username });
            
            const usersList = Array.from(connectedUsers, ([id, name]) => ({ id, name }));
            io.emit('active_users', usersList);
        }
    });
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
    console.log(`Servidor de WebSockets corriendo en http://localhost:${PORT}`);
});
