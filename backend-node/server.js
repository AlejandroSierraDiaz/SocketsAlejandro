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
        origin: "*",
        methods: ["GET", "POST"]
    },
    maxHttpBufferSize: 1e7 // 10 MB para imagenes
});

const connectedUsers = new Map(); // socket.id -> { name, avatar }
const globalVoiceChannels = new Map(); // roomName -> Array of {id, name, avatar}

// Asegurar que existe la tabla de perfiles para no perder avatares al desconectar
db.serialize(() => {
    db.run(`CREATE TABLE IF NOT EXISTS profiles (username TEXT PRIMARY KEY, avatar TEXT)`);
});

function broadcastVoiceChannels(ioInstance) {
    const vcState = {};
    for (let [room, users] of globalVoiceChannels.entries()) {
        vcState[room] = users;
    }
    ioInstance.emit('voice_channels_update', vcState);
}

io.on('connection', (socket) => {
    console.log(`Nuevo cliente: ${socket.id}`);

    // ----- AUTENTICACION Y ESTADO -----
    socket.on('register_user', (username) => {
        // Buscar perfil existente
        db.get(`SELECT avatar FROM profiles WHERE username = ?`, [username], (err, row) => {
            const avatar = row ? row.avatar : null;
            
            connectedUsers.set(socket.id, { name: username, avatar: avatar });
            db.run(`INSERT OR IGNORE INTO profiles (username, avatar) VALUES (?, ?)`, [username, avatar]);
            db.run(`INSERT OR REPLACE INTO users (id, username, avatar) VALUES (?, ?, ?)`, [socket.id, username, avatar]);
            
            io.emit('user_joined', { id: socket.id, username, avatar });
            const usersList = Array.from(connectedUsers, ([id, data]) => ({ id, name: data.name, avatar: data.avatar }));
            io.emit('active_users', usersList);
            broadcastVoiceChannels(io);
        });
    });

    socket.on('update_username', (newUsername) => {
        if(connectedUsers.has(socket.id)) {
            const userData = connectedUsers.get(socket.id);
            const oldName = userData.name;
            const currentAvatar = userData.avatar;
            userData.name = newUsername;
            connectedUsers.set(socket.id, userData);
            
            db.run(`INSERT OR REPLACE INTO profiles (username, avatar) VALUES (?, ?)`, [newUsername, currentAvatar]);
            db.run(`UPDATE users SET username = ? WHERE id = ?`, [newUsername, socket.id]);
            
            const usersList = Array.from(connectedUsers, ([id, data]) => ({ id, name: data.name, avatar: data.avatar }));
            io.emit('active_users', usersList);
            io.emit('username_changed', { id: socket.id, newName: newUsername, oldName });
        }
    });

    socket.on('update_avatar', (base64Avatar) => {
        if(connectedUsers.has(socket.id)) {
            const userData = connectedUsers.get(socket.id);
            userData.avatar = base64Avatar;
            connectedUsers.set(socket.id, userData);
            
            db.run(`UPDATE profiles SET avatar = ? WHERE username = ?`, [base64Avatar, userData.name]);
            db.run(`UPDATE users SET avatar = ? WHERE id = ?`, [base64Avatar, socket.id]);
            
            const usersList = Array.from(connectedUsers, ([id, data]) => ({ id, name: data.name, avatar: data.avatar }));
            io.emit('active_users', usersList);
            io.emit('avatar_changed', { username: userData.name, avatar: base64Avatar });
            
            // Actualizar en voice channels si esta
            let changed = false;
            for (let [room, users] of globalVoiceChannels.entries()) {
                const userIndex = users.findIndex(u => u.id === socket.id);
                if (userIndex !== -1) {
                    users[userIndex].avatar = base64Avatar;
                    changed = true;
                }
            }
            if (changed) broadcastVoiceChannels(io);
        }
    });

    // ----- CANALES DE TEXTO Y DMs -----
    socket.on('join_channel', (channelName) => {
        Array.from(socket.rooms).forEach(room => {
            if (room !== socket.id) socket.leave(room);
        });
        
        socket.join(channelName);
        
        // Obtener historial y hacer JOIN con perfiles para tener los avatares incluso de offline
        db.all(`
            SELECT m.*, p.avatar as sender_avatar 
            FROM messages m 
            LEFT JOIN profiles p ON m.sender_name = p.username 
            WHERE m.channel = ? 
            ORDER BY m.timestamp DESC LIMIT 100
        `, [channelName], (err, rows) => {
            if (!err) {
                socket.emit('message_history', rows.reverse());
            }
        });
    });

    socket.on('send_message', (data) => {
        const userData = connectedUsers.get(socket.id) || { name: "Anónimo", avatar: null };
        const channel = data.channel || 'general';
        const messageData = {
            sender_id: socket.id,
            sender_name: userData.name,
            sender_avatar: userData.avatar,
            channel: channel,
            content: data.content,
            type: data.type || 'text',
            timestamp: new Date().toISOString()
        };

        db.run(`INSERT INTO messages (sender_id, sender_name, channel, content) VALUES (?, ?, ?, ?)`, 
            [messageData.sender_id, messageData.sender_name, channel, JSON.stringify({text: messageData.content, type: messageData.type})], 
            function(err) {
                if (!err) {
                    messageData.id = this.lastID;
                    io.to(channel).emit('receive_message', messageData);
                } else {
                    console.error("Error guardando mensaje:", err);
                }
        });
    });

    socket.on('typing', (data) => {
        const userData = connectedUsers.get(socket.id);
        if(userData) socket.to(data.channel).emit('user_typing', { username: userData.name, channel: data.channel });
    });

    // ----- LLAMADAS DE VOZ (WebRTC Signaling) -----
    socket.on('join_voice', (roomName) => {
        const userData = connectedUsers.get(socket.id) || { name: "Anónimo" };
        socket.join(roomName);
        socket.to(roomName).emit('user_joined_voice', { id: socket.id, name: userData.name, avatar: userData.avatar });

        if (!globalVoiceChannels.has(roomName)) globalVoiceChannels.set(roomName, []);
        const usersInRoom = globalVoiceChannels.get(roomName).filter(u => u.id !== socket.id);
        usersInRoom.push({ id: socket.id, name: userData.name, avatar: userData.avatar });
        globalVoiceChannels.set(roomName, usersInRoom);
        broadcastVoiceChannels(io);
    });

    socket.on('leave_voice', (roomName) => {
        socket.leave(roomName);
        socket.to(roomName).emit('user_left_voice', socket.id);

        if (globalVoiceChannels.has(roomName)) {
            const usersInRoom = globalVoiceChannels.get(roomName).filter(u => u.id !== socket.id);
            if (usersInRoom.length === 0) globalVoiceChannels.delete(roomName);
            else globalVoiceChannels.set(roomName, usersInRoom);
            broadcastVoiceChannels(io);
        }
    });

    socket.on('webrtc_offer', (data) => {
        const userData = connectedUsers.get(socket.id);
        socket.to(data.target).emit('webrtc_offer', {
            sdp: data.sdp,
            caller: socket.id,
            callerName: userData ? userData.name : "Unknown",
            callerAvatar: userData ? userData.avatar : null
        });
    });

    socket.on('webrtc_answer', (data) => {
        socket.to(data.target).emit('webrtc_answer', {
            sdp: data.sdp,
            callee: socket.id
        });
    });

    socket.on('webrtc_ice_candidate', (data) => {
        socket.to(data.target).emit('webrtc_ice_candidate', {
            candidate: data.candidate,
            sender: socket.id
        });
    });

    // ----- DESCONEXION -----
    socket.on('disconnect', () => {
        const userData = connectedUsers.get(socket.id);
        if (userData) {
            connectedUsers.delete(socket.id);
            db.run(`DELETE FROM users WHERE id = ?`, [socket.id]);
            io.emit('user_left', { id: socket.id, username: userData.name });
            
            const usersList = Array.from(connectedUsers, ([id, data]) => ({ id, name: data.name, avatar: data.avatar }));
            io.emit('active_users', usersList);
            socket.broadcast.emit('user_left_voice', socket.id);

            let changed = false;
            for (let [room, users] of globalVoiceChannels.entries()) {
                const filtered = users.filter(u => u.id !== socket.id);
                if (filtered.length !== users.length) {
                    changed = true;
                    if (filtered.length === 0) globalVoiceChannels.delete(room);
                    else globalVoiceChannels.set(room, filtered);
                }
            }
            if (changed) broadcastVoiceChannels(io);
        }
    });
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
    console.log(`Servidor de WebSockets corriendo en http://localhost:${PORT}`);
});
