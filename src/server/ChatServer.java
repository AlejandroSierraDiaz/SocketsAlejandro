package server;

import common.Protocol;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor principal de chat con persistencia de datos.
 * Guarda los mensajes en archivos para que se mantengan entre reinicios.
 */
public class ChatServer {

    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, ChatRoom> rooms;
    private final List<ClientHandler> clients;
    private final ExecutorService threadPool;
    private volatile boolean running;
    private static final String DATA_DIR = "data";

    /** Salas por defecto que no se pueden borrar ni renombrar */
    public static final java.util.Set<String> DEFAULT_ROOMS =
            java.util.Set.of("General", "Gaming", "Musica", "Random");

    public ChatServer(int port) {
        this.port = port;
        this.rooms = new ConcurrentHashMap<>();
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.threadPool = Executors.newCachedThreadPool();
        this.running = false;
    }

    /**
     * Inicia el servidor.
     */
    public void start() {
        try {
            // Crear directorio de datos si no existe
            new File(DATA_DIR).mkdirs();

            serverSocket = new ServerSocket(port);
            running = true;

            System.out.println("==============================================");
            System.out.println("       SERVIDOR DE CHAT INICIADO");
            System.out.println("  Puerto: " + port);
            System.out.println("  Esperando conexiones...");
            System.out.println("==============================================");

            // Cargar salas persistentes o crear las por defecto
            loadOrCreateRooms();

            // Bucle principal de aceptacion de conexiones
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clients.add(handler);
                    threadPool.execute(handler);

                    System.out.println("[SERVIDOR] Nueva conexion desde: " + clientSocket.getInetAddress()
                            + ":" + clientSocket.getPort());
                    System.out.println("[SERVIDOR] Clientes conectados: " + clients.size());

                } catch (IOException e) {
                    if (running) {
                        System.out.println("[SERVIDOR] Error aceptando conexion: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("[SERVIDOR] Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    /**
     * Carga salas desde el disco o crea las por defecto.
     */
    private void loadOrCreateRooms() {
        File roomsFile = new File(DATA_DIR, "rooms.txt");
        if (roomsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(roomsFile, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String name = line.trim();
                    if (!name.isEmpty()) {
                        ChatRoom room = new ChatRoom(name, "SERVER", DATA_DIR);
                        rooms.put(name, room);
                        System.out.println("[SERVIDOR] Sala cargada: " + name);
                    }
                }
            } catch (IOException e) {
                System.out.println("[SERVIDOR] Error cargando salas: " + e.getMessage());
            }
        }

        // Crear salas por defecto si no existen
        String[] defaultRooms = {"General", "Gaming", "Musica", "Random"};
        for (String name : defaultRooms) {
            if (!rooms.containsKey(name)) {
                ChatRoom room = new ChatRoom(name, "SERVER", DATA_DIR);
                rooms.put(name, room);
                System.out.println("[SERVIDOR] Sala creada: " + name);
            }
        }
        saveRoomList();
    }

    /**
     * Guarda la lista de salas en disco.
     */
    private void saveRoomList() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new File(DATA_DIR, "rooms.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String name : rooms.keySet()) {
                pw.println(name);
            }
        } catch (IOException e) {
            System.out.println("[SERVIDOR] Error guardando salas: " + e.getMessage());
        }
    }

    /**
     * Detiene el servidor.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdown();
        System.out.println("[SERVIDOR] Servidor detenido.");
    }

    /**
     * Crea una nueva sala de chat.
     */
    public void createRoom(String name, String creator) {
        if (!rooms.containsKey(name)) {
            ChatRoom room = new ChatRoom(name, creator, DATA_DIR);
            rooms.put(name, room);
            saveRoomList();
            System.out.println("[SERVIDOR] Sala creada: " + name + " (por " + creator + ")");
        }
    }

    /**
     * Comprueba si una sala existe.
     */
    public boolean roomExists(String name) {
        return rooms.containsKey(name);
    }

    /**
     * Obtiene una sala por nombre.
     */
    public ChatRoom getRoom(String name) {
        return rooms.get(name);
    }

    /**
     * Devuelve todas las salas.
     */
    public Map<String, ChatRoom> getRooms() {
        return Collections.unmodifiableMap(rooms);
    }

    /**
     * Elimina un cliente de la lista.
     */
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("[SERVIDOR] Clientes conectados: " + clients.size());
    }

    /**
     * Comprueba si un nickname esta en uso.
     */
    public boolean isNicknameTaken(String nickname) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getNickname().equalsIgnoreCase(nickname)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Actualiza el registro de nickname.
     */
    public void updateNickname(String oldNick, String newNick) {
        // El nickname se actualiza en ClientHandler directamente
    }

    /**
     * Busca un cliente por nickname.
     */
    public ClientHandler getClientByNick(String nickname) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getNickname().equalsIgnoreCase(nickname)) {
                    return client;
                }
            }
        }
        return null;
    }

    /**
     * Elimina una sala de usuario (las salas por defecto no se pueden borrar).
     * Retorna true si se eliminó, false si no existe o es sala por defecto.
     */
    public boolean deleteRoom(String name) {
        if (DEFAULT_ROOMS.contains(name)) return false;
        ChatRoom room = rooms.remove(name);
        if (room == null) return false;
        saveRoomList();
        // Borrar archivo de historial
        new java.io.File(DATA_DIR, "room_" + name.replaceAll("[^a-zA-Z0-9]", "_") + ".txt").delete();
        System.out.println("[SERVIDOR] Sala eliminada: " + name);
        return true;
    }

    /**
     * Renombra una sala de usuario.
     * Retorna true si se renombró, false si no se puede.
     */
    public boolean renameRoom(String oldName, String newName) {
        if (DEFAULT_ROOMS.contains(oldName)) return false;
        if (rooms.containsKey(newName)) return false;
        ChatRoom room = rooms.remove(oldName);
        if (room == null) return false;
        ChatRoom renamed = new ChatRoom(newName, room.getCreator(), DATA_DIR);
        rooms.put(newName, renamed);
        saveRoomList();
        System.out.println("[SERVIDOR] Sala renombrada: " + oldName + " -> " + newName);
        return true;
    }

    /**
     * Envia la lista de salas actualizada a todos los clientes.
     */
    public void broadcastRoomList() {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendRoomList();
            }
        }
    }

    /**
     * Punto de entrada del servidor.
     */
    public static void main(String[] args) {
        int port = 5000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto no valido, usando puerto por defecto: " + port);
            }
        }

        ChatServer server = new ChatServer(port);
        server.start();
    }
}
