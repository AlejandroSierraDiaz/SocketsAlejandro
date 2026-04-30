package client.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Componentes personalizados para la interfaz grafica con estilo moderno oscuro.
 */
public class UIComponents {

    // --- Paleta de colores ---
    public static final Color BG_DARK       = new Color(15, 15, 20);
    public static final Color BG_PANEL      = new Color(24, 24, 34);
    public static final Color BG_SIDEBAR    = new Color(20, 20, 28);
    public static final Color BG_INPUT      = new Color(32, 32, 46);
    public static final Color BG_MESSAGE_SELF  = new Color(88, 60, 210);
    public static final Color BG_MESSAGE_OTHER = new Color(38, 38, 56);
    public static final Color BG_HOVER      = new Color(38, 38, 58);
    public static final Color BG_SELECTED   = new Color(60, 48, 130);
    public static final Color BG_BUTTON     = new Color(100, 70, 220);
    public static final Color BG_BUTTON_HOVER = new Color(120, 90, 240);

    public static final Color TEXT_PRIMARY   = new Color(232, 232, 248);
    public static final Color TEXT_SECONDARY = new Color(155, 155, 185);
    public static final Color TEXT_MUTED     = new Color(95, 95, 125);

    public static final Color ACCENT_PURPLE = new Color(125, 85, 255);
    public static final Color ACCENT_GREEN  = new Color(55, 200, 120);
    public static final Color ACCENT_RED    = new Color(230, 75, 75);

    public static final Color BORDER_COLOR  = new Color(40, 40, 58);

    // --- Fuentes ---
    public static final Font FONT_TITLE    = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_NORMAL   = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_SMALL    = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BOLD     = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_INPUT    = new Font("Segoe UI", Font.PLAIN, 14);

    // -----------------------------------------------------------------------
    // Componentes personalizados
    // -----------------------------------------------------------------------

    /** Campo de texto redondeado */
    public static class RoundedTextField extends JTextField {
        private final int radius;
        private final Color bgColor;

        public RoundedTextField(int columns, int radius, Color bgColor) {
            super(columns);
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
            setForeground(TEXT_PRIMARY);
            setCaretColor(ACCENT_PURPLE);
            setFont(FONT_INPUT);
            setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Boton redondeado con hover */
    public static class RoundedButton extends JButton {
        private final int radius;
        private Color bgColor;
        private Color hoverColor;
        private boolean hovering = false;

        public RoundedButton(String text, Color bgColor, Color hoverColor, int radius) {
            super(text);
            this.radius = radius;
            this.bgColor = bgColor;
            this.hoverColor = hoverColor;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(TEXT_PRIMARY);
            setFont(FONT_BOLD);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hovering = true;  repaint(); }
                @Override public void mouseExited (java.awt.event.MouseEvent e) { hovering = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovering ? hoverColor : bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Panel con fondo redondeado */
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private Color bgColor;

        public RoundedPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        public void setBgColor(Color c) { this.bgColor = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // -----------------------------------------------------------------------
    // Utilidades visuales
    // -----------------------------------------------------------------------

    /** Crea un icono de avatar circular con iniciales */
    public static ImageIcon createAvatarIcon(String name, int size, Color color) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(0, 0, size, size);
        String initials = getInitials(name);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(size / 3, 10)));
        FontMetrics fm = g2.getFontMetrics();
        int x = (size - fm.stringWidth(initials)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(initials, x, y);
        g2.dispose();
        return new ImageIcon(img);
    }

    private static String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    /** Color determinista basado en el nombre */
    public static Color getColorForName(String name) {
        if (name == null || name.isEmpty()) return ACCENT_PURPLE;
        Color[] colors = {
            new Color(125, 85, 255),
            new Color(60, 140, 255),
            new Color(55, 200, 120),
            new Color(255, 140, 50),
            new Color(230, 75, 130),
            new Color(50, 190, 200),
            new Color(190, 120, 255),
            new Color(255, 190, 50),
        };
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    /** ScrollPane con estilo minimalista */
    public static JScrollPane createStyledScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(5, 0));

        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(70, 70, 100);
                trackColor = new Color(0, 0, 0, 0);
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroBtn(); }
            private JButton zeroBtn() {
                JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b;
            }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(r.x + 1, r.y, r.width - 2, r.height, 8, 8);
                g2.dispose();
            }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
        });
        return sp;
    }
}
