const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const db = require('./database');

const app = express();
app.use(cors());

const server = http.createServer(app);
// Increase max payload size for images (e.g. 10MB)
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    },
    maxHttpBufferSize: 1e7 // 10 MB
});

const connectedUsers = new Map(); // socket.id -> username
const globalVoiceChannels = new Map(); // roomName -> Array of {id, name, micMuted}

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
        connectedUsers.set(socket.id, username);
        db.run(`INSERT OR REPLACE INTO users (id, username) VALUES (?, ?)`, [socket.id, username]);
        
        io.emit('user_joined', { id: socket.id, username });
        const usersList = Array.from(connectedUsers, ([id, name]) => ({ id, name }));
        io.emit('active_users', usersList);
        broadcastVoiceChannels(io); // Send voice channels state to new user
    });

    socket.on('update_username', (newUsername) => {
        if(connectedUsers.has(socket.id)) {
            connectedUsers.set(socket.id, newUsername);
            db.run(`UPDATE users SET username = ? WHERE id = ?`, [newUsername, socket.id]);
            const usersList = Array.from(connectedUsers, ([id, name]) => ({ id, name }));
            io.emit('active_users', usersList);
            io.emit('username_changed', { id: socket.id, newName: newUsername });
        }
    });

    // ----- CANALES DE TEXTO Y DMs -----
    socket.on('join_channel', (channelName) => {
        Array.from(socket.rooms).forEach(room => {
            if (room !== socket.id) socket.leave(room);
        });
        
        socket.join(channelName);
        
        db.all(`SELECT * FROM messages WHERE channel = ? ORDER BY timestamp DESC LIMIT 100`, [channelName], (err, rows) => {
            if (!err) {
                socket.emit('message_history', rows.reverse());
            }
        });
    });

    socket.on('send_message', (data) => {
        const username = connectedUsers.get(socket.id) || "Anónimo";
        const channel = data.channel || 'general';
        const messageData = {
            sender_id: socket.id,
            sender_name: username,
            channel: channel,
            content: data.content,
            type: data.type || 'text', // 'text' o 'image'
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
        socket.to(data.channel).emit('user_typing', { username: connectedUsers.get(socket.id), channel: data.channel });
    });

    // ----- LLAMADAS DE VOZ (WebRTC Signaling) -----
    socket.on('join_voice', (roomName) => {
        const username = connectedUsers.get(socket.id);
        socket.join(roomName);
        socket.to(roomName).emit('user_joined_voice', { id: socket.id, name: username });

        if (!globalVoiceChannels.has(roomName)) globalVoiceChannels.set(roomName, []);
        // Prevent duplicates
        const usersInRoom = globalVoiceChannels.get(roomName).filter(u => u.id !== socket.id);
        usersInRoom.push({ id: socket.id, name: username });
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
        socket.to(data.target).emit('webrtc_offer', {
            sdp: data.sdp,
            caller: socket.id,
            callerName: connectedUsers.get(socket.id)
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
        const username = connectedUsers.get(socket.id);
        if (username) {
            connectedUsers.delete(socket.id);
            db.run(`DELETE FROM users WHERE id = ?`, [socket.id]);
            io.emit('user_left', { id: socket.id, username });
            
            const usersList = Array.from(connectedUsers, ([id, name]) => ({ id, name }));
            io.emit('active_users', usersList);
            socket.broadcast.emit('user_left_voice', socket.id);

            // Clean up from global voice channels
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
