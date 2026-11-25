import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Custom title menu and loading screen. */
public class MenuScreen {
    public enum Phase { MAIN, SETTINGS, LOADING }

    private static final String[] TIPS = {
        "Running is loud. The thing in the halls is listening.",
        "Crouch (C) or walk quietly (Ctrl) to stay unheard.",
        "Shine your flashlight at the shadow to drive it back.",
        "Notes you find are saved in your journal (J).",
        "The faculty room and Room 404 require keys.",
        "Room 404 is the only way out."
    };

    /* =====================================================================================
     *  ANIMATION & EFFECT SETTINGS  -  change the numbers in this block to tune the menu.
     *  (Every effect further down in the code is tagged with "EFFECT:" so you can find it.)
     *  All positions are in the 1280 x 720 design space.
     * ===================================================================================== */

    // ---- LAYOUT -------------------------------------------------------------------------
    private static final double LOGO_SCALE = 1.0;          // size of the "404 LOOP" title (1.0 = exactly as designed in Figma).
                                                           // Keep it 1.0 or bigger: smaller shows the picture's straight edges.
    private static final int LOGO_TOP = 0;                 // distance of the title from the top of the screen
    private static final int[] BUTTON_TOP = {390, 490};    // y of the START button and the SETTINGS button
    private static final double BUTTON_SCALE = 1.10;       // size of both buttons (1.0 = the PNG's own size)
    private static final int HIT_PADDING = 14;             // extra clickable space around each button

    // ---- EFFECT: TITLE GLOW PULSE -------------------------------------------------------
    private static final double TITLE_PULSE_SPEED = 1.8;   // bigger = pulses faster
    private static final float TITLE_GLOW_MIN = 0.15f;     // glow strength at the dimmest point (0 = invisible)
    private static final float TITLE_GLOW_MAX = 1.00f;     // glow strength at the brightest point (1 = full)
    private static final Color TITLE_GLOW_COLOR = new Color(255, 35, 25);
    private static final double TITLE_BREATH_AMOUNT = 0.03;  // EFFECT: how much the title grows/shrinks (0.03 = +-3%, 0 = off)
    private static final double TITLE_BREATH_SPEED = 1.8;    // EFFECT: breathing speed (same as TITLE_PULSE_SPEED = grows as it glows)
    private static final int TITLE_GLOW_SIZE = 26;         // how far the glow spreads (pixels)

    // ---- EFFECT: BUTTON HOVER (glow + grow) ---------------------------------------------
    private static final double HOVER_GROW = 0.10;         // 0.10 = button becomes 10 % bigger when hovered/selected
    private static final double HOVER_SPEED = 12;          // bigger = the glow/grow animation is snappier
    private static final double GLOW_PULSE_SPEED = 5;      // how fast the hover glow breathes
    private static final double GLOW_PULSE_AMOUNT = 0.20;  // 0 = steady glow, 0.2 = gentle breathing
    private static final Color BUTTON_GLOW_COLOR = new Color(255, 40, 30);    // soft outer glow around the letters
    private static final Color BUTTON_CORE_COLOR = new Color(255, 160, 100);  // bright inner glow hugging the letters
    private static final int BUTTON_GLOW_SIZE = 16;        // how far the outer glow spreads (pixels)
    private static final float TEXT_BRIGHTEN = 2.4f;       // how much brighter the letters get on hover (1 = no change)

    // ---- EFFECT: FOG DRIFT --------------------------------------------------------------
    private static final double FOG_OVERSCAN = 1.10;       // fog is drawn this much wider than the screen so its edges stay hidden
    private static final double FOG_DRIFT_X = 40;          // how far it slides left/right (pixels). Keep <= (FOG_OVERSCAN-1)*640
    private static final double FOG_DRIFT_SPEED = 0.18;    // bigger = slides faster
    private static final double FOG_BOB_Y = 8;             // how far it floats up/down (pixels)
    private static final double FOG_BOB_SPEED = 0.12;      // bigger = floats faster
    private static final double FOG_EXTRA_BELOW = 14;      // how far the fog hangs below the screen (must be > FOG_BOB_Y)
    private static final float FOG_OPACITY = 1.0f;         // 1 = as exported from Figma, lower = more see-through

