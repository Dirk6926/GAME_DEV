import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Title / character creation screen and the loading screen. */
public class MenuScreen {
    public enum Phase { MAIN, SETTINGS, LOADING, CREATE }
    private static final String[] ROWS = {"Character", "Name", "Skin", "Hair style", "Hair color", "Outfit", "Glasses"};
    private static final String[] TIPS = {
        "Running is loud. The thing in the halls is listening.",
        "Crouch (C) or walk quietly (Ctrl) to stay unheard.",
        "Shine your flashlight at the shadow to drive it back.",
        "Notes you find are saved in your journal (J).",
        "The faculty room and Room 404 require keys.",
        "Room 404 is the only way out."
    };

    private final CharacterProfile profile = new CharacterProfile();
    private BufferedImage background;
    private BufferedImage fog;
    private BufferedImage titleLogo;
    private BufferedImage startImage;
    private BufferedImage settingsImage;

    private Phase phase = Phase.MAIN;
    private int selectedButton;
    private double t;
    private double loadTime;
    private boolean start;
    private int mouseX = -1;
    private int mouseY = -1;

    private final Font big = new Font("SansSerif", Font.BOLD, 26);
    private final Font mid = new Font("SansSerif", Font.PLAIN, 22);
    private final Font small = new Font("SansSerif", Font.PLAIN, 16);

    public MenuScreen() {
        loadMenuImages();
    }

    private void loadMenuImages() {
        background = loadMenuImage("background.png");
        fog = loadMenuImage("Fog.png");
        titleLogo = loadMenuImage("loop 404.png");
        startImage = loadMenuImage("START.png");
        settingsImage = loadMenuImage("SETTINGS.png");
    }

    private BufferedImage loadMenuImage(String fileName) {
        String[] candidates = {
            "assets/menu/" + fileName,
            "SchoolEscape/assets/menu/" + fileName,
            "./SchoolEscape/assets/menu/" + fileName
        };

        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) {
                try {
                    return ImageIO.read(file);
                } catch (IOException e) {
                    System.err.println("Unable to read menu PNG " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        System.err.println("Missing menu PNG: " + fileName);
        return null;
    }

    public CharacterProfile profile() { return profile; }
    public Phase phase() { return phase; }
    public boolean wantsStart() { return start; }

    public void beginLoading() {
        phase = Phase.LOADING;
        start = false;
        loadTime = 0;
    }

    public void setMouse(int mx, int my, int w, int h) {
        double s = w / 1280.0;
        mouseX = (int) (mx / s);
        mouseY = (int) (my / s);
    }

    /* ---------------------------------------------------------- input */
    public void key(Input in, double dt) {
        t += dt;
        if (phase == Phase.LOADING) return;

        if (phase == Phase.MAIN) {
            if (in.pressed(KeyEvent.VK_UP)) selectedButton = (selectedButton + 1) % 2;
            if (in.pressed(KeyEvent.VK_DOWN)) selectedButton = (selectedButton + 1) % 2;
            if (in.pressed(KeyEvent.VK_ENTER)) activateSelected();
        } else if (phase == Phase.SETTINGS) {
            if (in.pressed(KeyEvent.VK_ESCAPE) || in.pressed(KeyEvent.VK_ENTER)) phase = Phase.MAIN;
        }
        in.clearEdges();
    }

    private void activateSelected() {
        if (selectedButton == 0) start = true;
        else phase = Phase.SETTINGS;
    }

    public void click(int mx, int my, int w, int h) {
        if (phase == Phase.LOADING) return;
        setMouse(mx, my, w, h);

        if (phase == Phase.MAIN) {
            if (isStartHovered()) {
                selectedButton = 0;
                start = true;
            } else if (isSettingsHovered()) {
                selectedButton = 1;
                phase = Phase.SETTINGS;
            }
        } else if (phase == Phase.SETTINGS) {
            phase = Phase.MAIN;
        }
    }

    /* ---------------------------------------------------------- drawing */
    public double progress() { return Math.min(1, loadTime / 3.2); }
    public void tickLoading(double dt) { loadTime += dt; }
    public boolean loadingDone(boolean gameReady) { return gameReady && loadTime >= 3.2; }

    public void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = w / 1280.0;

        if (background != null) {
            g.drawImage(background, 0, 0, w, h, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(4, 6, 12), 0, h, new Color(14, 20, 34)));
            g.fillRect(0, 0, w, h);
        }

        AffineTransform base = g.getTransform();
        g.scale(s, s);

        if (fog != null) {
            int fogX = (int) (Math.sin(t * 0.18) * 35);
            int fogY = (int) (Math.cos(t * 0.12) * 12);
            g.drawImage(fog, fogX - 40, fogY - 20, 1360, 760, null);
        }

        if (titleLogo != null) {
            int logoH = 500;
            int logoW = (int) (logoH * (double) titleLogo.getWidth() / titleLogo.getHeight());
            g.drawImage(titleLogo, 640 - logoW / 2, 20, logoW, logoH, null);
        }

        switch (phase) {
            case MAIN, CREATE -> mainMenu(g);
            case SETTINGS -> settingsMenu(g);
            case LOADING -> loading(g);
        }
        g.setTransform(base);
    }

