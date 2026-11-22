import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;

/**
 * ROOM 404 - complete game in ONE file (menu + game + settings + character).
 *
 *   javac Room404Menu.java
 *   java Room404Menu
 *
 * MENU      : W/S or UP/DOWN + ENTER, or mouse.
 * PLAY      : WASD / arrows to move. Find 3 keys, open the door of ROOM 404, reach EXIT.
 *             A ghost hunts you. R = retry, ESC = back to menu.
 * SETTINGS  : volume, difficulty, scanlines, maximize window (saved automatically).
 * CHARACTER : name + color (saved automatically, used in the game).
 */
public class Room404Menu {

    static final int LW = 1280, LH = 720;           // logical resolution
    static final Color RED = new Color(0xE01B1B);
    static final Color BLOOD = new Color(0xB3121F);
    static final Color BONE = new Color(0xE9D6D9);
    static final Color LIGHT = new Color(0xDDD3D4);

    // ==================================================================
    //  SAVED SETTINGS
    // ==================================================================
    static class Prefs {
        static final Preferences P = Preferences.userRoot().node("room404game");
        static int volume = P.getInt("volume", 70);
        static int difficulty = P.getInt("difficulty", 1);      // 0 easy, 1 normal, 2 hard
        static boolean scanlines = P.getBoolean("scanlines", true);
        static boolean maximized = P.getBoolean("maximized", false);
        static String name = P.get("name", "Player");
        static int color = P.getInt("color", 0);
        static final Color[] COLORS = {
                new Color(0x4FC3F7), new Color(0xF06292), new Color(0xFFB74D), new Color(0x81C784)};

        static void save() {
            try {
                P.putInt("volume", volume);
                P.putInt("difficulty", difficulty);
                P.putBoolean("scanlines", scanlines);
                P.putBoolean("maximized", maximized);
                P.put("name", name);
                P.putInt("color", color);
                P.flush();
            } catch (Exception ignored) { }
        }
    }

    // ==================================================================
    //  SOUND (generated tones, no files needed)
    // ==================================================================
    static class Sfx {
        static void tone(int hz, int ms, double vol) {
            final double v = vol * Prefs.volume / 100.0;
            if (v <= 0.01) return;
            Thread t = new Thread(() -> {
                try {
                    float rate = 22050f;
                    AudioFormat f = new AudioFormat(rate, 8, 1, true, false);
                    byte[] buf = new byte[(int) (rate * ms / 1000.0)];
                    for (int i = 0; i < buf.length; i++) {
                        double env = 1.0 - (double) i / buf.length;
                        buf[i] = (byte) (Math.sin(2 * Math.PI * hz * i / rate) * 120 * v * env);
                    }
                    SourceDataLine line = AudioSystem.getSourceDataLine(f);
                    line.open(f);
                    line.start();
                    line.write(buf, 0, buf.length);
                    line.drain();
                    line.close();
                } catch (Throwable ignored) { }
            });
            t.setDaemon(true);
            t.start();
        }
    }

    // ==================================================================
    //  YOUR REAL GAME  (Input + Game + GamePanel + CharacterProfile)
    //  Loaded by name, so this file still compiles/runs without them.
    //  It does exactly what your Main.java does, but starts from this menu.
    // ==================================================================
    static class RealGame {
        static volatile boolean warmDone = true;

        /** loads your textures in the background while the LOADING bar runs */
        static void warmUp() {
            warmDone = false;
            Thread t = new Thread(() -> {
                try {
                    java.lang.reflect.Method m = Class.forName("Textures").getDeclaredMethod("init");
                    m.setAccessible(true);
                    m.invoke(null);
                } catch (Throwable ignored) { }
                warmDone = true;
            });
            t.setDaemon(true);
            t.start();
        }

        static JComponent create() {
            Class<?> inputC, gameC, panelC;
            try {
                inputC = Class.forName("Input");
                gameC = Class.forName("Game");
                panelC = Class.forName("GamePanel");
            } catch (ClassNotFoundException e) {
                System.err.println("[Room404] Your game classes were not found next to this file - using the built-in game.");
                return null;
            }
            try {
                java.lang.reflect.Constructor<?> ic = inputC.getDeclaredConstructor();
                ic.setAccessible(true);
                Object input = ic.newInstance();

                Object game = null;
                try {
                    Class<?> profC = Class.forName("CharacterProfile");
                    java.lang.reflect.Constructor<?> pc = profC.getDeclaredConstructor();
                    pc.setAccessible(true);
                    Object profile = pc.newInstance();
                    try { profC.getMethod("setName", String.class).invoke(profile, Prefs.name); }
                    catch (Exception ignored) { }
                    for (String m : new String[]{"setSkin", "setStyle", "setCharacter", "setLook", "setAvatar", "setType"}) {
                        try { profC.getMethod(m, int.class).invoke(profile, Prefs.color); } catch (Exception ignored) { }
                    }
                    java.lang.reflect.Constructor<?> gc = gameC.getDeclaredConstructor(inputC, profC);
                    gc.setAccessible(true);
                    game = gc.newInstance(input, profile);
                } catch (Exception e) {
                    System.err.println("[Room404] Could not build Game with a profile, trying GamePanel(null, input) like Main.java: " + e);
                }

                java.lang.reflect.Constructor<?> panc = panelC.getDeclaredConstructor(gameC, inputC);
                panc.setAccessible(true);
                return (JComponent) panc.newInstance(game, input);
            } catch (Throwable t) {
                t.printStackTrace();
                System.err.println("[Room404] Starting your game failed - using the built-in game instead.");
                return null;
            }
        }

        /** best effort: stop the game's loop if the panel has a stop() method */
        static void stop(JComponent panel) {
            try { panel.getClass().getMethod("stop").invoke(panel); } catch (Throwable ignored) { }
        }
    }

    // ==================================================================
    //  SCREEN PLUMBING
    // ==================================================================
    interface Screen {
        void onShow();
        void onHide();
    }

    static class App extends JFrame {
        final CardLayout cards = new CardLayout();
        final JPanel root = new JPanel(cards);
        final Map<String, JComponent> screens = new HashMap<>();
        String current;

