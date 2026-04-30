package client.gui;

import common.Protocol;
import client.NetworkClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import static client.gui.UIComponents.*;

/**
 * Panel principal de chat.
 * Sidebar izquierdo con salas, area central de mensajes, sidebar derecho con usuarios.
 */
public class ChatPanel extends JPanel {

    private final MainFrame mainFrame;
    private final NetworkClient client;
    private final String nickname;

    private JPanel roomListPanel;
    private JPanel messagesPanel;
    private JScrollPane messagesScroll;
    private RoundedTextField inputField;
    private JLabel typingLabel;
    private JLabel roomTitleLabel;
    private JLabel roomSubtitleLabel;
    private JPanel usersPanel;

    private String currentRoom = null;
    private boolean firstRoomListReceived = false;
    private final Set<String> typingUsers = new LinkedHashSet<>();
    private java.util.Timer typingTimer;
    private boolean iAmTyping = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    public ChatPanel(MainFrame mainFrame, NetworkClient client, String nickname) {
        this.mainFrame = mainFrame;
        this.client = client;
        this.nickname = nickname;

        setLayout(new BorderLayout(0, 0));
        setBackground(BG_DARK);

        add(buildLeftSidebar(), BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildRightSidebar(), BorderLayout.EAST);
    }

    // =========================================================
    //  Sidebar izquierdo
    // =========================================================
    private JPanel buildLeftSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // Cabecera
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(16, 16, 12, 12));

        JLabel title = new JLabel("Salas");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        header.add(title, BorderLayout.CENTER);

        RoundedButton addBtn = new RoundedButton("+", BG_BUTTON, BG_BUTTON_HOVER, 10);
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        addBtn.setPreferredSize(new Dimension(36, 36));
        addBtn.setBorder(new EmptyBorder(0, 0, 0, 0));
        addBtn.setToolTipText("Crear nueva sala");
        addBtn.addActionListener(e -> createRoom());
        header.add(addBtn, BorderLayout.EAST);

        // Lista de salas
        roomListPanel = new JPanel();
        roomListPanel.setLayout(new BoxLayout(roomListPanel, BoxLayout.Y_AXIS));
        roomListPanel.setBackground(BG_SIDEBAR);
        roomListPanel.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane roomScroll = createStyledScrollPane(roomListPanel);
        roomScroll.getViewport().setBackground(BG_SIDEBAR);

        // Barra inferior con info del usuario
        JPanel userBar = new JPanel(new BorderLayout());
        userBar.setBackground(new Color(17, 17, 24));
        userBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel avatarLbl = new JLabel(createAvatarIcon(nickname, 34, getColorForName(nickname)));
        avatarLbl.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel userInfo = new JPanel();
        userInfo.setLayout(new BoxLayout(userInfo, BoxLayout.Y_AXIS));
        userInfo.setOpaque(false);
        JLabel nickLbl = new JLabel(nickname);
        nickLbl.setFont(FONT_BOLD);
        nickLbl.setForeground(TEXT_PRIMARY);
        JLabel statusLbl = new JLabel("● Online");
        statusLbl.setFont(FONT_SMALL);
        statusLbl.setForeground(ACCENT_GREEN);
        userInfo.add(nickLbl);
        userInfo.add(statusLbl);

        userBar.add(avatarLbl, BorderLayout.WEST);
        userBar.add(userInfo, BorderLayout.CENTER);

        sidebar.add(header, BorderLayout.NORTH);
        sidebar.add(roomScroll, BorderLayout.CENTER);
        sidebar.add(userBar, BorderLayout.SOUTH);
        return sidebar;
    }

    // =========================================================
    //  Panel central (mensajes + input)
    // =========================================================
    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(BG_DARK);

        // Cabecera del chat
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_PANEL);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 20, 12, 20)));

        JPanel headerText = new JPanel();
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.setOpaque(false);

        roomTitleLabel = new JLabel("Selecciona una sala");
        roomTitleLabel.setFont(FONT_SUBTITLE);
        roomTitleLabel.setForeground(TEXT_PRIMARY);

        roomSubtitleLabel = new JLabel("Unete a una sala para empezar a chatear");
        roomSubtitleLabel.setFont(FONT_SMALL);
        roomSubtitleLabel.setForeground(TEXT_MUTED);

        headerText.add(roomTitleLabel);
        headerText.add(roomSubtitleLabel);
        chatHeader.add(headerText, BorderLayout.CENTER);

        // Area de mensajes
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(BG_DARK);
        messagesPanel.setBorder(new EmptyBorder(12, 16, 4, 16));

        messagesScroll = createStyledScrollPane(messagesPanel);
        messagesScroll.getViewport().setBackground(BG_DARK);

        // Zona de input
        JPanel inputZone = new JPanel(new BorderLayout(0, 0));
        inputZone.setBackground(BG_PANEL);
        inputZone.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 14, 12, 14)));

        typingLabel = new JLabel(" ");
        typingLabel.setFont(FONT_SMALL);
        typingLabel.setForeground(TEXT_MUTED);
        typingLabel.setBorder(new EmptyBorder(0, 4, 4, 0));

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);

        inputField = new RoundedTextField(30, 18, BG_INPUT);
        inputField.setPreferredSize(new Dimension(0, 44));
        inputField.setEnabled(false);
        inputField.addActionListener(e -> sendMessage());
        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { onKeyTyped(); }
        });

        RoundedButton sendBtn = new RoundedButton("Enviar", BG_BUTTON, BG_BUTTON_HOVER, 14);
        sendBtn.setPreferredSize(new Dimension(88, 44));
        sendBtn.setBorder(new EmptyBorder(0, 0, 0, 0));
        sendBtn.addActionListener(e -> sendMessage());

        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        inputZone.add(typingLabel, BorderLayout.NORTH);
        inputZone.add(inputRow, BorderLayout.CENTER);

        center.add(chatHeader, BorderLayout.NORTH);
        center.add(messagesScroll, BorderLayout.CENTER);
        center.add(inputZone, BorderLayout.SOUTH);
        return center;
    }

    // =========================================================
    //  Sidebar derecho (usuarios)
    // =========================================================
    private JPanel buildRightSidebar() {
        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(185, 0));
        right.setBackground(BG_SIDEBAR);
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(16, 14, 10, 14));
        JLabel lbl = new JLabel("Usuarios");
        lbl.setFont(FONT_SUBTITLE);
        lbl.setForeground(TEXT_PRIMARY);
        header.add(lbl, BorderLayout.CENTER);

        usersPanel = new JPanel();
        usersPanel.setLayout(new BoxLayout(usersPanel, BoxLayout.Y_AXIS));
        usersPanel.setBackground(BG_SIDEBAR);
        usersPanel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JScrollPane usersScroll = createStyledScrollPane(usersPanel);
        usersScroll.getViewport().setBackground(BG_SIDEBAR);

        right.add(header, BorderLayout.NORTH);
        right.add(usersScroll, BorderLayout.CENTER);
        return right;
    }

    // =========================================================
    //  Acciones
    // =========================================================
    private void createRoom() {
        String name = JOptionPane.showInputDialog(this,
                "Nombre de la nueva sala:", "Crear sala", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty())
            client.sendCreateRoom(name.trim());
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || currentRoom == null) return;
        client.sendChatMessage(currentRoom, text, nickname);
        inputField.setText("");
        if (iAmTyping) {
            iAmTyping = false;
            client.sendStopTyping(currentRoom);
        }
    }

    private void onKeyTyped() {
        if (currentRoom != null && !iAmTyping) {
            iAmTyping = true;
            client.sendTyping(currentRoom);
        }
        if (typingTimer != null) typingTimer.cancel();
        typingTimer = new java.util.Timer();
        typingTimer.schedule(new java.util.TimerTask() {
            @Override public void run() {
                if (iAmTyping && currentRoom != null) {
                    iAmTyping = false;
                    client.sendStopTyping(currentRoom);
                }
            }
        }, 2000);
    }

    private void joinRoom(String roomName) {
        if (roomName.equals(currentRoom)) return;
        currentRoom = roomName;
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();
        typingUsers.clear();
        updateTypingLabel();
        roomTitleLabel.setText("# " + roomName);
        roomSubtitleLabel.setText("Sala " + roomName);
        inputField.setEnabled(true);
        inputField.requestFocusInWindow();
        client.sendJoinRoom(roomName);
        client.sendListRooms(); // actualizar highlight
    }

    // =========================================================
    //  Actualizaciones desde el servidor
    // =========================================================
    public void updateRoomList(String data) {
        SwingUtilities.invokeLater(() -> {
            roomListPanel.removeAll();
            if (data == null || data.isEmpty()) {
                roomListPanel.revalidate(); roomListPanel.repaint(); return;
            }
            String[] rooms = data.split(";;");
            String firstRoom = null;
            for (String r : rooms) {
                String[] parts = r.split(":");
                String roomName = parts[0].trim();
                String count = parts.length > 1 ? parts[1] : "0";
                if (firstRoom == null) firstRoom = roomName;
                roomListPanel.add(buildRoomItem(roomName, Integer.parseInt(count)));
                roomListPanel.add(Box.createVerticalStrut(2));
            }
            roomListPanel.add(Box.createVerticalGlue());
            roomListPanel.revalidate();
            roomListPanel.repaint();

            if (!firstRoomListReceived && firstRoom != null) {
                firstRoomListReceived = true;
                final String toJoin = firstRoom;
                SwingUtilities.invokeLater(() -> joinRoom(toJoin));
            }
        });
    }

    private JPanel buildRoomItem(String name, int count) {
        boolean selected = name.equals(currentRoom);

        JPanel item = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (name.equals(currentRoom)) g2.setColor(BG_SELECTED);
                else g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        item.setOpaque(false);
        item.setBackground(BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        item.setPreferredSize(new Dimension(210, 52));
        item.setBorder(new EmptyBorder(8, 10, 8, 10));
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Avatar
        JLabel icon = new JLabel(createAvatarIcon(name, 34, getColorForName(name)));
        icon.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Texto
        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        JLabel nameLbl = new JLabel(name);
        nameLbl.setFont(FONT_BOLD);
        nameLbl.setForeground(selected ? Color.WHITE : TEXT_PRIMARY);

        JLabel cntLbl = new JLabel(count + " online");
        cntLbl.setFont(FONT_SMALL);
        cntLbl.setForeground(TEXT_MUTED);

        textCol.add(nameLbl);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(cntLbl);

        item.add(icon, BorderLayout.WEST);
        item.add(textCol, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showRoomContextMenu(item, name, e.getX(), e.getY());
                } else {
                    joinRoom(name);
                }
            }
            @Override public void mouseEntered(MouseEvent e) { if (!name.equals(currentRoom)) { item.setBackground(BG_HOVER); item.repaint(); } }
            @Override public void mouseExited (MouseEvent e) { item.setBackground(BG_SIDEBAR); item.repaint(); }
        });
        return item;
    }

    /** Menu contextual con clic derecho en una sala de usuario */
    private void showRoomContextMenu(JPanel item, String roomName, int x, int y) {
        // Las salas por defecto no tienen menu
        java.util.Set<String> defaults = java.util.Set.of("General", "Gaming", "Musica", "Random");
        if (defaults.contains(roomName)) return;

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JMenuItem renameItem = new JMenuItem("Renombrar sala");
        renameItem.setFont(FONT_NORMAL);
        renameItem.setForeground(TEXT_PRIMARY);
        renameItem.setBackground(BG_PANEL);
        renameItem.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(this,
                    "Nuevo nombre para la sala '" + roomName + "':",
                    "Renombrar sala", JOptionPane.PLAIN_MESSAGE);
            if (newName != null && !newName.trim().isEmpty()) {
                client.sendRenameRoom(roomName, newName.trim());
            }
        });

        JMenuItem deleteItem = new JMenuItem("Eliminar sala");
        deleteItem.setFont(FONT_NORMAL);
        deleteItem.setForeground(ACCENT_RED);
        deleteItem.setBackground(BG_PANEL);
        deleteItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Seguro que quieres eliminar la sala '" + roomName + "'?\nSe perdera el historial.",
                    "Eliminar sala", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                client.sendDeleteRoom(roomName);
            }
        });

        menu.add(renameItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(item, x, y);
    }

    /** Sala eliminada por el servidor: volver al estado sin sala activa */
    public void handleRoomDeleted(String roomName) {
        SwingUtilities.invokeLater(() -> {
            if (roomName.equals(currentRoom)) {
                currentRoom = null;
                messagesPanel.removeAll();
                messagesPanel.revalidate();
                messagesPanel.repaint();
                roomTitleLabel.setText("Sala eliminada");
                roomSubtitleLabel.setText("Selecciona otra sala");
                inputField.setEnabled(false);
                addSystemMessage("", "La sala '" + roomName + "' ha sido eliminada.");
            }
        });
    }

    // =========================================================
    //  Mensajes
    // =========================================================
    public void addMessage(String sender, String room, String content, String timestamp) {
        if (!room.equals(currentRoom)) return;
        SwingUtilities.invokeLater(() -> {
            boolean self = sender.equals(nickname);
            messagesPanel.add(buildBubble(sender, content, timestamp, self));
            messagesPanel.add(Box.createVerticalStrut(6));
            messagesPanel.revalidate();
            scrollToBottom();
        });
    }

    public void addSystemMessage(String room, String content) {
        if (!room.isEmpty() && !room.equals(currentRoom)) return;
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            JLabel lbl = new JLabel(content);
            lbl.setFont(FONT_SMALL);
            lbl.setForeground(TEXT_MUTED);
            row.add(lbl);
            messagesPanel.add(row);
            messagesPanel.revalidate();
            scrollToBottom();
        });
    }

    /**
     * Construye una burbuja de mensaje compacta.
     * Usa BorderLayout en la fila exterior (WEST=otros, EAST=propios) para alinear
     * correctamente sin que BoxLayout estire el contenido.
     */
    private JPanel buildBubble(String sender, String content, String ts, boolean self) {
        final int MAX_W = 360;

        // JTextArea con wrap
        JTextArea textArea = new JTextArea(content);
        textArea.setFont(FONT_NORMAL);
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(null);
        textArea.setBackground(new Color(0, 0, 0, 0));
        // Ancho fijo para que calcule cuantas lineas necesita
        int innerW = MAX_W - 28 - (self ? 0 : 38);
        textArea.setSize(innerW, Short.MAX_VALUE);

        // Timestamp
        String timeStr = "";
        try { timeStr = timeFormat.format(new java.util.Date(Long.parseLong(ts))); }
        catch (Exception ignored) {}
        JLabel timeLbl = new JLabel(timeStr);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(new Color(160, 155, 200));

        // Panel interior de la burbuja
        JPanel bubble = new JPanel(new BorderLayout(0, 3));
        bubble.setOpaque(false);
        if (!self) {
            JLabel senderLbl = new JLabel(sender);
            senderLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            senderLbl.setForeground(getColorForName(sender));
            bubble.add(senderLbl, BorderLayout.NORTH);
        }
        bubble.add(textArea, BorderLayout.CENTER);
        bubble.add(timeLbl, BorderLayout.SOUTH);

        // Card con fondo redondeado
        // getMaximumSize() devuelve preferred → BoxLayout no estira en altura
        RoundedPanel card = new RoundedPanel(16, self ? BG_MESSAGE_SELF : BG_MESSAGE_OTHER) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, MAX_W), d.height);
            }
            @Override public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(9, 14, 9, 14));
        card.add(bubble, BorderLayout.CENTER);

        // ── Fila exterior con BorderLayout ──────────────────────────────────
        // WEST: card alineado a la izquierda (mensajes ajenos)
        // EAST: card alineado a la derecha  (mensajes propios)
        // Esto es mucho mas fiable que BoxLayout + glue para controlar tamanos.
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        if (self) {
            row.add(card, BorderLayout.EAST);
        } else {
            // Avatar + card en un subpanel FlowLayout para que se peguen al borde izquierdo
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            left.setOpaque(false);
            JLabel avatarLbl = new JLabel(createAvatarIcon(sender, 30, getColorForName(sender)));
            avatarLbl.setAlignmentY(Component.TOP_ALIGNMENT);
            left.add(avatarLbl);
            left.add(card);
            row.add(left, BorderLayout.WEST);
        }
        return row;
    }




    // =========================================================
    //  Typing
    // =========================================================
    public void handleTyping(String user, boolean typing) {
        SwingUtilities.invokeLater(() -> {
            if (typing) typingUsers.add(user); else typingUsers.remove(user);
            updateTypingLabel();
        });
    }

    private void updateTypingLabel() {
        if (typingUsers.isEmpty()) { typingLabel.setText(" "); return; }
        Iterator<String> it = typingUsers.iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) { sb.append(it.next()); if (it.hasNext()) sb.append(", "); }
        sb.append(typingUsers.size() == 1 ? " esta escribiendo..." : " estan escribiendo...");
        typingLabel.setText(sb.toString());
    }

    // =========================================================
    //  Lista de usuarios
    // =========================================================
    public void updateUserList(String room, String data) {
        if (!room.equals(currentRoom)) return;
        SwingUtilities.invokeLater(() -> {
            usersPanel.removeAll();
            if (data == null || data.isEmpty()) { usersPanel.revalidate(); usersPanel.repaint(); return; }
            String[] users = data.split(",");
            for (String u : users) {
                u = u.trim();
                if (u.isEmpty()) continue;

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
                row.setBorder(new EmptyBorder(4, 2, 4, 2));

                JLabel av = new JLabel(createAvatarIcon(u, 28, getColorForName(u)));
                JPanel info = new JPanel();
                info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
                info.setOpaque(false);

                JLabel name = new JLabel(u.equals(nickname) ? u + " (tú)" : u);
                name.setFont(FONT_NORMAL);
                name.setForeground(u.equals(nickname) ? ACCENT_PURPLE : TEXT_PRIMARY);

                info.add(name);
                row.add(av, BorderLayout.WEST);
                row.add(info, BorderLayout.CENTER);
                usersPanel.add(row);
            }
            usersPanel.add(Box.createVerticalGlue());
            usersPanel.revalidate();
            usersPanel.repaint();
        });
    }

    // =========================================================
    //  Util
    // =========================================================
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = messagesScroll.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }
}