    private boolean isStartHovered() {
        return mouseX >= 490 && mouseX <= 790 && mouseY >= 385 && mouseY <= 470;
    }

    private boolean isSettingsHovered() {
        return mouseX >= 490 && mouseX <= 790 && mouseY >= 495 && mouseY <= 580;
    }

    private void drawButton(Graphics2D g, BufferedImage image, int y, boolean hover) {
        if (image == null) return;

        int targetW = 300;
        int targetH = (int) (targetW * (double) image.getHeight() / image.getWidth());
        int x = 640 - targetW / 2;

        if (hover) {
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f + (float) ((Math.sin(t * 4) + 1) * 0.12)));
            g.setColor(new Color(190, 215, 255));
            g.fillRoundRect(x - 18, y - 12, targetW + 36, targetH + 24, 24, 24);
            g.setComposite(old);
        }

        g.drawImage(image, x, y, targetW, targetH, null);
    }

    private void mainMenu(Graphics2D g) {
        boolean startHover = isStartHovered() || selectedButton == 0;
        boolean settingsHover = isSettingsHovered() || selectedButton == 1;
        drawButton(g, startImage, 350, startHover);
        drawButton(g, settingsImage, 500, settingsHover);
    }

    private void settingsMenu(Graphics2D g) {
        g.setColor(new Color(4, 6, 12, 220));
        g.fillRoundRect(340, 180, 600, 380, 24, 24);
        g.setColor(new Color(210, 220, 235, 180));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(340, 180, 600, 380, 24, 24);

        g.setFont(big);
        g.setColor(Color.WHITE);
        String titleText = "SETTINGS";
        g.drawString(titleText, 640 - g.getFontMetrics().stringWidth(titleText) / 2, 260);

        g.setFont(small);
        g.drawString("Press ENTER or ESC to go back", 640 - g.getFontMetrics().stringWidth("Press ENTER or ESC to go back") / 2, 340);
    }

    private void loading(Graphics2D g) {
        AffineTransform a = g.getTransform();
        g.translate(560, 190); g.scale(2.0, 2.0);
        Hud.drawBust(g, profile, t);
        g.setTransform(a);
        g.setFont(big); g.setColor(Color.WHITE);
        String n = profile.getName();
        g.drawString(n, 640 - g.getFontMetrics().stringWidth(n) / 2, 410);
        g.setFont(mid); g.setColor(new Color(210, 215, 225));
        String l = "Loading" + ".".repeat(1 + (int) (t * 3) % 3);
        g.drawString(l, 640 - g.getFontMetrics().stringWidth(l) / 2, 470);
        g.setColor(new Color(0, 0, 0, 190)); g.fillRoundRect(340, 490, 600, 20, 20, 20);
        g.setColor(new Color(205, 30, 36)); g.fillRoundRect(340, 490, (int) (600 * progress()), 20, 20, 20);
        g.setColor(new Color(255, 255, 255, 120)); g.drawRoundRect(340, 490, 600, 20, 20, 20);
        g.setFont(small); g.setColor(new Color(190, 195, 205));
        String tip = "Tip: " + TIPS[((int) (loadTime / 1.1)) % TIPS.length];
        g.drawString(tip, 640 - g.getFontMetrics().stringWidth(tip) / 2, 550);
    }

    public void typed(char c) { } // compatibility; name entry is handled by Game's name-entry state
}