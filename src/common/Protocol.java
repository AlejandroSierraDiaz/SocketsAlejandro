package common;

/**
 * Clase que define el protocolo de comunicación entre cliente y servidor.
 * Todos los mensajes se envían como texto plano con campos separados por un delimitador.
 * 
 * Formato general: TIPO|EMISOR|SALA|CONTENIDO|TIMESTAMP
 */
public class Protocol {

    // Delimitador de campos en los mensajes
    public static final String DELIMITER = "|||";
    // Regex para split (escapar caracteres especiales de regex)
    public static final String DELIMITER_REGEX = "\\|\\|\\|";

    // --- Tipos de mensaje ---

    /** Mensaje de texto normal */
    public static final String MSG = "MSG";

    /** El usuario se ha conectado al servidor */
    public static final String JOIN = "JOIN";

    /** El usuario se ha desconectado del servidor */
    public static final String LEAVE = "LEAVE";

    /** El usuario está escribiendo */
    public static final String TYPING = "TYPING";

    /** El usuario dejó de escribir */
    public static final String STOP_TYPING = "STOP_TYPING";

    /** Crear una nueva sala de chat */
    public static final String CREATE_ROOM = "CREATE_ROOM";

    /** Solicitar lista de salas disponibles */
    public static final String LIST_ROOMS = "LIST_ROOMS";

    /** Unirse a una sala */
    public static final String JOIN_ROOM = "JOIN_ROOM";

    /** Salir de una sala */
    public static final String LEAVE_ROOM = "LEAVE_ROOM";

    /** Establecer nickname */
    public static final String NICK = "NICK";

    /** Establecer avatar (imagen en base64) */
    public static final String AVATAR = "AVATAR";

    /** Lista de usuarios en una sala */
    public static final String USERS = "USERS";

    /** Mensaje del sistema */
    public static final String SYSTEM = "SYSTEM";

    /** Respuesta de lista de salas */
    public static final String ROOM_LIST = "ROOM_LIST";

    /** Respuesta de lista de usuarios */
    public static final String USER_LIST = "USER_LIST";

    /** Error */
    public static final String ERROR = "ERROR";

    /** Mensaje privado */
    public static final String PRIVATE_MSG = "PRIVATE_MSG";

    /** Historial de mensajes */
    public static final String HISTORY = "HISTORY";

    /** Eliminar sala (solo el creador o salas de usuario) */
    public static final String DELETE_ROOM = "DELETE_ROOM";

    /** Renombrar sala */
    public static final String RENAME_ROOM = "RENAME_ROOM";

    /** El servidor notifica que una sala fue eliminada */
    public static final String ROOM_DELETED = "ROOM_DELETED";

    /**
     * Construye un mensaje del protocolo.
     */
    public static String buildMessage(String type, String sender, String room, String content) {
        long timestamp = System.currentTimeMillis();
        return type + DELIMITER + sender + DELIMITER + room + DELIMITER + content + DELIMITER + timestamp;
    }

    /**
     * Parsea un mensaje del protocolo.
     * @return array con [tipo, emisor, sala, contenido, timestamp]
     */
    public static String[] parseMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        String[] parts = rawMessage.split(DELIMITER_REGEX, 5);
        if (parts.length < 5) {
            // Rellenar campos faltantes
            String[] padded = new String[5];
            System.arraycopy(parts, 0, padded, 0, parts.length);
            for (int i = parts.length; i < 5; i++) {
                padded[i] = "";
            }
            return padded;
        }
        return parts;
    }
}