    private final CharacterProfile profile = new CharacterProfile();
    private BufferedImage background;
    private BufferedImage fog;          // ready to draw (cleaned + scaled)
    private BufferedImage titleLogo;    // ready to draw (cleaned + scaled)
    private BufferedImage logoGlowWide, logoGlowTight;
    private int logoGlowPad;
    private final Button[] buttons = new Button[2];   // 0 = START, 1 = SETTINGS

    private Phase phase = Phase.MAIN;
    private int selectedButton = -1;         // -1 = nothing selected (so nothing is highlighted at the start)
    private boolean usingKeyboard;           // true after Up/Down was pressed, false again when the mouse moves
    private double t;
    private double loadTime;
    private boolean start;
    private int mouseX = -1;
    private int mouseY = -1;

    private final Font big = new Font("SansSerif", Font.BOLD, 26);
    private final Font small = new Font("SansSerif", Font.PLAIN, 16);

    /** One menu button: its picture, a brighter copy, its glow pictures and how "hovered" it is right now. */
    private static final class Button {
        BufferedImage base, hot, glowWide, glowCore;
        int pad;
        int top;
        double amount;      // 0 = idle ... 1 = fully hovered/selected (eases between them)
        double width() { return base.getWidth() * BUTTON_SCALE; }
        double height() { return base.getHeight() * BUTTON_SCALE; }
        Rectangle2D hitBox() {
            return new Rectangle2D.Double(640 - width() / 2 - HIT_PADDING, top - HIT_PADDING,
                    width() + 2 * HIT_PADDING, height() + 2 * HIT_PADDING);
        }
    }

    public MenuScreen() {
        loadMenuImages();
    }

    /* ---------------------------------------------------------- loading the pictures */
    private void loadMenuImages() {
        background = loadMenuImage("background.png");

        BufferedImage fogRaw = loadMenuImage("Fog.png");
        if (fogRaw != null) {
            fog = scaleTo(cleanEdges(fogRaw), (int) Math.round(1280 * FOG_OVERSCAN),
                    (int) Math.round(fogRaw.getHeight() * FOG_OVERSCAN * 1280.0 / fogRaw.getWidth()));
        }

        BufferedImage logoRaw = loadMenuImage("loop 404.png");
        if (logoRaw != null) {
            // the title picture is as wide as the screen (1280) in Figma, so its soft edges line up with the screen edges
            int logoW = (int) Math.round(1280 * LOGO_SCALE);
            int logoH = (int) Math.round(logoRaw.getHeight() * (double) logoW / logoRaw.getWidth());
            titleLogo = scaleTo(cleanEdges(logoRaw), logoW, logoH);
            logoGlowPad = TITLE_GLOW_SIZE * 2;
            // glow made from the RED parts of the title only (the "404" scribble), not the blue smear behind it
            logoGlowWide = makeGlow(titleLogo, logoGlowPad, TITLE_GLOW_SIZE, 1.6f, TITLE_GLOW_COLOR, true);
            logoGlowTight = makeGlow(titleLogo, logoGlowPad, Math.max(2, TITLE_GLOW_SIZE / 3), 2.2f, TITLE_GLOW_COLOR, true);
        }

        buttons[0] = makeButton(loadMenuImage("START.png"), "START", BUTTON_TOP[0]);
        buttons[1] = makeButton(loadMenuImage("SETTINGS.png"), "SETTINGS", BUTTON_TOP[1]);
    }

