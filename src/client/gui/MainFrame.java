package client.gui;

import client.NetworkClient;
import common.Protocol;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import static client.gui.UIComponents.*;

/**
 * Ventana principal de la aplicacion.
 * Gestiona la navegacion entre LoginPanel y ChatPanel,
 * y procesa los mensajes recibidos del servidor.
 */
public class MainFrame extends JFrame {
    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private LoginPanel loginPanel;
    private ChatPanel chatPanel;
    private NetworkClient client;
    private String nickname;

    public MainFrame() {
        setTitle("SocketChat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        // Icono de la ventana
        try {
            java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = icon.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ACCENT_PURPLE);
            g2.fillRoundRect(0, 0, 32, 32, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            g2.drawString("S", 8, 24);
            g2.dispose();
            setIconImage(icon);
        } catch (Exception ignored) {
        }

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(BG_DARK);

        loginPanel = new LoginPanel(this);
        mainPanel.add(loginPanel, "login");

        add(mainPanel);
        cardLayout.show(mainPanel, "login");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (client != null) client.disconnect();
            }
        });
    }

    /**
     * Conecta al servidor y cambia al panel de chat si tiene exito.
     */
    public boolean connectToServer(String host, int port, String nick, String avatarBase64) {
        this.nickname = nick;
        client = new NetworkClient();
        client.setMessageHandler(this::handleMessage);
        client.setErrorHandler(this::handleError);

        if (!client.connect(host, port)) return false;

        // Enviar nickname
        client.sendNickChange(nick);
        // Enviar avatar si hay
        if (avatarBase64 != null && !avatarBase64.isEmpty()) {
            client.sendAvatar(avatarBase64);
        }

        SwingUtilities.invokeLater(() -> {
            chatPanel = new ChatPanel(this, client, nickname);
            mainPanel.add(chatPanel, "chat");
            cardLayout.show(mainPanel, "chat");
            setTitle("SocketChat - " + nickname);
            // Pedir la lista de salas ahora que el panel ya existe
            client.sendListRooms();
        });

        return true;
    }

    /**
     * Procesa mensajes del servidor.
     */
    private void handleMessage(String rawMessage) {
        String[] parts = Protocol.parseMessage(rawMessage);
        if (parts == null) return;
        String type = parts[0];
        String sender = parts[1];
        String room = parts[2];
        String content = parts[3];
        String timestamp = parts[4];

        switch (type) {
            case Protocol.MSG:
                if (chatPanel != null) chatPanel.addMessage(sender, room, content, timestamp);
                break;
            case Protocol.SYSTEM:
                if (chatPanel != null) chatPanel.addSystemMessage(room, content);
                break;
            case Protocol.ROOM_LIST:
                if (chatPanel != null) chatPanel.updateRoomList(content);
                break;
            case Protocol.USER_LIST:
                if (chatPanel != null) chatPanel.updateUserList(room, content);
                break;
            case Protocol.TYPING:
                if (chatPanel != null) chatPanel.handleTyping(sender, true);
                break;
            case Protocol.STOP_TYPING:
                if (chatPanel != null) chatPanel.handleTyping(sender, false);
                break;
            case Protocol.ERROR:
                if (chatPanel != null) chatPanel.addSystemMessage("", "Error: " + content);
                break;
            case Protocol.ROOM_DELETED:
                if (chatPanel != null) {
                    chatPanel.handleRoomDeleted(room);
                    chatPanel.addSystemMessage("", content);
                }
                break;
            case Protocol.PRIVATE_MSG:
                if (chatPanel != null) chatPanel.addSystemMessage("", "[Privado de " + sender + "]: " + content);
                break;
            default:
                break;
        }
    }

    private void handleError(String error) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, error, "Error de conexion", JOptionPane.ERROR_MESSAGE);
            if (client != null && !client.isConnected()) {
                cardLayout.show(mainPanel, "login");
                setTitle("SocketChat");
            }
        });
    }

    public static void main(String[] args) {
        // Mejorar aspecto visual
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
