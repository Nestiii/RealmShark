package tomato.gui.overlay;

import tomato.gui.overlay.O3PhaseDetector.PhaseAlert;
import util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;

/**
 * Transparent, always-on-top overlay window that flashes the current O3 phase/attack.
 *
 * <p>Singleton. {@link #showPhase(PhaseAlert)} displays the alert label + tip and
 * auto-hides after {@code alert.displayMs()}. All Swing work happens on the EDT.
 */
public class O3PhaseOverlay extends JWindow {

    /** Master on/off for the overlay; toggled from the Overlay menu, persisted by TomatoMenuBar. */
    public static boolean enabled = true;
    /** Debug: when true, every incoming chat name/text is logged so the sender filter can be tuned. */
    public static boolean logSenders = false;

    /** Prefs keys for the persisted drag position (X = box center, Y = top edge). */
    private static final String PREF_X = "o3OverlayX";
    private static final String PREF_Y = "o3OverlayY";

    private static O3PhaseOverlay instance;
    private final JLabel mainLabel;
    private final JLabel tipLabel;
    private Timer hideTimer;

    // Drag state (window-relative drag via absolute screen coords).
    private Point pressScreen;
    private Point winAtPress;
    private boolean repositioning;

    private O3PhaseOverlay() {
        setAlwaysOnTop(true);
        // Per-pixel transparency so only the rounded panel shows. Falls back gracefully
        // if the platform rejects it (see getInstance()).
        setBackground(new Color(0, 0, 0, 0));

        mainLabel = new JLabel("", SwingConstants.CENTER);
        mainLabel.setFont(new Font("Arial", Font.BOLD, 26));
        mainLabel.setForeground(Color.WHITE);

        tipLabel = new JLabel("", SwingConstants.CENTER);
        tipLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        tipLabel.setForeground(new Color(220, 220, 220));

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 190));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        mainLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(mainLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(tipLabel);

        add(panel);
        installDrag(panel);
        installDrag(mainLabel);
        installDrag(tipLabel);
        pack();
        positionOnScreen();
    }

    /** Make a component drag the whole window, saving the position on release. */
    private void installDrag(Component c) {
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressScreen = e.getLocationOnScreen();
                winAtPress = getLocation();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                saveLocation();
            }
        });
        c.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressScreen == null || winAtPress == null) return;
                Point now = e.getLocationOnScreen();
                setLocation(winAtPress.x + (now.x - pressScreen.x),
                            winAtPress.y + (now.y - pressScreen.y));
            }
        });
    }

    /**
     * Restore the saved position (clamped on-screen): X is the box CENTER so it grows
     * symmetrically left/right when the label width changes; Y is the plain top edge.
     * Falls back to top-center when nothing is saved.
     */
    private void positionOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        String sx = PropertiesManager.getProperty(PREF_X);
        String sy = PropertiesManager.getProperty(PREF_Y);
        if (sx != null && sy != null) {
            try {
                int x = Integer.parseInt(sx) - getWidth() / 2;
                int y = Integer.parseInt(sy);
                x = Math.max(0, Math.min(x, screen.width - getWidth()));
                y = Math.max(0, Math.min(y, screen.height - getHeight()));
                setLocation(x, y);
                return;
            } catch (NumberFormatException ignore) {
                // fall through to default
            }
        }
        setLocation((screen.width - getWidth()) / 2, 80);
    }

    private void saveLocation() {
        Point p = getLocation();
        // X = center (so a later wider/narrower alert stays horizontally centered here);
        // Y = top edge (vertical position is fixed, height stays roughly constant).
        PropertiesManager.setProperties(PREF_X, String.valueOf(p.x + getWidth() / 2));
        PropertiesManager.setProperties(PREF_Y, String.valueOf(p.y));
    }

    /**
     * Reposition mode: show a draggable placeholder that stays visible (no auto-hide) so the
     * user can drag the overlay to where they want it. Turning it off saves and hides.
     */
    public void setRepositioning(boolean on) {
        SwingUtilities.invokeLater(() -> {
            repositioning = on;
            if (on) {
                if (hideTimer != null && hideTimer.isRunning()) hideTimer.stop();
                mainLabel.setText("◆ DRAG TO REPOSITION");
                mainLabel.setForeground(Color.decode("#00E5FF"));
                tipLabel.setText("Drag this box, then untick 'Reposition Overlay' to save");
                pack();
                positionOnScreen();
                setVisible(true);
            } else {
                saveLocation();
                setVisible(false);
            }
        });
    }

    public static synchronized O3PhaseOverlay getInstance() {
        if (instance == null) {
            try {
                instance = new O3PhaseOverlay();
            } catch (Exception e) {
                // Some platforms/headless setups reject per-pixel transparency.
                // Surface it rather than failing silently; caller skips the overlay.
                System.err.println("[O3Overlay] Could not create overlay window: " + e.getMessage());
                throw e;
            }
        }
        return instance;
    }

    public void showPhase(PhaseAlert alert) {
        SwingUtilities.invokeLater(() -> {
            if (repositioning) return; // don't disrupt an in-progress drag
            mainLabel.setText(alert.label());
            try {
                mainLabel.setForeground(Color.decode(alert.color()));
            } catch (NumberFormatException ex) {
                mainLabel.setForeground(Color.WHITE);
            }
            tipLabel.setText(alert.tip());
            pack();
            positionOnScreen();
            setVisible(true);

            if (hideTimer != null && hideTimer.isRunning()) hideTimer.stop();
            hideTimer = new Timer(alert.displayMs(), e -> setVisible(false));
            hideTimer.setRepeats(false);
            hideTimer.start();
        });
    }

    /** Run a taunt substring through the detector and show the overlay if it matches. */
    public static void debugTest(String tauntSubstring) {
        PhaseAlert alert = O3PhaseDetector.detect(tauntSubstring);
        if (alert != null) getInstance().showPhase(alert);
        else System.out.println("[O3Overlay] No match for: " + tauntSubstring);
    }
}