    private BufferedImage loadMenuImage(String fileName) {
        String[] candidates = {
            "assets/menu/" + fileName,
            "SchoolEscape/assets/menu/" + fileName,
            "./SchoolEscape/assets/menu/" + fileName,
            "../assets/menu/" + fileName
        };

        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) return toArgb(img);
                } catch (IOException e) {
                    System.err.println("Unable to read menu PNG " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        System.err.println("Missing menu PNG: " + fileName);
        return null;
    }

    /** Builds a button with its glow. If the PNG is missing, a plain text button is drawn instead so the menu still works. */
    private Button makeButton(BufferedImage image, String label, int top) {
        Button b = new Button();
        b.top = top;
        b.base = image != null ? image : fallbackButton(label);
        b.pad = BUTTON_GLOW_SIZE * 3;
        b.glowWide = makeGlow(b.base, b.pad, BUTTON_GLOW_SIZE, 2.0f, BUTTON_GLOW_COLOR, false);
        b.glowCore = makeGlow(b.base, b.pad, Math.max(2, BUTTON_GLOW_SIZE / 3), 2.0f, BUTTON_CORE_COLOR, false);
        // "hot" copy: same letters, brighter - faded in on top of the normal button while hovered
        b.hot = new RescaleOp(new float[]{TEXT_BRIGHTEN, TEXT_BRIGHTEN, TEXT_BRIGHTEN, 1f},
                new float[]{25, 25, 25, 0}, null).filter(b.base, null);
        return b;
    }

    private static BufferedImage fallbackButton(String label) {
        Font f = new Font(Font.SERIF, Font.BOLD, 56);
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = tmp.createGraphics().getFontMetrics(f);
        BufferedImage img = new BufferedImage(fm.stringWidth(label) + 20, 68, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(f);
        g.setColor(new Color(107, 16, 16));
        g.drawString(label, 10, 52);
        g.dispose();
        return img;
    }

    /* ---------------------------------------------------------- picture helpers */
    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * Soft edges of exported PNGs often carry speckled cyan/purple "noise" colours. That noise shows up as a
     * glittery line along the edge of the fog and the title smear. This pulls the colour of every
     * see-through pixel toward the picture's normal colour, which removes the speckles.
     */
    private static BufferedImage cleanEdges(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        long sr = 0, sg = 0, sb = 0, n = 0;
        for (int p : px) {
            int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            if (a >= 160 && b >= r) { sr += r; sg += g; sb += b; n++; }      // body colour (ignores the red scribble)
        }
        if (n == 0) return src;
        int ar = (int) (sr / n), ag = (int) (sg / n), ab = (int) (sb / n);
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24;
            if (a >= 200) continue;
            double k = 1 - a / 200.0;                                      // 1 = fully see-through
            int r = (int) (((p >> 16) & 255) * (1 - k) + ar * k);
            int g = (int) (((p >> 8) & 255) * (1 - k) + ag * k);
            int b = (int) ((p & 255) * (1 - k) + ab * k);
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    /**
     * Makes a soft coloured glow in the SHAPE of the letters (no box). It copies the opaque parts of src,
     * blurs them and tints them. radius = how far the glow spreads, strength = how bright it is,
     * redOnly = glow only the red parts (used for the title).
     */
    private static BufferedImage makeGlow(BufferedImage src, int pad, int radius, float strength, Color color, boolean redOnly) {
        int sw = src.getWidth(), sh = src.getHeight(), w = sw + 2 * pad, h = sh + 2 * pad;
        float[] a = new float[w * h];
        int[] px = src.getRGB(0, 0, sw, sh, null, 0, sw);
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++) {
                int p = px[y * sw + x];
                float alpha = (p >>> 24) / 255f;
                if (redOnly) {
                    int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                    float redness = Math.max(0f, Math.min(1f, (r - Math.max(g, b) - 25) / 70f));
                    alpha *= redness;
                }
                a[(y + pad) * w + x + pad] = alpha;
            }
        for (int pass = 0; pass < 3; pass++) boxBlur(a, w, h, radius);       // 3 box blurs ~ a smooth gaussian blur
        int rgb = color.getRGB() & 0xFFFFFF;
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) out[i] = ((int) (Math.min(1f, a[i] * strength) * 255) << 24) | rgb;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    private static void boxBlur(float[] a, int w, int h, int r) {
        float[] tmp = new float[Math.max(w, h) + 1];
        float inv = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {                                          // horizontal pass
            tmp[0] = 0;
            for (int x = 0; x < w; x++) tmp[x + 1] = tmp[x] + a[y * w + x];
            for (int x = 0; x < w; x++) a[y * w + x] = (tmp[Math.min(x + r, w - 1) + 1] - tmp[Math.max(x - r, 0)]) * inv;
        }
        for (int x = 0; x < w; x++) {                                          // vertical pass
            tmp[0] = 0;
            for (int y = 0; y < h; y++) tmp[y + 1] = tmp[y] + a[y * w + x];
            for (int y = 0; y < h; y++) a[y * w + x] = (tmp[Math.min(y + r, h - 1) + 1] - tmp[Math.max(y - r, 0)]) * inv;
        }
    }

    public CharacterProfile profile() { return profile; }
    public Phase phase() { return phase; }
    public boolean wantsStart() { return start; }

    public void beginLoading() {
        phase = Phase.LOADING;
        start = false;
        loadTime = 0;
    }

    /** Call this whenever the mouse moves (and on clicks). */
    public void setMouse(int mx, int my, int w, int h) {
        double s = w / 1280.0;
        int nx = (int) (mx / s), ny = (int) (my / s);
        if (nx != mouseX || ny != mouseY) usingKeyboard = false;    // the mouse moved -> the mouse controls the highlight again
        mouseX = nx;
        mouseY = ny;
    }

    /* ---------------------------------------------------------- input */
    public void key(Input in, double dt) {
        t += dt;

        // EFFECT: BUTTON HOVER - each button eases toward "hovered" (1) or "idle" (0) every frame
        int focus = focusedButton();
        double ease = 1 - Math.exp(-HOVER_SPEED * dt);
        for (int i = 0; i < buttons.length; i++) {
            double target = i == focus ? 1 : 0;
            buttons[i].amount += (target - buttons[i].amount) * ease;
        }

        if (phase == Phase.LOADING) return;

        if (phase == Phase.MAIN) {
            if (in.pressed(KeyEvent.VK_UP) || in.pressed(KeyEvent.VK_DOWN)) {
                usingKeyboard = true;
                selectedButton = selectedButton < 0 ? (in.pressed(KeyEvent.VK_UP) ? 1 : 0) : (selectedButton + 1) % 2;
            }
            if (in.pressed(KeyEvent.VK_ENTER)) activate(focus < 0 ? 0 : focus);
        } else if (phase == Phase.SETTINGS) {
            if (in.pressed(KeyEvent.VK_ESCAPE) || in.pressed(KeyEvent.VK_ENTER)) backToMain();
        }
        in.clearEdges();
    }

    /** Which button is highlighted right now: the keyboard's pick, or the one under the mouse. -1 = none. */
    private int focusedButton() {
        if (phase != Phase.MAIN) return -1;
        if (usingKeyboard) return selectedButton;
        return hoveredButton();
    }

    private int hoveredButton() {
        for (int i = 0; i < buttons.length; i++)
            if (buttons[i].hitBox().contains(mouseX, mouseY)) return i;
        return -1;
    }

    private void activate(int button) {
        if (button == 0) start = true;
        else if (button == 1) phase = Phase.SETTINGS;
    }

    private void backToMain() {
        phase = Phase.MAIN;
        usingKeyboard = false;
        selectedButton = -1;
    }

    public void click(int mx, int my, int w, int h) {
        if (phase == Phase.LOADING) return;
        setMouse(mx, my, w, h);

        if (phase == Phase.MAIN) {
            int hit = hoveredButton();
            if (hit >= 0) { selectedButton = hit; activate(hit); }
        } else if (phase == Phase.SETTINGS) {
            backToMain();
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

        // layer 1: background picture (does not move)
        if (background != null) {
            g.drawImage(background, 0, 0, w, h, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(4, 6, 12), 0, h, new Color(14, 20, 34)));
            g.fillRect(0, 0, w, h);
        }

        AffineTransform base = g.getTransform();
        g.scale(s, s);

        drawFog(g);        // layer 2: drifting fog
        drawTitle(g);      // layer 3: "404 LOOP" title with its glow pulse

        // layer 4: whatever screen is showing (buttons / settings / loading)
        switch (phase) {
            case MAIN -> mainMenu(g);
            case SETTINGS -> settingsMenu(g);
            case LOADING -> loading(g);
        }
        g.setTransform(base);
    }

    /** EFFECT: FOG DRIFT - the fog sits on the bottom edge and slowly slides sideways while floating up and down. */
    private void drawFog(Graphics2D g) {
        if (fog == null) return;
        double fw = fog.getWidth(), fh = fog.getHeight();
        double maxDrift = Math.max(0, (fw - 1280) / 2);                          // never slide far enough to show an edge
        double driftX = Math.min(FOG_DRIFT_X, maxDrift) * Math.sin(t * FOG_DRIFT_SPEED);
        double bobY = FOG_BOB_Y * Math.cos(t * FOG_BOB_SPEED);
        double x = (1280 - fw) / 2 + driftX;                                     // centred: the extra width hangs off both sides
        double y = 720 - fh + FOG_EXTRA_BELOW + bobY;                            // sits on the bottom edge of the screen

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FOG_OPACITY));
        g.drawImage(fog, AffineTransform.getTranslateInstance(x, y), null);
        g.setComposite(old);
    }

    /** EFFECT: TITLE GLOW PULSE - a red glow in the shape of the "404" scribble that slowly brightens and dims. */
    private void drawTitle(Graphics2D g) {
        if (titleLogo == null) return;
        int lx = 640 - titleLogo.getWidth() / 2, ly = LOGO_TOP;

        double pulse = 0.5 + 0.5 * Math.sin(t * TITLE_PULSE_SPEED);              // 0..1..0 over and over
        pulse = pulse * pulse * (3 - 2 * pulse);                                 // smooth the ends
        float glow = (float) (TITLE_GLOW_MIN + (TITLE_GLOW_MAX - TITLE_GLOW_MIN) * pulse);

        // EFFECT: TITLE BREATHING - the title and its glow scale together around the logo's centre.
        double breath = 1 + TITLE_BREATH_AMOUNT * Math.sin(t * TITLE_BREATH_SPEED);
        double cx = lx + titleLogo.getWidth() / 2.0, cy = ly + titleLogo.getHeight() / 2.0;
        AffineTransform oldT = g.getTransform();
        Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate(cx, cy);
        g.scale(breath, breath);
        g.translate(-cx, -cy);

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glow));
        g.drawImage(logoGlowWide, lx - logoGlowPad, ly - logoGlowPad, null);     // wide soft halo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, glow * 0.9f)));
        g.drawImage(logoGlowTight, lx - logoGlowPad, ly - logoGlowPad, null);    // tight bright rim
        g.setComposite(old);

        g.drawImage(titleLogo, lx, ly, null);                                    // the title itself, on top of its glow
        g.setTransform(oldT);
        if (oldInterp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
    }

    private void mainMenu(Graphics2D g) {
        for (Button b : buttons) drawButton(g, b);
    }

    /**
     * EFFECT: BUTTON HOVER - an idle button is drawn plainly. While hovered/selected it grows a little,
     * gets brighter letters and a glow in the shape of the letters (no box). b.amount eases 0 -> 1.
     */
    private void drawButton(Graphics2D g, Button b) {
        double e = b.amount * b.amount * (3 - 2 * b.amount);                     // smooth 0..1
        double scale = BUTTON_SCALE * (1 + HOVER_GROW * e);                      // grows from its centre
        int bw = b.base.getWidth(), bh = b.base.getHeight();

        AffineTransform oldT = g.getTransform();
        Composite oldC = g.getComposite();
        g.translate(640, b.top + b.height() / 2);
        g.scale(scale, scale);

        if (e > 0.01) {
            double breathe = 1 - GLOW_PULSE_AMOUNT + GLOW_PULSE_AMOUNT * Math.sin(t * GLOW_PULSE_SPEED);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, e * breathe)));
            g.drawImage(b.glowWide, -b.glowWide.getWidth() / 2, -b.glowWide.getHeight() / 2, null);
            g.drawImage(b.glowCore, -b.glowCore.getWidth() / 2, -b.glowCore.getHeight() / 2, null);
            g.setComposite(oldC);
        }

        g.drawImage(b.base, -bw / 2, -bh / 2, null);                             // the normal button

        if (e > 0.01) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) e));
            g.drawImage(b.hot, -bw / 2, -bh / 2, null);                          // brighter letters fade in on top
            g.setComposite(oldC);
        }
        g.setTransform(oldT);
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
        g.setFont(big);
        g.setColor(Color.WHITE);
        String loadingText = "LOADING";
        g.drawString(loadingText, 640 - g.getFontMetrics().stringWidth(loadingText) / 2, 440);

        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(340, 490, 600, 20, 20, 20);
        g.setColor(new Color(205, 30, 36));
        g.fillRoundRect(340, 490, (int) (600 * progress()), 20, 20, 20);

        g.setFont(small);
        g.setColor(new Color(190, 195, 205));
        String tip = "Tip: " + TIPS[((int) (loadTime / 1.1)) % TIPS.length];
        g.drawString(tip, 640 - g.getFontMetrics().stringWidth(tip) / 2, 550);
    }
}