        App() {
            super("ROOM 404");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            root.setBackground(Color.BLACK);
            addScreen("menu", new MenuPanel(this));
            addScreen("game", new MiniGamePanel(this));     // built-in backup game
            root.setPreferredSize(new Dimension(1280, 720));
            setContentPane(root);
            pack();
            setLocationRelativeTo(null);
            applyWindowMode();
            show("menu");

            // F10 while playing your real game = back to this menu
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F10
                        && "real".equals(current)) {
                    leaveRealGame(true);
                    return true;
                }
                return false;
            });
        }

        /** PLAY: starts YOUR game (Game / GamePanel / Input). Falls back to the built-in mini game. */
        void startPlay() {
            JComponent real = RealGame.create();
            if (real == null) { show("game"); return; }
            leaveRealGame(false);
            addScreen("real", real);
            show("real");
            root.revalidate();
            SwingUtilities.invokeLater(real::requestFocusInWindow);
        }

        void leaveRealGame(boolean toMenu) {
            JComponent real = screens.remove("real");
            if (toMenu) show("menu");
            if (real != null) {
                RealGame.stop(real);
                root.remove(real);
                root.revalidate();
                root.repaint();
            }
        }

        void addScreen(String name, JComponent c) {
            screens.put(name, c);
            root.add(c, name);
        }

        void show(String name) {
            if (current != null) {
                JComponent old = screens.get(current);
                if (old instanceof Screen) ((Screen) old).onHide();
            }
            current = name;
            cards.show(root, name);
            JComponent now = screens.get(name);
            if (now instanceof Screen) ((Screen) now).onShow();
        }

        void applyWindowMode() {
            setExtendedState(Prefs.maximized ? JFrame.MAXIMIZED_BOTH : JFrame.NORMAL);
        }
    }

    /** Draws at 1280x720 and scales to the window. */
    static abstract class ScaledPanel extends JPanel {
        double scale = 1, offX = 0, offY = 0;

        ScaledPanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
        }

        Graphics2D begin(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            scale = Math.min(getWidth() / (double) LW, getHeight() / (double) LH);
            offX = (getWidth() - LW * scale) / 2;
            offY = (getHeight() - LH * scale) / 2;
            g2.translate(offX, offY);
            g2.scale(scale, scale);
            g2.setClip(0, 0, LW, LH);
            return g2;
        }

        Point2D toLogical(Point p) {
            return new Point2D.Double((p.x - offX) / scale, (p.y - offY) / scale);
        }
    }

    static Font bigFont(float size) {
        Font f = new Font("Impact", Font.BOLD, (int) size);
        if (!f.getFamily().equals("Impact")) f = new Font(Font.SANS_SERIF, Font.BOLD, (int) size);
        return f;
    }

    /** vignette + scanlines + glitch strips (shared look) */
    static void overlay(Graphics2D g, Random rng, int glitch) { overlay(g, rng, glitch, 3); }

    static void overlay(Graphics2D g, Random rng, int glitch, int step) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(LW / 2.0, LH / 2.0), LW * 0.62f,
                new float[]{0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 215)}));
        g.fillRect(0, 0, LW, LH);
        if (Prefs.scanlines) {
            g.setColor(new Color(0, 0, 0, 55));
            for (int y = 0; y < LH; y += step) g.drawLine(0, y, LW, y);
        }
        if (glitch > 0 || rng.nextInt(60) == 0) {
            g.setColor(new Color(255, 255, 255, 10 + rng.nextInt(18)));
            g.fillRect(0, 0, LW, LH);
        }
        for (int i = 0; i < 40; i++) {
            int y = rng.nextInt(LH);
            int w = 6 + rng.nextInt(26);
            int h = 2 + rng.nextInt(4);
            g.setColor(i % 2 == 0 ? new Color(150, 20, 40, 130) : new Color(90, 80, 100, 110));
            g.fillRect(rng.nextBoolean() ? 0 : LW - w, y, w, h);
        }
        if (glitch > 0) {
            g.setColor(new Color(200, 30, 50, 60));
            g.fillRect(0, rng.nextInt(LH - 40), LW, 3 + rng.nextInt(10));
        }
    }

    static void drawPlayer(Graphics2D g, double x, double y, double r, Color c, String name) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new Ellipse2D.Double(x - r, y - r * 0.5 + r * 0.6, r * 2, r * 1.1));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(c.darker().darker());
        g.setStroke(new BasicStroke((float) Math.max(2, r / 6)));
        g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(x - r * 0.45, y - r * 0.25, r * 0.28, r * 0.28));
        g.fill(new Ellipse2D.Double(x + r * 0.17, y - r * 0.25, r * 0.28, r * 0.28));
        if (name != null) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) Math.max(12, r * 0.9)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(0xE9D6D9));
            g.drawString(name, (int) (x - fm.stringWidth(name) / 2.0), (int) (y - r - 6));
        }
    }

    // ==================================================================
    //  MENU: home / character / settings / loading  (one pixel-art scene)
    //  Everything is drawn with the SAME font (ui) at low resolution and scaled up,
    //  so the buttons, character panel and settings all share one pixel look.
    // ==================================================================
    static Font ui(float size) { return new Font(Font.MONOSPACED, Font.BOLD, (int) size); }

    /** pixel-art characters for the CHARACTER screen (same body, different outfits) */
    static class Chars {
        static final String[] NAMES = {"STUDENT", "SENIOR", "ATHLETE", "BOOKWORM"};
        //                          hair      skin      shirt     tie/trim  pants
        static final int[][] PAL = {
                {0x15121A, 0xE8C9B0, 0xC7CBEA, 0x14141C, 0x1B2038},
                {0x5A3A22, 0xD9B08C, 0xF0F0F0, 0xB3121F, 0x4A4A55},
                {0x7A5A2E, 0xC99A72, 0xC0392B, 0xF0F0F0, 0x202020},
                {0x2B1B12, 0xF0D3BC, 0x6E8F6A, 0xE0D8A0, 0x5A4632}};
        static final String[] BODY = {
                "......H..H......",
                "....HHHHHHHH....",
                "...HHHHHHHHHH...",
                "..HHHHHHHHHHHH..",
                "..HHHHHHHHHHHH..",
                "..HHHSSSSSSHHH..",
                "..HHSSSSSSSSHH..",
                "..HHSEESSEESHH..",
                "...HSSSSSSSSH...",
                "....SSSSSSSS....",
                ".....SSSSSS.....",
                "......SSSS......",
                "...WWWWTTWWWW...",
                "..WWWWWTTWWWWW..",
                ".WWWWWWTTWWWWWW.",
                ".WWWWWWTTWWWWWW.",
                ".SSWWWWTTWWWWSS.",
                ".SSWWWWTTWWWWSS.",
                ".SSWWWWWWWWWWSS.",
                ".SS.WWWWWWWW.SS.",
                "....PPPPPPPP....",
                "....PPPPPPPP....",
                "....PPPPPPPP....",
                "....PPP..PPP....",
                "....PPP..PPP....",
                "....PPP..PPP....",
                "...BBBB..BBBB...",
                "...BBBB..BBBB..."};
        static final BufferedImage[] IMG = new BufferedImage[NAMES.length];

        static BufferedImage image(int idx) {
            if (IMG[idx] != null) return IMG[idx];
            int w = 16, h = BODY.length;
            int[] pal = PAL[idx];
            int[][] px = new int[h + 2][w + 2];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int col;
                    switch (BODY[y].charAt(x)) {
                        case 'H': col = pal[0]; break;
                        case 'S': col = pal[1]; break;
                        case 'W': col = pal[2]; break;
                        case 'T': col = pal[3]; break;
                        case 'P': col = pal[4]; break;
                        case 'E': col = 0x0C0A10; break;
                        case 'B': col = 0x0E0E12; break;
                        default: col = -1;
                    }
                    if (col >= 0) px[y + 1][x + 1] = 0xFF000000 | col;
                }
            }
            BufferedImage img = new BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h + 2; y++) {
                for (int x = 0; x < w + 2; x++) {
                    int v = px[y][x];
                    if (v == 0) {
                        boolean edge = (y > 0 && px[y - 1][x] != 0) || (y < h + 1 && px[y + 1][x] != 0)
                                || (x > 0 && px[y][x - 1] != 0) || (x < w + 1 && px[y][x + 1] != 0);
                        if (edge) v = 0xFF08070B;
                    }
                    img.setRGB(x, y, v);
                }
            }
            IMG[idx] = img;
            return img;
        }
    }

    static class MenuPanel extends ScaledPanel implements Screen, ActionListener {
        static final int HOME = 0, CHARACTER = 1, SETTINGS = 2, LOADING = 3;
        static final int MENU = 0, BOX = 1, ARROW_L = 2, ARROW_R = 3, HIT = 4;
        static final Color LINE = new Color(0xC9BDBF);
        static final int CX = 800;      // centre of the character panel
        static final int SX = 765;      // centre of the settings panel

        static class Btn {
            final Rectangle r;
            final int kind;
            String text;
            final Runnable act;
            Btn(Rectangle r, int kind, String text, Runnable act) {
                this.r = r; this.kind = kind; this.text = text; this.act = act;
            }
        }

        final App app;
        final String[] items = {"PLAY", "SETTINGS", "CHARACTER", "QUIT"};
        final java.util.List<Btn> btns = new ArrayList<>();
        int mode = HOME, selected = 0, tick = 0, glitch = 0;
        boolean editingName, replaceOnType;
        double loadProgress;
        int loadDone;
        Point2D mouse = new Point2D.Double(-100, -100);
        BufferedImage buf;
        final Timer timer = new Timer(33, this);
        final Random rng = new Random();
        final int[] dripX = new int[16], dripLen = new int[16], dripW = new int[16];

        MenuPanel(App app) {
            this.app = app;
            Random r = new Random(404);
            for (int i = 0; i < dripX.length; i++) {
                dripX[i] = 140 + r.nextInt(330);
                dripLen[i] = 14 + r.nextInt(34);
                dripW[i] = 4 + r.nextInt(4);
            }
            rebuild();

            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { onKey(e); }
                @Override public void keyTyped(KeyEvent e) { onTyped(e.getKeyChar()); }
            });
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover(e.getPoint()); }
                @Override public void mouseDragged(MouseEvent e) { hover(e.getPoint()); }
                @Override public void mousePressed(MouseEvent e) { click(e.getPoint()); }
            };
            addMouseMotionListener(ma);
            addMouseListener(ma);
        }

        @Override public void onShow() {
            loadProgress = 0; loadDone = 0;
            setMode(HOME);
            timer.start();
            requestFocusInWindow();
        }

        @Override public void onHide() { timer.stop(); Prefs.save(); }

        // ---------------- buttons ----------------
        void rebuild() {
            btns.clear();
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                btns.add(new Btn(new Rectangle(115, 333 + i * 66, 272, 56), MENU, items[i], () -> activate(idx)));
            }
            if (mode == CHARACTER) {
                btns.add(new Btn(new Rectangle(CX - 215, 325, 64, 84), ARROW_L, "", () -> cycleChar(-1)));
                btns.add(new Btn(new Rectangle(CX + 151, 325, 64, 84), ARROW_R, "", () -> cycleChar(1)));
                for (int i = 0; i < Chars.NAMES.length; i++) {
                    final int idx = i;
                    btns.add(new Btn(new Rectangle(CX - 60 + i * 36, 548, 28, 28), HIT, "", () -> pickChar(idx)));
                }
                btns.add(new Btn(new Rectangle(CX - 110, 586, 220, 44), HIT, "", () -> { editingName = true; replaceOnType = true; }));
                btns.add(new Btn(new Rectangle(CX - 110, 650, 220, 44), BOX, "BACK", () -> setMode(HOME)));
            } else if (mode == SETTINGS) {
                btns.add(new Btn(new Rectangle(SX - 75, 225, 48, 48), BOX, "-", () -> setVolume(Prefs.volume - 10)));
                for (int i = 0; i < 10; i++) {
                    final int idx = i;
                    btns.add(new Btn(new Rectangle(SX - 17 + i * 26, 225, 22, 48), HIT, "", () -> setVolume((idx + 1) * 10)));
                }
                btns.add(new Btn(new Rectangle(SX + 247, 225, 48, 48), BOX, "+", () -> setVolume(Prefs.volume + 10)));
                btns.add(new Btn(new Rectangle(SX - 75, 295, 48, 48), BOX, "<", () -> cycleDifficulty(-1)));
                btns.add(new Btn(new Rectangle(SX + 247, 295, 48, 48), BOX, ">", () -> cycleDifficulty(1)));
                btns.add(new Btn(new Rectangle(SX - 75, 365, 370, 48), BOX, Prefs.scanlines ? "ON" : "OFF", () -> {
                    Prefs.scanlines = !Prefs.scanlines; Prefs.save(); Sfx.tone(500, 60, 0.4); rebuild();
                }));
                btns.add(new Btn(new Rectangle(SX - 75, 435, 370, 48), BOX, Prefs.maximized ? "MAXIMIZED" : "NORMAL", () -> {
                    Prefs.maximized = !Prefs.maximized; Prefs.save(); Sfx.tone(500, 60, 0.4);
                    app.applyWindowMode(); rebuild();
                }));
                btns.add(new Btn(new Rectangle(SX - 110, 520, 220, 48), BOX, "BACK", () -> setMode(HOME)));
            }
        }

        void setMode(int m) {
            if (mode == CHARACTER) endEdit();
            Prefs.save();
            mode = m;
            editingName = false;
            rebuild();
        }

        void activate(int i) {
            if (mode == LOADING) return;
            selected = i;
            endEdit();
            Sfx.tone(500, 90, 0.5);
            if (i == 0) startLoading();
            else if (i == 1) setMode(mode == SETTINGS ? HOME : SETTINGS);
            else if (i == 2) setMode(mode == CHARACTER ? HOME : CHARACTER);
            else System.exit(0);
        }

        void startLoading() {
            mode = LOADING;
            loadProgress = 0; loadDone = 0;
            RealGame.warmUp();
            rebuild();
        }

        void move(int d) {
            selected = (selected + d + items.length) % items.length;
            glitch = 4;
            Sfx.tone(300, 40, 0.3);
        }

        void cycleChar(int d) {
            int n = Chars.NAMES.length;
            Prefs.color = (Prefs.color + d + n) % n;
            Prefs.save();
            glitch = 3;
            Sfx.tone(420, 60, 0.4);
        }

        void pickChar(int i) {
            Prefs.color = i;
            Prefs.save();
            glitch = 3;
            Sfx.tone(520, 60, 0.4);
        }

        void setVolume(int v) {
            Prefs.volume = Math.max(0, Math.min(100, v));
            Prefs.save();
            Sfx.tone(600, 100, 0.6);
        }

        void cycleDifficulty(int d) {
            Prefs.difficulty = (Prefs.difficulty + d + 3) % 3;
            Prefs.save();
            Sfx.tone(450, 60, 0.4);
        }

        void endEdit() {
            if (editingName) {
                editingName = false;
                if (Prefs.name.trim().isEmpty()) Prefs.name = "Player";
                Prefs.save();
            }
        }

        // ---------------- input ----------------
        void onKey(KeyEvent e) {
            int k = e.getKeyCode();
            if (editingName) {
                if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE) endEdit();
                else if (k == KeyEvent.VK_BACK_SPACE) {
                    if (replaceOnType) { Prefs.name = ""; replaceOnType = false; }
                    else if (!Prefs.name.isEmpty()) Prefs.name = Prefs.name.substring(0, Prefs.name.length() - 1);
                }
                return;
            }
            if (k == KeyEvent.VK_ESCAPE) { if (mode != HOME) setMode(HOME); return; }
            if (mode == LOADING) return;
            if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) move(-1);
            else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) move(1);
            else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) activate(selected);
            else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) {
                if (mode == CHARACTER) cycleChar(-1); else if (mode == SETTINGS) setVolume(Prefs.volume - 10);
            } else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) {
                if (mode == CHARACTER) cycleChar(1); else if (mode == SETTINGS) setVolume(Prefs.volume + 10);
            }
        }

        void onTyped(char c) {
            if (!editingName || c < 32 || c > 126) return;
            if (replaceOnType) { Prefs.name = ""; replaceOnType = false; }
            if (Prefs.name.length() < 12) Prefs.name += c;
        }

        void hover(Point p) {
            mouse = toLogical(p);
            boolean any = false;
            for (int i = 0; i < btns.size(); i++) {
                Btn b = btns.get(i);
                if (!b.r.contains(mouse)) continue;
                if (mode == LOADING) break;
                any = true;
                if (b.kind == MENU && i != selected) { selected = i; glitch = 4; Sfx.tone(300, 40, 0.3); }
                break;
            }
            setCursor(Cursor.getPredefinedCursor(any ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        void click(Point p) {
            requestFocusInWindow();
            mouse = toLogical(p);
            if (mode == LOADING) return;
            Btn hit = null;
            for (Btn b : btns) if (b.r.contains(mouse)) { hit = b; break; }
            if (editingName && (hit == null || hit.kind != HIT || hit.r.width != 220)) endEdit();
            if (hit != null) {
                if (hit.kind != MENU) Sfx.tone(480, 50, 0.4);
                hit.act.run();
            }
        }

        boolean hot(Btn b) { return mode != LOADING && b.r.contains(mouse); }

        // ---------------- loop ----------------
        @Override public void actionPerformed(ActionEvent e) {
            tick++;
            if (glitch > 0) glitch--;
            else if (rng.nextInt(90) == 0) glitch = 3 + rng.nextInt(4);
            if (mode == LOADING) {
                double speed = (tick % 40 < 6) ? 0.15 : 1.0 + rng.nextDouble() * 0.9;
                loadProgress = Math.min(loadProgress + speed, RealGame.warmDone ? 100 : 94);
                if (loadProgress >= 100 && ++loadDone > 10) {
                    mode = HOME;
                    rebuild();
                    app.startPlay();
                }
            }
            repaint();
        }

        // ---------------- painting ----------------
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (buf == null) buf = new BufferedImage(LW / 2, LH / 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = buf.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.scale(0.5, 0.5);

            drawHallway(g);
            drawClock(g, 1160, 130, 56);
            drawTitle(g);
            drawLeftMenu(g);
            if (mode == CHARACTER) drawCharacter(g);
            else if (mode == SETTINGS) drawSettings(g);
            else if (mode == LOADING) drawLoading(g);
            drawEscapeText(g);
            g.setFont(ui(14));
            g.setColor(new Color(150, 130, 135));
            g.drawString(mode == LOADING ? "ESC = cancel" : "CLICK A BUTTON  |  W/S + ENTER  |  ESC = back", 115, 700);
            overlay(g, rng, glitch, 4);
            g.dispose();

            scale = Math.min(getWidth() / (double) LW, getHeight() / (double) LH);
            offX = (getWidth() - LW * scale) / 2;
            offY = (getHeight() - LH * scale) / 2;
            Graphics2D o = (Graphics2D) g0.create();
            o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            o.drawImage(buf, (int) offX, (int) offY, (int) (LW * scale), (int) (LH * scale), null);
            o.dispose();
        }

        void drawHallway(Graphics2D g) {
            g.setColor(new Color(0x0B0506));
            g.fillRect(0, 0, LW, LH);
            int bx1 = 560, bx2 = 800, by1 = 230, by2 = 380;
            g.setPaint(new GradientPaint(0, 0, new Color(0x120A0C), 0, by1, new Color(0x1D1214)));
            g.fillPolygon(new int[]{0, LW, bx2, bx1}, new int[]{0, 0, by1, by1}, 4);
            g.setPaint(new GradientPaint(0, LH, new Color(0x3A2528), 0, by2, new Color(0x0E0708)));
            g.fillPolygon(new int[]{0, LW, bx2, bx1}, new int[]{LH, LH, by2, by2}, 4);
            g.setColor(new Color(0, 0, 0, 120));
            for (int k = 0; k <= 10; k++) g.drawLine(k * 128, LH, bx1 + k * 24, by2);
            for (int s = 1; s <= 8; s++) {
                double t = Math.pow(s / 8.0, 2.2);
                int y = (int) (by2 + (LH - by2) * t);
                g.drawLine((int) (bx1 - bx1 * t), y, (int) (bx2 + (LW - bx2) * t), y);
            }
            g.setPaint(new GradientPaint(0, 0, new Color(0x0A0607), 560, 0, new Color(0x1A1012)));
            g.fillPolygon(new int[]{0, bx1, bx1, 0}, new int[]{0, by1, by2, LH}, 4);
            g.setPaint(new GradientPaint(LW, 0, new Color(0x4A3A3C), bx2, 0, new Color(0x1C1214)));
            g.fillPolygon(new int[]{LW, bx2, bx2, LW}, new int[]{0, by1, by2, LH}, 4);

            double[][] spans = {{0.05, 0.28}, {0.34, 0.52}, {0.58, 0.70}};
            for (double[] sp : spans) {
                double[] a = wallPoint(sp[0]), b = wallPoint(sp[1]);
                double h0 = a[3] - a[1], h1 = b[3] - b[1];
                Path2D win = new Path2D.Double();
                win.moveTo(a[0], a[1] + h0 * 0.22);
                win.lineTo(b[0], b[1] + h1 * 0.22);
                win.lineTo(b[0], b[3] - h1 * 0.30);
                win.lineTo(a[0], a[3] - h0 * 0.30);
                win.closePath();
                g.setColor(new Color(0x050304));
                g.fill(win);
                g.setColor(new Color(0x6B5558));
                g.setStroke(new BasicStroke(4));
                g.draw(win);
                double mx = (a[0] + b[0]) / 2;
                double my1 = (a[1] + h0 * 0.22 + b[1] + h1 * 0.22) / 2;
                double my2 = (a[3] - h0 * 0.30 + b[3] - h1 * 0.30) / 2;
                g.setStroke(new BasicStroke(3));
                g.draw(new Line2D.Double(mx, my1, mx, my2));
                g.setStroke(new BasicStroke(1));
            }
            for (double t : new double[]{0.0, 0.31, 0.55, 0.72}) {
                double[] p = wallPoint(t);
                g.setColor(new Color(0x2B1D1F));
                g.fill(new Rectangle2D.Double(p[0] - 6 + 12 * t, p[1], 14 * (1 - t) + 3, p[3] - p[1]));
            }
            g.setPaint(new RadialGradientPaint(new Point2D.Double(680, 305), 170,
                    new float[]{0f, 1f}, new Color[]{new Color(0, 0, 0, 255), new Color(0, 0, 0, 0)}));
            g.fillRect(480, 160, 400, 300);

            double[] ls = {0.0, 0.45, 0.72, 0.88};
            for (double t : ls) {
                double w = 200 * (1 - t) + 20, h = 34 * (1 - t) + 4;
                double cx = 680 - 20 * t;
                double y = 90 * (1 - t) + by1 * t - 10;
                int a = 130 + rng.nextInt(glitch > 0 ? 100 : 25);
                g.setColor(new Color(200, 190, 190, Math.min(255, a)));
                g.fill(new Rectangle2D.Double(cx - w / 2, y, w, h));
            }
        }

        double[] wallPoint(double t) {
            return new double[]{LW - (LW - 800) * t, 230 * t, LW - (LW - 800) * t, LH - (LH - 380) * t};
        }

        void drawClock(Graphics2D g, int cx, int cy, int r) {
            g.setColor(new Color(0x14090B));
            g.fill(new Ellipse2D.Double(cx - r - 8, cy - r - 8, 2 * r + 16, 2 * r + 16));
            g.setColor(new Color(0x7A6568));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(new Color(0x2A1D1F));
            g.fill(new Ellipse2D.Double(cx - r + 4, cy - r + 4, 2 * r - 8, 2 * r - 8));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
            g.setColor(new Color(0xB9A6A9));
            for (int n = 1; n <= 12; n++) {
                double a = Math.toRadians(n * 30 - 90);
                String s = String.valueOf(n);
                g.drawString(s, (int) (cx + Math.cos(a) * (r - 16)) - g.getFontMetrics().stringWidth(s) / 2,
                        (int) (cy + Math.sin(a) * (r - 16)) + 5);
            }
            g.setColor(new Color(0xD8C8CA));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double hour = Math.toRadians(10 * 30 - 90);
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(hour) * r * 0.5, cy + Math.sin(hour) * r * 0.5));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double min = Math.toRadians(-90 + (tick % 30 == 0 ? 3 : 0));
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(min) * r * 0.72, cy + Math.sin(min) * r * 0.72));
            g.setStroke(new BasicStroke(1));
            g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        }


        void stretchText(Graphics2D g, String s, int x, int baseline, int targetW, Color c) {
            g.setFont(g.getFont());
            int w = g.getFontMetrics().stringWidth(s);
            AffineTransform old = g.getTransform();
            g.translate(x, baseline);
            g.scale(targetW / (double) Math.max(1, w), 1.0);
            g.setColor(c);
            g.drawString(s, 0, 0);
            g.setTransform(old);
        }

        void drawTitle(Graphics2D g) {
            Font big = bigFont(112);
            int jx = glitch > 0 ? rng.nextInt(7) - 3 : 0;
            g.setFont(big);
            stretchText(g, "ROOM", 84 + jx, 132, 410, new Color(120, 0, 20, 150));
            stretchText(g, "ROOM", 80 + jx, 128, 410, BONE);
            g.setColor(BONE);
            for (int i = 0; i < 7; i++) {
                int x = 92 + i * 62 + (i * 17) % 23;
                g.fillRect(x, 124, 4, 16 + (i * 37) % 40);
            }
            AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(-6), 260, 250);
            g.setFont(big.deriveFont(142f));
            stretchText(g, "404", 134 + jx, 256, 370, new Color(60, 0, 8));
            stretchText(g, "404", 128 + jx, 250, 370, BLOOD);
            g.setTransform(old);
            g.setColor(new Color(0xA50F1B));
            for (int i = 0; i < dripX.length; i++) {
                int len = dripLen[i] + (int) (3 * Math.sin((tick + i * 7) / 18.0));
                g.fillRect(dripX[i], 246, dripW[i], len);
                g.fillOval(dripX[i] - 1, 246 + len - 2, dripW[i] + 2, dripW[i] + 4);
            }
        }

        void drawLeftMenu(Graphics2D g) {
            g.setFont(ui(34));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 4; i++) {
                Btn b = btns.get(i);
                Rectangle r = b.r;
                boolean sel = (i == selected) && mode != LOADING || (mode == LOADING && i == 0);
                if (sel) {
                    int pulse = 40 + (int) (25 * Math.sin(tick / 5.0));
                    g.setColor(new Color(90, 0, 10, 150 + pulse));
                    g.fill(r);
                    g.setColor(RED);
                    g.setStroke(new BasicStroke(4));
                    g.drawRect(r.x, r.y, r.width, r.height);
                    g.setStroke(new BasicStroke(1));
                    g.setColor(RED);
                } else {
                    g.setColor(new Color(0, 0, 0, 200));
                    g.fill(r);
                    g.setColor(LINE);
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(r.x, r.y, r.width, r.height);
                    g.drawRect(r.x + 4, r.y + 4, r.width - 8, r.height - 8);
                    g.setStroke(new BasicStroke(1));
                    g.setColor(LIGHT);
                }
                int ty = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(b.text, r.x + (r.width - fm.stringWidth(b.text)) / 2, ty);
                if (sel) {
                    int ax = 64 + (int) (4 * Math.sin(tick / 4.0));
                    int ay = r.y + r.height / 2;
                    g.setColor(new Color(0xF0D9DB));
                    g.fillPolygon(new int[]{ax, ax, ax + 26}, new int[]{ay - 20, ay + 20, ay}, 3);
                }
            }
        }

        // small dark tag with a thin border ("CHARACTER", "STUDENT", "MAP"...)
        void tag(Graphics2D g, Rectangle r, String text) {
            g.setColor(new Color(0, 0, 0, 215));
            g.fill(r);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setStroke(new BasicStroke(1));
            g.setFont(ui(26));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(LIGHT);
            g.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2,
                    r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
        }

        void drawBox(Graphics2D g, Btn b) {
            boolean h = hot(b);
            Rectangle r = b.r;
            g.setColor(h ? new Color(90, 0, 10, 210) : new Color(0, 0, 0, 200));
            g.fill(r);
            g.setColor(h ? RED : LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(r.x, r.y, r.width, r.height);
            if (!h) g.drawRect(r.x + 4, r.y + 4, r.width - 8, r.height - 8);
            g.setStroke(new BasicStroke(1));
            g.setFont(ui(26));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(h ? RED : LIGHT);
            g.drawString(b.text, r.x + (r.width - fm.stringWidth(b.text)) / 2,
                    r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
        }

        void drawArrow(Graphics2D g, Btn b) {
            Rectangle r = b.r;
            boolean left = b.kind == ARROW_L;
            g.setColor(hot(b) ? RED : new Color(0xDDD3D4));
            g.setStroke(new BasicStroke(8, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            int x1 = left ? r.x + r.width - 14 : r.x + 14, x2 = left ? r.x + 14 : r.x + r.width - 14;
            g.drawLine(x1, r.y + 8, x2, r.y + r.height / 2);
            g.drawLine(x2, r.y + r.height / 2, x1, r.y + r.height - 8);
            g.setStroke(new BasicStroke(1));
        }

        void drawPanelButtons(Graphics2D g) {
            for (Btn b : btns) {
                if (b.kind == BOX) drawBox(g, b);
                else if (b.kind == ARROW_L || b.kind == ARROW_R) drawArrow(g, b);
            }
        }

        void drawCharacter(Graphics2D g) {
            int idx = Prefs.color % Chars.NAMES.length;
            tag(g, new Rectangle(CX - 110, 140, 220, 44), "CHARACTER");

            g.setPaint(new RadialGradientPaint(new Point2D.Double(CX, 345), 240,
                    new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 38), new Color(0, 0, 0, 0)}));
            g.fillRect(CX - 250, 100, 500, 500);

            g.setColor(new Color(0, 0, 0, 120));
            g.fillOval(CX - 76, 466, 152, 18);
            BufferedImage img = Chars.image(idx);
            int sc = 9;
            int bob = (Math.sin(tick / 9.0) > 0.2) ? 2 : 0;
            g.drawImage(img, CX - img.getWidth() * sc / 2, 200 + bob, img.getWidth() * sc, img.getHeight() * sc, null);

            tag(g, new Rectangle(CX - 110, 490, 220, 44), Chars.NAMES[idx]);

            for (int i = 0; i < Chars.NAMES.length; i++) {
                Rectangle d = new Rectangle(CX - 60 + i * 36 + 6, 548 + 6, 16, 16);
                boolean on = i == idx;
                boolean h = d.contains(mouse.getX() + 0, mouse.getY() + 0) || new Rectangle(CX - 60 + i * 36, 548, 28, 28).contains(mouse);
                g.setColor(on ? RED : (h ? LIGHT : LINE));
                if (on) g.fill(d); else { g.setStroke(new BasicStroke(2)); g.drawRect(d.x, d.y, d.width, d.height); g.setStroke(new BasicStroke(1)); }
            }

            Rectangle nf = new Rectangle(CX - 110, 586, 220, 44);
            boolean nh = nf.contains(mouse) || editingName;
            g.setColor(nh ? new Color(90, 0, 10, 200) : new Color(0, 0, 0, 200));
            g.fill(nf);
            g.setColor(nh ? RED : LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(nf.x, nf.y, nf.width, nf.height);
            g.setStroke(new BasicStroke(1));
            g.setFont(ui(26));
            FontMetrics fm = g.getFontMetrics();
            String shown = Prefs.name + (editingName && (tick / 10) % 2 == 0 ? "_" : "");
            g.setColor(LIGHT);
            g.drawString(shown, nf.x + (nf.width - fm.stringWidth(shown)) / 2,
                    nf.y + (nf.height - fm.getHeight()) / 2 + fm.getAscent());
            g.setFont(ui(22));
            g.setColor(new Color(170, 150, 155));
            String lab = "NAME";
            g.drawString(lab, nf.x - 14 - g.getFontMetrics().stringWidth(lab),
                    nf.y + (nf.height - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
            drawPanelButtons(g);
        }

        void drawSettings(Graphics2D g) {
            tag(g, new Rectangle(SX - 110, 140, 220, 44), "SETTINGS");
            String[] labels = {"VOLUME", "DIFFICULTY", "SCANLINES", "WINDOW"};
            g.setFont(ui(26));
            g.setColor(LIGHT);
            for (int i = 0; i < 4; i++) {
                FontMetrics fm = g.getFontMetrics();
                g.drawString(labels[i], 470, 225 + i * 70 + (48 - fm.getHeight()) / 2 + fm.getAscent());
            }
            // volume segments
            for (int i = 0; i < 10; i++) {
                Rectangle s = new Rectangle(SX - 17 + i * 26, 225, 22, 48);
                boolean on = Prefs.volume >= (i + 1) * 10;
                boolean h = s.contains(mouse);
                g.setColor(on ? (h ? new Color(0xFF3B3B) : BLOOD) : new Color(0, 0, 0, 200));
                g.fill(s);
                g.setColor(h ? RED : LINE);
                g.setStroke(new BasicStroke(2));
                g.drawRect(s.x, s.y, s.width, s.height);
                g.setStroke(new BasicStroke(1));
            }
            // difficulty value
            Rectangle dv = new Rectangle(SX - 17, 295, 256, 48);
            g.setColor(new Color(0, 0, 0, 200));
            g.fill(dv);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(dv.x, dv.y, dv.width, dv.height);
            g.setStroke(new BasicStroke(1));
            String[] dn = {"EASY", "NORMAL", "HARD"};
            String d = dn[Math.max(0, Math.min(2, Prefs.difficulty))];
            g.setColor(Prefs.difficulty == 2 ? RED : LIGHT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(d, dv.x + (dv.width - fm.stringWidth(d)) / 2, dv.y + (dv.height - fm.getHeight()) / 2 + fm.getAscent());
            drawPanelButtons(g);
        }

        void drawLoading(Graphics2D g) {
            // MAP card
            Rectangle card = new Rectangle(860, 220, 360, 330);
            g.setColor(new Color(0, 0, 0, 200));
            g.fill(card);
            Shape oldClip = g.getClip();
            AffineTransform at = g.getTransform();
            g.setClip(new Rectangle(864, 264, 352, 164));
            g.translate(864, 264);
            g.scale(352.0 / LW, 352.0 / LW);
            drawHallway(g);
            g.setTransform(at);
            g.setClip(oldClip);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(864, 392, 352, 36);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(card.x, card.y, card.width, card.height);
            g.drawLine(card.x, 428, card.x + card.width, 428);
            g.setStroke(new BasicStroke(1));
            tag(g, new Rectangle(860, 220, 130, 44), "MAP");
            g.setFont(ui(26));
            FontMetrics fm = g.getFontMetrics();
            String t1 = "SCHOOL HALLWAY";
            g.setColor(LIGHT);
            g.drawString(t1, 864 + (352 - fm.stringWidth(t1)) / 2, 392 + (36 - fm.getHeight()) / 2 + fm.getAscent());
            g.setFont(ui(19));
            g.setColor(new Color(0xCFC3C5));
            g.drawString("A familiar school...", 884, 472);
            g.drawString("but something feels wrong.", 884, 502);

            // LOADING bar
            g.setFont(ui(44));
            String lt = "LOADING" + ".".repeat(1 + (tick / 10) % 3);
            g.setColor(LIGHT);
            g.drawString(lt, 318 + (588 - g.getFontMetrics().stringWidth("LOADING...")) / 2, 622);
            Rectangle bar = new Rectangle(318, 634, 588, 26);
            g.setColor(new Color(0, 0, 0, 220));
            g.fill(bar);
            g.setColor(BLOOD);
            g.fillRect(bar.x + 4, bar.y + 4, (int) ((bar.width - 8) * loadProgress / 100.0), bar.height - 8);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(bar.x, bar.y, bar.width, bar.height);
            g.setStroke(new BasicStroke(1));
            g.setFont(ui(28));
            g.setColor(LIGHT);
            g.drawString((int) loadProgress + "%", 926, 656);
        }

        void drawEscapeText(Graphics2D g) {
            AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(-22), 1160, 660);
            g.setFont(ui(26));
            g.setColor(new Color(150, 15, 25, 210));
            g.drawString("FIND A WAY", 1080, 650);
            g.drawString("TO ESCAPE...", 1104, 682);
            g.setTransform(old);
        }
    }

    // ==================================================================
    //  THE GAME
    // ==================================================================
    static class MiniGamePanel extends ScaledPanel implements Screen, ActionListener {
        static final int T = 40, COLS = 32, ROWS = 18, NEED_KEYS = 3;
        static final double PLAYER_SPEED = 3.0;
        static final double[] GHOST_SPEED = {1.6, 2.1, 2.6};

        final App app;
        final Timer timer = new Timer(16, this);
        final Random rng = new Random();
        char[][] map;
        double px, py, gx, gy, nextGx, nextGy;
        int keys, state, graceTicks, tick, pathTimer, msgTicks, deadTicks;
        long startNanos, endNanos;
        boolean up, down, left, right, doorOpen;
        String msg = "";

        MiniGamePanel(App app) {
            this.app = app;
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { key(e.getKeyCode(), true); }
                @Override public void keyReleased(KeyEvent e) { key(e.getKeyCode(), false); }
            });
        }

        void key(int k, boolean down0) {
            if (k == KeyEvent.VK_W || k == KeyEvent.VK_UP) up = down0;
            else if (k == KeyEvent.VK_S || k == KeyEvent.VK_DOWN) down = down0;
            else if (k == KeyEvent.VK_A || k == KeyEvent.VK_LEFT) left = down0;
            else if (k == KeyEvent.VK_D || k == KeyEvent.VK_RIGHT) right = down0;
            else if (down0 && k == KeyEvent.VK_R && state != 0) restart();
            else if (down0 && k == KeyEvent.VK_ESCAPE) app.show("menu");
        }

        @Override public void onShow() { restart(); timer.start(); requestFocusInWindow(); }
        @Override public void onHide() { timer.stop(); up = down = left = right = false; }

        // ---------- level ----------
        void buildMap() {
            map = new char[ROWS][COLS];
            for (char[] row : map) java.util.Arrays.fill(row, '#');
            carve(1, 6, 30, 8);                                   // corridor
            int[][] rooms = {{1, 6}, {8, 14}, {16, 23}, {25, 30}};
            for (int[] r : rooms) {
                carve(r[0], 1, r[1], 4);                          // top rooms
                carve(r[0], 10, r[1], 16);                        // bottom rooms
            }
            int[] doors = {3, 11, 20, 27};
            for (int d : doors) { map[5][d] = '.'; map[9][d] = '.'; }
            map[9][27] = 'D';                                     // ROOM 404 door
            int[][] desks = {{4, 3}, {5, 3}, {12, 2}, {13, 2}, {17, 2}, {18, 2}, {17, 3},
                    {5, 12}, {6, 12}, {9, 12}, {10, 12}, {13, 12}, {18, 12}, {19, 12},
                    {22, 12}, {22, 15}, {25, 12}, {26, 12}, {28, 3}, {29, 3}};
            for (int[] d : desks) map[d[1]][d[0]] = 'T';
            map[2][2] = 'K';
            map[3][21] = 'K';
            map[14][11] = 'K';
            map[13][28] = 'E';                                    // exit
        }

        void carve(int c1, int r1, int c2, int r2) {
            for (int r = r1; r <= r2; r++) for (int c = c1; c <= c2; c++) map[r][c] = '.';
        }

        void restart() {
            buildMap();
            px = 2 * T + T / 2.0; py = 7 * T + T / 2.0;
            gx = 20 * T + T / 2.0; gy = 13 * T + T / 2.0;
            nextGx = gx; nextGy = gy;
            keys = 0; state = 0; doorOpen = false; tick = 0; deadTicks = 0;
            graceTicks = 240; pathTimer = 0;
            startNanos = System.nanoTime();
            say("Find " + NEED_KEYS + " keys. Open ROOM 404. Escape.", 240);
        }

        void say(String m, int ticks) { msg = m; msgTicks = ticks; }

        boolean isWall(int tx, int ty) {
            if (tx < 0 || ty < 0 || tx >= COLS || ty >= ROWS) return true;
            char c = map[ty][tx];
            return c == '#' || c == 'T' || (c == 'D' && !doorOpen);
        }

        boolean solid(double x, double y) { return isWall((int) Math.floor(x / T), (int) Math.floor(y / T)); }

        boolean blocked(double x, double y) {
            double h = 11;
            return solid(x - h, y - h) || solid(x + h, y - h) || solid(x - h, y + h) || solid(x + h, y + h);
        }

        // ---------- update ----------
        @Override public void actionPerformed(ActionEvent e) {
            tick++;
            if (msgTicks > 0) msgTicks--;
            if (state == 1) deadTicks++;
            if (state == 0) update();
            repaint();
        }

        void update() {
            double dx = (right ? 1 : 0) - (left ? 1 : 0), dy = (down ? 1 : 0) - (up ? 1 : 0);
            if (dx != 0 && dy != 0) { dx *= 0.7071; dy *= 0.7071; }
            double nx = px + dx * PLAYER_SPEED;
            if (!blocked(nx, py)) px = nx;
            double ny = py + dy * PLAYER_SPEED;
            if (!blocked(px, ny)) py = ny;

            int tx = (int) (px / T), ty = (int) (py / T);
            char c = map[ty][tx];
            if (c == 'K') {
                map[ty][tx] = '.';
                keys++;
                Sfx.tone(880, 140, 0.6);
                say(keys < NEED_KEYS ? "Key found (" + keys + "/" + NEED_KEYS + ")" : "All keys! Open the red door of ROOM 404.", 180);
            } else if (c == 'E') {
                state = 2;
                endNanos = System.nanoTime();
                Sfx.tone(660, 500, 0.7);
            }

            // locked door
            if (!doorOpen) {
                double ddx = px - (27 * T + T / 2.0), ddy = py - (9 * T + T / 2.0);
                if (Math.sqrt(ddx * ddx + ddy * ddy) < 34) {
                    if (keys >= NEED_KEYS) {
                        doorOpen = true;
                        Sfx.tone(200, 350, 0.7);
                        say("The door of ROOM 404 creaks open...", 200);
                    } else if (tick % 60 == 0) {
                        say("Locked. Keys: " + keys + "/" + NEED_KEYS, 90);
                    }
                }
            }

            // ghost
            if (graceTicks > 0) { graceTicks--; return; }
            if (pathTimer-- <= 0) { updateGhostPath(); pathTimer = 8; }
            double speed = GHOST_SPEED[Math.max(0, Math.min(2, Prefs.difficulty))];
            double mx = nextGx - gx, my = nextGy - gy, dist = Math.sqrt(mx * mx + my * my);
            if (dist > speed) { gx += mx / dist * speed; gy += my / dist * speed; }
            else { gx = nextGx; gy = nextGy; }

            double cx = gx - px, cy = gy - py;
            if (Math.sqrt(cx * cx + cy * cy) < 24) {
                state = 1;
                deadTicks = 0;
                Sfx.tone(90, 700, 1.0);
            }
        }

        void updateGhostPath() {
            int sx = (int) (gx / T), sy = (int) (gy / T), tx = (int) (px / T), ty = (int) (py / T);
            if (sx == tx && sy == ty) { nextGx = px; nextGy = py; return; }
            int[][] prev = new int[ROWS][COLS];
            for (int[] row : prev) java.util.Arrays.fill(row, -1);
            prev[sy][sx] = sy * COLS + sx;
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{sx, sy});
            int[] ox = {1, -1, 0, 0}, oy = {0, 0, 1, -1};
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                if (cur[0] == tx && cur[1] == ty) break;
                for (int d = 0; d < 4; d++) {
                    int nx = cur[0] + ox[d], ny = cur[1] + oy[d];
                    if (isWall(nx, ny) || prev[ny][nx] != -1) continue;
                    prev[ny][nx] = cur[1] * COLS + cur[0];
                    q.add(new int[]{nx, ny});
                }
            }
            if (prev[ty][tx] == -1) { nextGx = px; nextGy = py; return; }
            int cx = tx, cy = ty;
            while (true) {
                int p = prev[cy][cx];
                int pxT = p % COLS, pyT = p / COLS;
                if (pxT == sx && pyT == sy) break;
                cx = pxT; cy = pyT;
            }
            nextGx = cx * T + T / 2.0;
            nextGy = cy * T + T / 2.0;
        }

        // ---------- drawing ----------
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = begin(g0);
            if (state == 1 && deadTicks < 25) g.translate(rng.nextInt(13) - 6, rng.nextInt(13) - 6);

            drawWorld(g);
            drawGhost(g);
            drawPlayer(g, px, py, 11, Prefs.COLORS[Prefs.color % Prefs.COLORS.length], Prefs.name);
            drawDarkness(g);
            drawHud(g);

            if (state == 1) drawDead(g);
            else if (state == 2) drawWin(g);
            overlay(g, rng, state == 1 && deadTicks < 25 ? 3 : 0);
            g.dispose();
        }

        void drawWorld(Graphics2D g) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    char ch = map[r][c];
                    int x = c * T, y = r * T;
                    if (ch == '#') {
                        g.setColor(new Color(0x2B1D20)); g.fillRect(x, y, T, T);
                        g.setColor(new Color(0x3D2A2D)); g.drawRect(x, y, T - 1, T - 1);
                        g.setColor(new Color(0x1A1113)); g.fillRect(x + 5, y + 5, T - 10, T - 10);
                        continue;
                    }
                    g.setColor(((r + c) & 1) == 0 ? new Color(0x2A1F21) : new Color(0x241A1C));
                    g.fillRect(x, y, T, T);
                    if (ch == 'T') {
                        g.setColor(new Color(0x4A3322)); g.fillRect(x + 3, y + 6, T - 6, T - 12);
                        g.setColor(new Color(0x2E1E14)); g.drawRect(x + 3, y + 6, T - 7, T - 13);
                    } else if (ch == 'D') {
                        g.setColor(new Color(0x6A0D17)); g.fillRect(x + 2, y + 2, T - 4, T - 4);
                        g.setColor(new Color(0xE01B1B)); g.drawRect(x + 2, y + 2, T - 5, T - 5);
                        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
                        g.setColor(Color.WHITE); g.drawString("404", x + 6, y + 25);
                    } else if (ch == 'K') {
                        double bob = Math.sin((tick + c * 5) / 10.0) * 3;
                        g.setColor(new Color(255, 215, 0, 70)); g.fill(new Ellipse2D.Double(x + 6, y + 6 + bob, 28, 28));
                        g.setColor(new Color(0xFFD54F)); g.fill(new Ellipse2D.Double(x + 12, y + 10 + bob, 12, 12));
                        g.fillRect(x + 17, y + 20 + (int) bob, 4, 12);
                        g.fillRect(x + 17, y + 26 + (int) bob, 9, 3);
                    } else if (ch == 'E') {
                        g.setColor(new Color(60, 200, 90, 90 + (int) (50 * Math.sin(tick / 8.0))));
                        g.fillRect(x, y, T, T);
                        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
                        g.setColor(new Color(0xB8F5C6)); g.drawString("EXIT", x + 4, y + 25);
                    }
                }
            }
        }

        void drawGhost(Graphics2D g) {
            double bob = Math.sin(tick / 8.0) * 3;
            int alpha = (tick % 47 < 3) ? 110 : 205;
            g.setColor(new Color(225, 215, 222, alpha));
            g.fill(new Ellipse2D.Double(gx - 14, gy - 18 + bob, 28, 30));
            Path2D skirt = new Path2D.Double();
            skirt.moveTo(gx - 14, gy - 4 + bob);
            skirt.lineTo(gx + 14, gy - 4 + bob);
            skirt.lineTo(gx + 14, gy + 18 + bob);
            for (int i = 0; i < 4; i++) {
                skirt.lineTo(gx + 14 - (i + 0.5) * 7, gy + 10 + bob);
                skirt.lineTo(gx + 14 - (i + 1) * 7, gy + 18 + bob);
            }
            skirt.closePath();
            g.fill(skirt);
            g.setColor(new Color(0xE01B1B));
            g.fill(new Ellipse2D.Double(gx - 8, gy - 8 + bob, 5, 7));
            g.fill(new Ellipse2D.Double(gx + 3, gy - 8 + bob, 5, 7));
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(gx - 4, gy + 2 + bob, 8, 10));
        }

        void drawDarkness(Graphics2D g) {
            float radius = 250f + (float) (6 * Math.sin(tick / 6.0)) + (tick % 97 < 2 ? -40 : 0);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), radius,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 120), new Color(0, 0, 0, 252)}));
            g.fillRect(0, 0, LW, LH);
            double d = Math.hypot(gx - px, gy - py);
            if (state == 0 && graceTicks <= 0 && d < 230) {
                g.setColor(new Color(160, 0, 0, (int) ((230 - d) / 230 * 95)));
                g.fillRect(0, 0, LW, LH);
            }
        }

        void drawHud(Graphics2D g) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(12, 10, 250, 34);
            g.setColor(keys >= NEED_KEYS ? new Color(0xFFD54F) : LIGHT);
            g.drawString("KEYS " + keys + "/" + NEED_KEYS, 22, 34);
            long t = (state == 0 ? System.nanoTime() : endNanos > 0 ? endNanos : System.nanoTime()) - startNanos;
            g.setColor(new Color(0xB9A6A9));
            g.drawString(String.format("%.1fs", t / 1e9), 170, 34);
            if (msgTicks > 0 && state == 0) {
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
                FontMetrics fm = g.getFontMetrics();
                int w = fm.stringWidth(msg);
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect((LW - w) / 2 - 14, 640, w + 28, 44);
                g.setColor(BONE);
                g.drawString(msg, (LW - w) / 2, 671);
            }
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            g.setColor(new Color(150, 130, 135));
            g.drawString("WASD/Arrows = move   ESC = menu", 1000, 706);
        }

        void centered(Graphics2D g, String s, int y) {
            g.drawString(s, (LW - g.getFontMetrics().stringWidth(s)) / 2, y);
        }

        void drawDead(Graphics2D g) {
            g.setColor(new Color(60, 0, 0, Math.min(210, 60 + deadTicks * 6)));
            g.fillRect(0, 0, LW, LH);
            if (deadTicks < 6) { g.setColor(new Color(255, 255, 255, 180)); g.fillRect(0, 0, LW, LH); }
            g.setFont(bigFont(130));
            g.setColor(BLOOD);
            centered(g, "YOU DIED", 340);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
            g.setColor(BONE);
            centered(g, "Press R to try again  |  ESC for menu", 420);
        }

        void drawWin(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(0, 0, LW, LH);
            g.setFont(bigFont(120));
            g.setColor(new Color(0x7BE08A));
            centered(g, "YOU ESCAPED", 330);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
            g.setColor(BONE);
            centered(g, String.format("Time: %.1f seconds", (endNanos - startNanos) / 1e9), 400);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
            centered(g, "Press R to play again  |  ESC for menu", 450);
        }
    }

    // ==================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
        });
    }
}