package server;

import common.Protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representa una sala de chat en el servidor.
 * Gestiona los usuarios conectados, el historial de mensajes y las notificaciones de escritura.
 * Los mensajes se persisten en un archivo de texto.
 */
public class ChatRoom {

    private final String name;
    private final String creator;
    private final long createdAt;
    private final Set<ClientHandler> members;
    private final List<String> messageHistory;
    private final Set<String> typingUsers;
    private final String dataDir;
    private static final int MAX_HISTORY = 200;

    public ChatRoom(String name, String creator, String dataDir) {
        this.name = name;
        this.creator = creator;
        this.dataDir = dataDir;
        this.createdAt = System.currentTimeMillis();
        this.members = ConcurrentHashMap.newKeySet();
        this.messageHistory = Collections.synchronizedList(new ArrayList<>());
        this.typingUsers = ConcurrentHashMap.newKeySet();
        loadHistory();
    }

    public String getName() {
        return name;
    }

    public String getCreator() {
        return creator;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Carga el historial de mensajes desde el archivo.
     */
    private void loadHistory() {
        File file = getHistoryFile();
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    messageHistory.add(line);
                }
            }
            // Mantener solo los ultimos MAX_HISTORY mensajes
            while (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(0);
            }
            System.out.println("[SERVIDOR] Historial cargado para sala '" + name + "': " + messageHistory.size() + " mensajes");
        } catch (IOException e) {
            System.out.println("[SERVIDOR] Error cargando historial de " + name + ": " + e.getMessage());
        }
    }

    /**
     * Guarda un mensaje en el archivo de historial.
     */
    private void persistMessage(String message) {
        File file = getHistoryFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8, true))) {
            pw.println(message);
        } catch (IOException e) {
            System.out.println("[SERVIDOR] Error guardando mensaje: " + e.getMessage());
        }
    }

    private File getHistoryFile() {
        return new File(dataDir, "room_" + name.replaceAll("[^a-zA-Z0-9]", "_") + ".txt");
    }

    /**
     * Anade un miembro a la sala.
     */
    public void addMember(ClientHandler client) {
        members.add(client);
        // Notificar a todos los miembros que alguien se ha unido
        String joinMsg = Protocol.buildMessage(Protocol.SYSTEM, "SERVER", name,
                client.getNickname() + " se ha unido a la sala");
        broadcast(joinMsg, null);
        // Enviar historial al nuevo miembro
        sendHistory(client);
        // Enviar lista de usuarios actualizada
        broadcastUserList();
    }

    /**
     * Elimina un miembro de la sala.
     */
    public void removeMember(ClientHandler client) {
        members.remove(client);
        typingUsers.remove(client.getNickname());
        String leaveMsg = Protocol.buildMessage(Protocol.SYSTEM, "SERVER", name,
                client.getNickname() + " ha salido de la sala");
        broadcast(leaveMsg, null);
        broadcastUserList();
    }

    /**
     * Envia un mensaje a todos los miembros de la sala (excepto exclude).
     */
    public void broadcast(String message, ClientHandler exclude) {
        // Guardar en historial (solo mensajes de texto)
        String[] parsed = Protocol.parseMessage(message);
        if (parsed != null && parsed[0].equals(Protocol.MSG)) {
            synchronized (messageHistory) {
                messageHistory.add(message);
                if (messageHistory.size() > MAX_HISTORY) {
                    messageHistory.remove(0);
                }
            }
            persistMessage(message);
        }

        for (ClientHandler member : members) {
            if (member != exclude) {
                member.sendMessage(message);
            }
        }
    }

    /**
     * Envia un mensaje a TODOS los miembros incluyendo el emisor.
     */
    public void broadcastToAll(String message) {
        // Guardar en historial
        String[] parsed = Protocol.parseMessage(message);
        if (parsed != null && parsed[0].equals(Protocol.MSG)) {
            synchronized (messageHistory) {
                messageHistory.add(message);
                if (messageHistory.size() > MAX_HISTORY) {
                    messageHistory.remove(0);
                }
            }
            persistMessage(message);
        }

        for (ClientHandler member : members) {
            member.sendMessage(message);
        }
    }

    /**
     * Envia el historial de mensajes al cliente.
     */
    private void sendHistory(ClientHandler client) {
        synchronized (messageHistory) {
            for (String msg : messageHistory) {
                client.sendMessage(msg);
            }
        }
    }

    /**
     * Notifica que un usuario esta escribiendo.
     */
    public void setTyping(String nickname, boolean typing) {
        if (typing) {
            typingUsers.add(nickname);
        } else {
            typingUsers.remove(nickname);
        }
        String type = typing ? Protocol.TYPING : Protocol.STOP_TYPING;
        String message = Protocol.buildMessage(type, nickname, name, "");
        for (ClientHandler member : members) {
            if (!member.getNickname().equals(nickname)) {
                member.sendMessage(message);
            }
        }
    }

    /**
     * Envia la lista de usuarios actualizada a todos los miembros.
     */
    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler member : members) {
            if (sb.length() > 0) sb.append(",");
            sb.append(member.getNickname());
        }
        String message = Protocol.buildMessage(Protocol.USER_LIST, "SERVER", name, sb.toString());
        for (ClientHandler member : members) {
            member.sendMessage(message);
        }
    }

    /**
     * Devuelve el numero de miembros en la sala.
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Comprueba si un usuario es miembro de la sala.
     */
    public boolean hasMember(ClientHandler client) {
        return members.contains(client);
    }

    /**
     * Devuelve los nicknames de los miembros.
     */
    public List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler member : members) {
            names.add(member.getNickname());
        }
        return names;
    }
}
