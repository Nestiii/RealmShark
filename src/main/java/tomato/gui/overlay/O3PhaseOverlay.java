package tomato.gui.overlay;

import tomato.gui.overlay.O3PhaseDetector.PhaseAlert;

import javax.swing.*;
import java.awt.*;
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

    private static O3PhaseOverlay instance;
    private final JLabel mainLabel;
    private final JLabel tipLabel;
    private Timer hideTimer;

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
        pack();
        positionOnScreen();
    }

    private void positionOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, 80);
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
