package client;

import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Cliente de red que gestiona la conexión con el servidor.
 * Se encarga de enviar y recibir mensajes a través del socket TCP.
 */
public class NetworkClient {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Consumer<String> messageHandler;
    private Consumer<String> errorHandler;
    private volatile boolean connected;
    private Thread readerThread;

    public NetworkClient() {
        this.connected = false;
    }

    /**
     * Establece el handler para mensajes entrantes.
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Establece el handler para errores.
     */
    public void setErrorHandler(Consumer<String> handler) {
        this.errorHandler = handler;
    }

    /**
     * Conecta al servidor.
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            connected = true;

            // Iniciar hilo de lectura
            readerThread = new Thread(this::readMessages, "ReaderThread");
            readerThread.setDaemon(true);
            readerThread.start();

            return true;
        } catch (IOException e) {
            if (errorHandler != null) {
                errorHandler.accept("Error al conectar: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Hilo de lectura de mensajes del servidor.
     */
    private void readMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                connected = false;
                if (errorHandler != null) {
                    errorHandler.accept("Conexión perdida con el servidor.");
                }
            }
        }
    }

    /**
     * Envía un mensaje al servidor.
     */
    public void send(String message) {
        if (connected && out != null) {
            out.println(message);
        }
    }

    /**
     * Envía un mensaje de texto a la sala actual.
     */
    public void sendChatMessage(String room, String content, String nickname) {
        send(Protocol.buildMessage(Protocol.MSG, nickname, room, content));
    }

    /**
     * Envía notificación de cambio de nick.
     */
    public void sendNickChange(String newNick) {
        send(Protocol.buildMessage(Protocol.NICK, "", "", newNick));
    }

    /**
     * Envía avatar en base64.
     */
    public void sendAvatar(String base64Data) {
        send(Protocol.buildMessage(Protocol.AVATAR, "", "", base64Data));
    }

    /**
     * Solicita crear una sala.
     */
    public void sendCreateRoom(String roomName) {
        send(Protocol.buildMessage(Protocol.CREATE_ROOM, "", "", roomName));
    }

    public void sendDeleteRoom(String roomName) {
        send(Protocol.buildMessage(Protocol.DELETE_ROOM, "", "", roomName));
    }

    public void sendRenameRoom(String oldName, String newName) {
        send(Protocol.buildMessage(Protocol.RENAME_ROOM, "", "", oldName + "||" + newName));
    }

    /**
     * Solicita unirse a una sala.
     */
    public void sendJoinRoom(String roomName) {
        send(Protocol.buildMessage(Protocol.JOIN_ROOM, "", "", roomName));
    }

    /**
     * Solicita salir de la sala actual.
     */
    public void sendLeaveRoom() {
        send(Protocol.buildMessage(Protocol.LEAVE_ROOM, "", "", ""));
    }

    /**
     * Envía indicador de escritura.
     */
    public void sendTyping(String room) {
        send(Protocol.buildMessage(Protocol.TYPING, "", room, ""));
    }

    /**
     * Envía indicador de parada de escritura.
     */
    public void sendStopTyping(String room) {
        send(Protocol.buildMessage(Protocol.STOP_TYPING, "", room, ""));
    }

    /**
     * Solicita la lista de salas.
     */
    public void sendListRooms() {
        send(Protocol.buildMessage(Protocol.LIST_ROOMS, "", "", ""));
    }

    /**
     * Desconecta del servidor.
     */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignorar errores al cerrar
        }
    }

    /**
     * Comprueba si está conectado.
     */
    public boolean isConnected() {
        return connected;
    }
}
