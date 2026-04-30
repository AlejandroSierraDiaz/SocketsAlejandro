package server;

import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * Manejador de cada cliente conectado al servidor.
 * Se ejecuta en un hilo independiente para gestionar la comunicación con un cliente específico.
 * Implementa Runnable para permitir la ejecución concurrente.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String nickname;
    private String avatarBase64;
    private String currentRoom;
    private volatile boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.nickname = "Usuario" + socket.getPort();
        this.avatarBase64 = "";
        this.currentRoom = null;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            // Inicializar streams de entrada y salida
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            System.out.println("[SERVIDOR] Cliente conectado: " + nickname + " desde " + socket.getInetAddress());

            // Enviar mensaje de bienvenida
            sendMessage(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", "",
                    "¡Bienvenido al servidor de chat! Usa /nick <nombre> para cambiar tu nombre."));

            // Enviar lista de salas disponibles
            sendRoomList();

            // Bucle principal de lectura de mensajes
            String rawMessage;
            while (running && (rawMessage = in.readLine()) != null) {
                processMessage(rawMessage);
            }

        } catch (IOException e) {
            if (running) {
                System.out.println("[SERVIDOR] Error de conexión con " + nickname + ": " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Procesa un mensaje recibido del cliente.
     */
    private void processMessage(String rawMessage) {
        String[] parts = Protocol.parseMessage(rawMessage);
        if (parts == null) return;

        String type = parts[0];
        String sender = parts[1];
        String room = parts[2];
        String content = parts[3];

        switch (type) {
            case Protocol.NICK:
                handleNickChange(content);
                break;

            case Protocol.AVATAR:
                handleAvatarChange(content);
                break;

            case Protocol.CREATE_ROOM:
                handleCreateRoom(content);
                break;

            case Protocol.LIST_ROOMS:
                sendRoomList();
                break;

            case Protocol.JOIN_ROOM:
                handleJoinRoom(content);
                break;

            case Protocol.LEAVE_ROOM:
                handleLeaveRoom();
                break;

            case Protocol.MSG:
                handleMessage(room, content);
                break;

            case Protocol.TYPING:
                handleTyping(room, true);
                break;

            case Protocol.STOP_TYPING:
                handleTyping(room, false);
                break;

            case Protocol.PRIVATE_MSG:
                handlePrivateMessage(room, content);
                break;

            case Protocol.DELETE_ROOM:
                handleDeleteRoom(content);
                break;

            case Protocol.RENAME_ROOM:
                handleRenameRoom(content);
                break;

            default:
                sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                        "Tipo de mensaje desconocido: " + type));
                break;
        }
    }

    /**
     * Cambio de nickname.
     */
    private void handleNickChange(String newNick) {
        if (newNick == null || newNick.trim().isEmpty()) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "El nickname no puede estar vacío."));
            return;
        }

        newNick = newNick.trim();

        // Comprobar si el nick ya está en uso
        if (server.isNicknameTaken(newNick)) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "El nickname '" + newNick + "' ya está en uso."));
            return;
        }

        String oldNick = this.nickname;
        this.nickname = newNick;
        server.updateNickname(oldNick, newNick);

        sendMessage(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", "",
                "Tu nickname ahora es: " + newNick));

        // Notificar a la sala actual
        if (currentRoom != null) {
            ChatRoom chatRoom = server.getRoom(currentRoom);
            if (chatRoom != null) {
                chatRoom.broadcast(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", currentRoom,
                        oldNick + " ahora se llama " + newNick), this);
                chatRoom.broadcastUserList();
            }
        }

        System.out.println("[SERVIDOR] " + oldNick + " cambió su nick a " + newNick);
    }

    /**
     * Cambio de avatar.
     */
    private void handleAvatarChange(String base64Data) {
        this.avatarBase64 = base64Data;
        sendMessage(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", "",
                "Avatar actualizado correctamente."));

        // Notificar a la sala actual para que actualicen el avatar
        if (currentRoom != null) {
            ChatRoom chatRoom = server.getRoom(currentRoom);
            if (chatRoom != null) {
                chatRoom.broadcast(Protocol.buildMessage(Protocol.AVATAR, nickname, currentRoom, base64Data), this);
            }
        }

        System.out.println("[SERVIDOR] " + nickname + " actualizó su avatar");
    }

    /**
     * Eliminar una sala creada por el usuario.
     */
    private void handleDeleteRoom(String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) return;
        roomName = roomName.trim();

        if (server.DEFAULT_ROOMS.contains(roomName)) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "Las salas por defecto no se pueden eliminar."));
            return;
        }

        ChatRoom chatRoom = server.getRoom(roomName);
        if (chatRoom != null) {
            chatRoom.broadcast(Protocol.buildMessage(Protocol.ROOM_DELETED, "SERVER", roomName,
                    "La sala '" + roomName + "' ha sido eliminada."), null);
        }

        boolean ok = server.deleteRoom(roomName);
        if (ok) {
            if (roomName.equals(currentRoom)) currentRoom = null;
            server.broadcastRoomList();
        } else {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "No se pudo eliminar la sala."));
        }
    }

    /**
     * Renombrar una sala creada por el usuario.
     * El contenido es: nombreAntiguo||nombreNuevo
     */
    private void handleRenameRoom(String content) {
        if (content == null || !content.contains("||")) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "Formato incorrecto."));
            return;
        }
        String[] parts = content.split("\\|\\|", 2);
        String oldName = parts[0].trim();
        String newName = parts[1].trim();

        if (server.DEFAULT_ROOMS.contains(oldName)) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "Las salas por defecto no se pueden renombrar."));
            return;
        }
        if (newName.isEmpty()) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "El nuevo nombre no puede estar vacio."));
            return;
        }

        boolean ok = server.renameRoom(oldName, newName);
        if (ok) {
            if (oldName.equals(currentRoom)) currentRoom = newName;
            server.broadcastRoomList();
            sendMessage(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", "",
                    "Sala renombrada a '" + newName + "'."));
        } else {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "No se pudo renombrar. El nombre puede que ya exista."));
        }
    }

    /**
     * Crear una nueva sala de chat.
     */
    private void handleCreateRoom(String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "El nombre de la sala no puede estar vacío."));
            return;
        }

        roomName = roomName.trim();

        if (server.roomExists(roomName)) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "La sala '" + roomName + "' ya existe."));
            return;
        }

        server.createRoom(roomName, nickname);
        sendMessage(Protocol.buildMessage(Protocol.SYSTEM, "SERVER", "",
                "Sala '" + roomName + "' creada con éxito."));

        // Enviar lista actualizada a todos los clientes
        server.broadcastRoomList();

        System.out.println("[SERVIDOR] " + nickname + " creó la sala: " + roomName);
    }

    /**
     * Unirse a una sala.
     */
    private void handleJoinRoom(String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "Nombre de sala no válido."));
            return;
        }

        roomName = roomName.trim();

        // Salir de la sala actual si hay una
        if (currentRoom != null) {
            ChatRoom oldRoom = server.getRoom(currentRoom);
            if (oldRoom != null) {
                oldRoom.removeMember(this);
            }
        }

        ChatRoom chatRoom = server.getRoom(roomName);
        if (chatRoom == null) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "La sala '" + roomName + "' no existe."));
            return;
        }

        currentRoom = roomName;
        chatRoom.addMember(this);

        System.out.println("[SERVIDOR] " + nickname + " se unió a la sala: " + roomName);
    }

    /**
     * Salir de la sala actual.
     */
    private void handleLeaveRoom() {
        if (currentRoom != null) {
            ChatRoom chatRoom = server.getRoom(currentRoom);
            if (chatRoom != null) {
                chatRoom.removeMember(this);
            }
            currentRoom = null;
            sendRoomList();
        }
    }

    /**
     * Enviar un mensaje a la sala actual.
     */
    private void handleMessage(String room, String content) {
        if (currentRoom == null) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "No estás en ninguna sala. Únete a una sala primero."));
            return;
        }

        ChatRoom chatRoom = server.getRoom(currentRoom);
        if (chatRoom != null) {
            // El mensaje se envía a todos incluido el emisor (confirmación)
            chatRoom.broadcastToAll(Protocol.buildMessage(Protocol.MSG, nickname, currentRoom, content));
        }
    }

    /**
     * Gestionar indicador de escritura.
     */
    private void handleTyping(String room, boolean typing) {
        if (currentRoom == null) return;

        ChatRoom chatRoom = server.getRoom(currentRoom);
        if (chatRoom != null) {
            chatRoom.setTyping(nickname, typing);
        }
    }

    /**
     * Enviar mensaje privado.
     */
    private void handlePrivateMessage(String targetNick, String content) {
        ClientHandler target = server.getClientByNick(targetNick);
        if (target == null) {
            sendMessage(Protocol.buildMessage(Protocol.ERROR, "SERVER", "",
                    "Usuario '" + targetNick + "' no encontrado."));
            return;
        }

        target.sendMessage(Protocol.buildMessage(Protocol.PRIVATE_MSG, nickname, "", content));
        sendMessage(Protocol.buildMessage(Protocol.PRIVATE_MSG, nickname, targetNick, content));
    }

    /**
     * Enviar la lista de salas al cliente.
     */
    public void sendRoomList() {
        Map<String, ChatRoom> rooms = server.getRooms();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
            if (sb.length() > 0) sb.append(";;");
            sb.append(entry.getKey()).append(":").append(entry.getValue().getMemberCount());
        }
        sendMessage(Protocol.buildMessage(Protocol.ROOM_LIST, "SERVER", "", sb.toString()));
    }

    /**
     * Envía un mensaje al cliente.
     */
    public void sendMessage(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    /**
     * Desconecta al cliente.
     */
    public void disconnect() {
        running = false;

        // Salir de la sala actual
        if (currentRoom != null) {
            ChatRoom chatRoom = server.getRoom(currentRoom);
            if (chatRoom != null) {
                chatRoom.removeMember(this);
            }
        }

        // Eliminar del servidor
        server.removeClient(this);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("[SERVIDOR] Error al cerrar socket de " + nickname + ": " + e.getMessage());
        }

        System.out.println("[SERVIDOR] Cliente desconectado: " + nickname);
    }

    // --- Getters ---

    public String getNickname() {
        return nickname;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }
}
