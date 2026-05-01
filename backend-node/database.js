const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const dbPath = path.resolve(__dirname, 'chat.db');
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error al conectar a la base de datos:', err.message);
    } else {
        console.log('Conectado a la base de datos SQLite.');
        initializeDB();
    }
});

function initializeDB() {
    db.serialize(() => {
        // Tabla de usuarios
        db.run(`CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL,
            connected_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )`);

        // Tabla de mensajes (ahora con canal)
        db.run(`CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender_id TEXT,
            sender_name TEXT,
            channel TEXT DEFAULT 'general',
            content TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )`);
        
        // Si la tabla existia sin la columna channel, intentamos añadirla (ignorar si falla)
        db.run(`ALTER TABLE messages ADD COLUMN channel TEXT DEFAULT 'general'`, (err) => {
            // Ignorar error de que la columna ya existe
        });
    });
}

module.exports = db;
