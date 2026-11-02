import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** The window content: runs the loop, draws the 3D image and the HUD, captures the mouse. */
public class GamePanel extends JPanel {
    private Game game;
    private final Input input;
    private final Renderer renderer = new Renderer();
    private final Hud hud = new Hud();
    private Robot robot;
    private final Cursor blank;
    private long last = System.nanoTime();
    private boolean captured;

    public GamePanel(Game game, Input input) {
        this.game = game;
        this.input = input;
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(input);
        addMouseWheelListener(input);
        try { robot = new Robot(); } catch (AWTException | SecurityException e) { robot = null; }
        blank = Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); captured = true; input.setClick(); }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { captured = false; setCursor(Cursor.getDefaultCursor()); }
        });
        new javax.swing.Timer(16, e -> tick()).start();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9);
        last = now;

        if (game.state() == Game.State.DEAD || game.state() == Game.State.WON) {
            if (input.pressed(KeyEvent.VK_ENTER)) {
                game.shutdown();
                game = new Game(input);
                input.clearEdges();
            }
        }
        boolean look = captured && isShowing() && game.state() == Game.State.PLAYING;
        setCursor(look ? blank : Cursor.getDefaultCursor());
        if (look && robot != null) {
            Point c = getLocationOnScreen();
            int cx = c.x + getWidth() / 2, cy = c.y + getHeight() / 2;
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                Point m = pi.getLocation();
                input.addMouse(m.x - cx, m.y - cy);
                robot.mouseMove(cx, cy);
            }
        }
        if (captured || game.state() != Game.State.PLAYING) game.update(dt);
        else { input.takeDX(); input.takeDY(); game.update(dt); }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        renderer.render(game, game.time());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(renderer.getImage(), 0, 0, getWidth(), getHeight(), null);
        hud.draw(g, game, getWidth(), getHeight());
        if (!captured && game.state() == Game.State.PLAYING) {
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            String s = "Click the window to start (mouse look)";
            int sw = g.getFontMetrics().stringWidth(s);
            g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(getWidth() / 2 - sw / 2 - 20, getHeight() / 2 + 70, sw + 40, 44, 12, 12);
            g.setColor(Color.WHITE); g.drawString(s, getWidth() / 2 - sw / 2, getHeight() / 2 + 100);
        }
    }
}
