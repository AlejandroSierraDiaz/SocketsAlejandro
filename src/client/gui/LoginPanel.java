package client.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import static client.gui.UIComponents.*;

/**
 * Pantalla de inicio: solo nickname y foto de perfil opcional.
 * Se conecta automaticamente a localhost:5000.
 */
public class LoginPanel extends JPanel {

    private final RoundedTextField nicknameField;
    private final RoundedButton connectButton;
    private final JLabel statusLabel;
    private final JLabel avatarLabel;
    private final MainFrame mainFrame;
    private String avatarBase64 = "";

    public LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        setBackground(BG_DARK);

        // Fondo degradado sutil (pintado manualmente)
        setOpaque(false);

        // Tarjeta central
        RoundedPanel card = new RoundedPanel(22, BG_PANEL) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Borde sutil
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 22, 22);
                g2.dispose();
            }
        };
        card.setLayout(new GridBagLayout());
        card.setPreferredSize(new Dimension(380, 480));
        card.setBorder(new EmptyBorder(42, 46, 42, 46));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 0);

        // Icono de app
        JLabel appIcon = new JLabel(buildAppIcon());
        appIcon.setAlignmentX(CENTER_ALIGNMENT);
        card.add(appIcon, gbc);

        // Titulo
        gbc.gridy++; gbc.insets = new Insets(12, 0, 2, 0);
        JLabel title = new JLabel("SocketChat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(TEXT_PRIMARY);
        card.add(title, gbc);

        // Subtitulo
        gbc.gridy++; gbc.insets = new Insets(0, 0, 28, 0);
        JLabel sub = new JLabel("Mensajeria en tiempo real");
        sub.setFont(FONT_SMALL);
        sub.setForeground(TEXT_MUTED);
        card.add(sub, gbc);

        // Avatar clicable
        avatarLabel = new JLabel(createAvatarIcon("?", 72, new Color(55, 45, 110)));
        avatarLabel.setToolTipText("Haz clic para elegir tu foto");
        avatarLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        avatarLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { pickAvatar(); }
        });
        gbc.gridy++; gbc.insets = new Insets(0, 0, 6, 0);
        card.add(avatarLabel, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 22, 0);
        JLabel avatarHint = new JLabel("Haz clic para elegir foto");
        avatarHint.setFont(FONT_SMALL);
        avatarHint.setForeground(TEXT_MUTED);
        card.add(avatarHint, gbc);

        // Campo nickname
        gbc.gridy++; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(0, 0, 6, 0);
        JLabel nickLabel = new JLabel("Tu nombre");
        nickLabel.setFont(FONT_BOLD);
        nickLabel.setForeground(TEXT_SECONDARY);
        card.add(nickLabel, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 24, 0);
        nicknameField = new RoundedTextField(20, 14, BG_INPUT);
        nicknameField.setPreferredSize(new Dimension(288, 46));
        nicknameField.addActionListener(e -> connect());
        card.add(nicknameField, gbc);

        // Boton
        gbc.gridy++; gbc.insets = new Insets(0, 0, 10, 0);
        connectButton = new RoundedButton("Entrar al chat", BG_BUTTON, BG_BUTTON_HOVER, 14);
        connectButton.setPreferredSize(new Dimension(288, 48));
        connectButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        connectButton.addActionListener(e -> connect());
        card.add(connectButton, gbc);

        // Estado
        gbc.gridy++; gbc.insets = new Insets(0, 0, 0, 0);
        statusLabel = new JLabel(" ");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(ACCENT_RED);
        card.add(statusLabel, gbc);

        add(card);
        SwingUtilities.invokeLater(() -> nicknameField.requestFocusInWindow());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Degradado de fondo
        g2.setPaint(new GradientPaint(0, 0, new Color(14, 12, 22), getWidth(), getHeight(), new Color(20, 16, 38)));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    private ImageIcon buildAppIcon() {
        int s = 52;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, new Color(110, 70, 240), s, s, new Color(60, 130, 255));
        g2.setPaint(gp);
        g2.fillRoundRect(0, 0, s, s, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
        FontMetrics fm = g2.getFontMetrics();
        String ch = "S";
        g2.drawString(ch, (s - fm.stringWidth(ch)) / 2, (s - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
        return new ImageIcon(img);
    }

    private void pickAvatar() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar foto de perfil");
        fc.setFileFilter(new FileNameExtensionFilter("Imagenes", "png","jpg","jpeg","gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage src = ImageIO.read(fc.getSelectedFile());
            if (src == null) return;
            // Crop cuadrado centrado + escalar a 72
            int side = Math.min(src.getWidth(), src.getHeight());
            int ox = (src.getWidth() - side) / 2;
            int oy = (src.getHeight() - side) / 2;
            BufferedImage out = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, 72, 72));
            g2.drawImage(src, 0, 0, 72, 72, ox, oy, ox + side, oy + side, null);
            g2.dispose();
            avatarLabel.setIcon(new ImageIcon(out));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            avatarBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ex) {
            statusLabel.setText("Error al cargar la imagen.");
        }
    }

    private void connect() {
        String nick = nicknameField.getText().trim();
        if (nick.isEmpty()) {
            statusLabel.setText("Escribe tu nombre para continuar.");
            statusLabel.setForeground(ACCENT_RED);
            return;
        }
        statusLabel.setText("Conectando...");
        statusLabel.setForeground(TEXT_MUTED);
        connectButton.setEnabled(false);
        new Thread(() -> {
            boolean ok = mainFrame.connectToServer("localhost", 5000, nick, avatarBase64);
            SwingUtilities.invokeLater(() -> {
                if (!ok) {
                    statusLabel.setText("No se pudo conectar. Asegurate de que el servidor este activo.");
                    statusLabel.setForeground(ACCENT_RED);
                    connectButton.setEnabled(true);
                }
            });
        }).start();
    }

    public String getAvatarBase64() { return avatarBase64; }
}
