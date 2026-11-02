import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Every texture in the game is painted with code (no image files).
 * Wall pixels are ARGB where a lowered alpha means "this pixel glows" (alpha 255 = does not glow).
 */
public final class Textures {
    private Textures() {}

    public static final int TW = 128, TH = 224, FS = 128;

    /** A wall texture plus how shiny it is. */
    public static final class Wall {
        public final int[] px;
        public final double gloss;
        Wall(int[] px, double gloss) { this.px = px; this.gloss = gloss; }
    }

    private static final Wall[] walls = new Wall[256];
    public static int[] ceiling;
    public static final int[][] floors = new int[3][];
    public static final float[][] gloss = new float[3][];
    public static Sprite plant, stalker, flashlight, key, battery, note, medkit;
    private static boolean ready;

    /* ----------------------------------------------------------- helpers */
    static int cl(int v) { return v < 0 ? 0 : Math.min(v, 255); }
    static int rgb(int r, int g, int b) { return 0xFF000000 | (cl(r) << 16) | (cl(g) << 8) | cl(b); }

    static int hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    static double rnd(int x, int y, int s) { return (hash(x, y, s) & 0xFFFF) / 65535.0; }

    static double vnoise(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = rnd(xi, yi, s), b = rnd(xi + 1, yi, s), c = rnd(xi, yi + 1, s), d = rnd(xi + 1, yi + 1, s);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static BufferedImage image(int[] px, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, w, h, px, 0, w);
        return im;
    }

    private static int[] pixels(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] a = new int[w * h];
        im.getRGB(0, 0, w, h, a, 0, w);
        return a;
    }

    private static Graphics2D gfx(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Brightness grain so flat colours never look flat. */
    private static void grain(int[] a, int seed, double amount) {
        for (int i = 0; i < a.length; i++) {
            int c = a[i];
            double f = 1 + (rnd(i, i >> 3, seed) - 0.5) * amount;
            a[i] = (c & 0xFF000000) | (cl((int) (((c >> 16) & 255) * f)) << 16)
                    | (cl((int) (((c >> 8) & 255) * f)) << 8) | cl((int) ((c & 255) * f));
        }
    }

    /** Mark a rectangle's bright pixels as glowing. */
    private static void glow(int[] a, int w, int x0, int y0, int x1, int y1, double e, int minRed) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int c = a[y * w + x];
                if (((c >> 16) & 255) >= minRed) a[y * w + x] = (c & 0xFFFFFF) | ((255 - (int) (e * 255)) << 24);
            }
    }

    /* ----------------------------------------------------------- public API */
    public static synchronized void init() {
        if (ready) return;
        ready = true;
        walls[Tile.WALL] = new Wall(plaster(), 0.08);
        walls[Tile.LOCKER] = new Wall(lockers(), 0.38);
        walls[Tile.WINDOW] = new Wall(window(), 0.55);
        walls[Tile.BANNER] = new Wall(banner(), 0.12);
        walls[Tile.BOARD] = new Wall(board(), 0.08);
        walls[Tile.EXTINGUISHER] = new Wall(extinguisherCabinet(), 0.5);
        walls[Tile.EXIT] = new Wall(exitDoor(), 0.3);
        buildCeiling();
        buildFloors();
        buildSprites();
    }

    public static Wall wall(int tile) {
        Wall w = tile >= 0 && tile < walls.length ? walls[tile] : null;
        return w != null ? w : walls[Tile.WALL];
    }

    public static void registerDoor(int tile, String label) { walls[tile] = new Wall(door(label), 0.3); }

    /* ----------------------------------------------------------- walls */
    private static int[] plaster() {
        int[] a = new int[TW * TH];
        for (int y = 0; y < TH; y++) {
            double t = y / (double) TH;
            for (int x = 0; x < TW; x++) {
                double n = vnoise(x / 22.0, y / 22.0, 3) * 0.20 + vnoise(x / 5.0, y / 5.0, 5) * 0.07
                        + (rnd(x, y, 7) - 0.5) * 0.05;
                int r, g, b;
                if (t < 0.60) { r = 206; g = 204; b = 192; } else { r = 66; g = 80; b = 86; }
                if (t >= 0.592 && t < 0.616) { r = 150; g = 148; b = 140; }          // chair rail
                if (t > 0.965) { r = 26; g = 26; b = 30; }                          // baseboard
                double stain = vnoise(x / 3.0, y / 70.0, 11);
                double k = t < 0.6 ? 1 - 0.22 * Math.max(0, stain - 0.52) * 2 : 1;
                double top = t < 0.12 ? 0.82 + t * 1.5 : 1;                         // darker near the ceiling
                double f = (0.80 + n) * k * top;
                a[y * TW + x] = rgb((int) (r * f), (int) (g * f), (int) (b * f));
            }
        }
        return a;
    }

    private static int[] lockers() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        int top = 62, bot = 214, w = 40;
        for (int i = 0; i < 3; i++) {
            int x0 = 3 + i * 42;
            g.setPaint(new GradientPaint(x0, top, new Color(66, 82, 112), x0, bot, new Color(36, 48, 74)));
            g.fillRect(x0, top, w, bot - top);
            g.setColor(new Color(16, 22, 36));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x0, top, w, bot - top);
            g.setColor(new Color(110, 128, 160, 90));
            g.drawLine(x0 + 2, top + 2, x0 + 2, bot - 2);                          // left highlight
            g.setColor(new Color(12, 16, 26));
            for (int s = 0; s < 4; s++) g.fillRect(x0 + 9, top + 10 + s * 6, 22, 2);        // top vents
            for (int s = 0; s < 3; s++) g.fillRect(x0 + 9, bot - 26 + s * 6, 22, 2);        // bottom vents
            g.setColor(new Color(176, 182, 194));
            g.fillRect(x0 + 30, top + 66, 4, 22);                                          // handle
            g.setColor(new Color(220, 220, 215));
            g.fillRect(x0 + 13, top + 36, 14, 9);                                          // number tag
            g.setColor(new Color(40, 40, 40));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g.drawString(String.valueOf(100 + i * 7 + 12), x0 + 14, top + 44);
        }
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, top - 5, TW, 5);                                                     // shadow above
        g.dispose();
        int[] a = pixels(im);
        grain(a, 21, 0.10);
        return a;
    }

    private static int[] window() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        int x0 = 10, y0 = 46, x1 = 118, y1 = 146;
        g.setColor(new Color(46, 48, 52));
        g.fillRect(x0 - 5, y0 - 5, x1 - x0 + 10, y1 - y0 + 10);                           // frame
        for (int pane = 0; pane < 2; pane++) {
            int px0 = x0 + pane * 54 + (pane == 0 ? 0 : 2), pw = 50;
            g.setPaint(new GradientPaint(0, y0, new Color(20, 30, 54), 0, y1, new Color(40, 56, 86)));
            g.fillRect(px0, y0, pw, y1 - y0);
            g.setColor(new Color(60, 84, 120, 110));                                      // moonlit back windows
            g.fillRect(px0 + 6, y0 + 16, 12, 22); g.fillRect(px0 + 30, y0 + 16, 12, 22);
            g.setColor(new Color(8, 10, 16, 230));                                        // desks and chairs
            for (int row = 0; row < 3; row++) {
                int yy = y0 + 48 + row * 15;
                for (int col = 0; col < 3; col++) {
                    int xx = px0 + 3 + col * 16 - row;
                    g.fillRect(xx, yy, 13, 3);
                    g.fillRect(xx + 1, yy + 3, 2, 8);
                    g.fillRect(xx + 10, yy + 3, 2, 8);
                    g.fillRect(xx + 3, yy - 6, 7, 5);
                }
            }
            g.setColor(new Color(150, 180, 230, 34));                                     // reflection streaks
            g.fillPolygon(new int[]{px0 + 8, px0 + 24, px0 + 6, px0 - 10},
                          new int[]{y0, y0, y1, y1}, 4);
        }
        g.setColor(new Color(150, 152, 156));
        g.fillRect(x0 - 8, y1 + 5, x1 - x0 + 16, 6);                                      // sill
        g.dispose();
        int[] a = pixels(im);
        glow(a, TW, x0, y0, x1, y1, 0.07, 0);
        grain(a, 33, 0.07);
        return a;
    }

    private static int[] banner() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        g.setColor(new Color(60, 40, 26));
        g.fillRoundRect(16, 30, 96, 7, 4, 4);                                             // rod
        g.setPaint(new GradientPaint(0, 36, new Color(168, 28, 34), 0, 180, new Color(110, 14, 20)));
        Path2D p = new Path2D.Double();
        p.moveTo(24, 37); p.lineTo(104, 37); p.lineTo(104, 176); p.lineTo(64, 160); p.lineTo(24, 176); p.closePath();
        g.fill(p);
        g.setColor(new Color(214, 176, 90));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(30, 44, 68, 108, 4, 4));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        g.setColor(new Color(240, 232, 220));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("STUDY", 64 - fm.stringWidth("STUDY") / 2, 88);
        g.drawString("HARDER", 64 - fm.stringWidth("HARDER") / 2, 122);
        g.dispose();
        int[] a = pixels(im);
        grain(a, 41, 0.12);
        return a;
    }

    private static int[] board() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        g.setColor(new Color(92, 62, 36));
        g.fillRect(12, 48, 104, 100);
        g.setColor(new Color(176, 132, 88));
        g.fillRect(17, 53, 94, 90);
        Random r = new Random(5);
        for (int i = 0; i < 9; i++) {
            AffineTransform t = g.getTransform();
            int x = 22 + r.nextInt(70), y = 58 + r.nextInt(60);
            g.rotate((r.nextDouble() - 0.5) * 0.4, x, y);
            g.setColor(r.nextInt(3) == 0 ? new Color(236, 224, 150) : new Color(230, 228, 220));
            g.fillRect(x, y, 22, 26);
            g.setColor(new Color(90, 90, 100));
            for (int l = 0; l < 4; l++) g.fillRect(x + 3, y + 7 + l * 5, 16, 1);
            g.setColor(new Color(200, 30, 30));
            g.fillOval(x + 9, y + 2, 4, 4);
            g.setTransform(t);
        }
        g.dispose();
        int[] a = pixels(im);
        grain(a, 51, 0.12);
        return a;
    }

    private static int[] extinguisherCabinet() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        g.setColor(new Color(150, 22, 22));
        g.fillRoundRect(36, 74, 56, 98, 6, 6);
        g.setColor(new Color(40, 50, 60));
        g.fillRect(42, 82, 44, 82);
        g.setPaint(new GradientPaint(52, 0, new Color(200, 36, 36), 76, 0, new Color(120, 14, 14)));
        g.fillRoundRect(53, 98, 22, 60, 10, 10);
        g.setColor(new Color(20, 20, 20));
        g.fillRect(58, 90, 12, 10);
        g.setColor(new Color(220, 220, 220, 150));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g.drawString("FIRE", 54, 76 + 0);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillPolygon(new int[]{42, 62, 46, 42}, new int[]{82, 82, 164, 164}, 4);
        g.dispose();
        int[] a = pixels(im);
        grain(a, 61, 0.08);
        return a;
    }

    private static int[] exitDoor() {
        BufferedImage im = image(plaster(), TW, TH);
        Graphics2D g = gfx(im);
        g.setPaint(new GradientPaint(0, 40, new Color(96, 100, 106), 0, TH, new Color(52, 56, 62)));
        g.fillRect(10, 40, 108, TH - 40);
        g.setColor(new Color(24, 26, 30));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(10, 40, 108, TH - 40);
        g.drawLine(64, 40, 64, TH);
        g.setColor(new Color(170, 172, 178));
        g.fillRect(52, 120, 10, 5); g.fillRect(66, 120, 10, 5);                           // push bars
        g.setColor(new Color(80, 82, 86));                                                // chain
        for (int i = 0; i < 12; i++) g.drawOval(14 + i * 8, 134 + (i % 2) * 3, 8, 5);
        g.setColor(new Color(14, 14, 18));
        g.fillRoundRect(34, 6, 60, 26, 4, 4);                                             // sign box
        g.setColor(new Color(255, 40, 30));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("EXIT", 44, 27);
        g.dispose();
        int[] a = pixels(im);
        glow(a, TW, 34, 6, 94, 32, 0.95, 120);
        grain(a, 71, 0.08);
        return a;
    }

    private static int[] door(String label) {
        BufferedImage im = image(plaster(), TW, TH);
        int[] base = pixels(im);
        for (int y = 36; y < TH - 2; y++)
            for (int x = 26; x < 102; x++) {
                double grain = 0.80 + vnoise(x / 2.2, y / 46.0, 9) * 0.40;
                base[y * TW + x] = rgb((int) (122 * grain), (int) (76 * grain), (int) (44 * grain));
            }
        im = image(base, TW, TH);
        Graphics2D g = gfx(im);
        g.setColor(new Color(58, 40, 28));
        g.setStroke(new BasicStroke(5f));
        g.drawRect(24, 34, 80, TH - 36);                                                   // frame
        g.setColor(new Color(0, 0, 0, 70));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(36, 108, 56, 40);
        g.drawRect(36, 156, 56, 50);                                                       // panels
        g.setColor(new Color(40, 44, 50));
        g.fillRect(42, 50, 44, 46);                                                        // glass slit
        g.setPaint(new GradientPaint(0, 52, new Color(18, 26, 42), 0, 94, new Color(34, 48, 70)));
        g.fillRect(45, 53, 38, 40);
        g.setColor(new Color(214, 176, 80));
        g.fillOval(88, 138, 8, 8);                                                         // handle
        g.setColor(new Color(150, 120, 60));
        g.fillRect(86, 134, 12, 3);
        g.setColor(new Color(18, 22, 34));
        g.fillRoundRect(30, 4, 68, 26, 3, 3);                                              // number plate
        g.setColor(new Color(150, 154, 164));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(30, 4, 68, 26, 3, 3);
        g.setColor(new Color(236, 236, 240));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, label.length() > 4 ? 12 : 20));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, 64 - fm.stringWidth(label) / 2, 24 - (label.length() > 4 ? 4 : 0) + (label.length() > 4 ? 0 : 0));
        g.dispose();
        int[] a = pixels(im);
        grain(a, 81, 0.08);
        return a;
    }

    /* ----------------------------------------------------------- ceiling and floors */
    private static void buildCeiling() {
        int[] a = new int[FS * FS];
        for (int y = 0; y < FS; y++)
            for (int x = 0; x < FS; x++) {
                double n = vnoise(x / 9.0, y / 9.0, 2) * 0.18 + (rnd(x, y, 4) - 0.5) * 0.08;
                int v = (int) (96 * (0.85 + n));
                if (x % 64 < 2 || y % 64 < 2) v = 40;                                      // metal grid
                else if (rnd(x / 3, y / 3, 8) > 0.93) v = (int) (v * 0.55);                // perforations
                a[y * FS + x] = rgb(v, v + 2, v + 6);
            }
        ceiling = a;
    }

    private static void buildFloors() {
        // 0: glossy hallway tiles, 1: room wood, 2: concrete
        int[] t = new int[FS * FS];
        float[] gt = new float[FS * FS];
        for (int y = 0; y < FS; y++)
            for (int x = 0; x < FS; x++) {
                int tx = x / 64, ty = y / 64;
                double tone = 0.90 + rnd(tx, ty, 13) * 0.16;
                double n = vnoise(x / 11.0, y / 11.0, 17) * 0.10 + (rnd(x, y, 19) - 0.5) * 0.03;
                int r = (int) (200 * (tone + n)), g = (int) (196 * (tone + n)), b = (int) (182 * (tone + n));
                float gl = 0.95f;
                int ex = Math.min(x % 64, 63 - x % 64), ey = Math.min(y % 64, 63 - y % 64);
                if (Math.min(ex, ey) < 2) { r = 88; g = 86; b = 80; gl = 0.12f; }          // grout
                if (vnoise(x / 4.0, y / 30.0, 23) > 0.78) { gl *= 0.45f; r *= 0.92; g *= 0.92; b *= 0.92; }   // scuffs
                t[y * FS + x] = rgb(r, g, b);
                gt[y * FS + x] = gl;
            }
        floors[0] = t; gloss[0] = gt;

        int[] w = new int[FS * FS];
        float[] gw = new float[FS * FS];
        for (int y = 0; y < FS; y++)
            for (int x = 0; x < FS; x++) {
                int plank = x / 16;
                double tone = 0.82 + rnd(plank, 0, 29) * 0.30;
                double grain = vnoise(x / 1.5, y / 22.0, 31) * 0.30;
                int r = (int) (128 * (tone + grain)), g = (int) (92 * (tone + grain)), b = (int) (62 * (tone + grain));
                float gl = 0.42f;
                if (x % 16 == 0) { r = 44; g = 30; b = 20; gl = 0.1f; }
                w[y * FS + x] = rgb(r, g, b);
                gw[y * FS + x] = gl;
            }
        floors[1] = w; gloss[1] = gw;

        int[] c = new int[FS * FS];
        float[] gc = new float[FS * FS];
        for (int y = 0; y < FS; y++)
            for (int x = 0; x < FS; x++) {
                double n = vnoise(x / 8.0, y / 8.0, 37) * 0.3 + (rnd(x, y, 39) - 0.5) * 0.12;
                int v = (int) (108 * (0.8 + n));
                c[y * FS + x] = rgb(v, v + 2, v + 4);
                gc[y * FS + x] = 0.10f;
            }
        floors[2] = c; gloss[2] = gc;
    }

    /* ----------------------------------------------------------- sprites */
    private static void buildSprites() {
        plant = plantSprite();
        stalker = stalkerSprite();
        flashlight = flashlightSprite();
        key = keySprite();
        battery = batterySprite();
        note = noteSprite();
        medkit = medkitSprite();
    }

    private static Sprite plantSprite() {
        BufferedImage im = new BufferedImage(110, 150, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        Random r = new Random(3);
        for (int i = 0; i < 16; i++) {
            AffineTransform t = g.getTransform();
            double ang = -Math.PI / 2 + (r.nextDouble() - 0.5) * 2.3;
            g.translate(55, 92);
            g.rotate(ang + Math.PI / 2);
            int len = 40 + r.nextInt(30);
            g.setPaint(new GradientPaint(0, 0, new Color(24, 70, 34), 0, -len, new Color(66, 140, 66)));
            g.fill(new Ellipse2D.Double(-9, -len, 18, len));
            g.setColor(new Color(16, 50, 24));
            g.drawLine(0, 0, 0, -len + 4);
            g.setTransform(t);
        }
        g.setPaint(new GradientPaint(26, 0, new Color(168, 82, 52), 84, 0, new Color(104, 46, 28)));
        g.fillPolygon(new int[]{28, 82, 74, 36}, new int[]{102, 102, 148, 148}, 4);
        g.setColor(new Color(190, 100, 66));
        g.fillRoundRect(24, 94, 62, 12, 5, 5);
        g.dispose();
        return new Sprite(110, 150, pixels(im));
    }

    private static Sprite stalkerSprite() {
        BufferedImage im = new BufferedImage(96, 230, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        Path2D body = new Path2D.Double();
        body.moveTo(26, 62);  body.curveTo(30, 54, 66, 54, 70, 62);                       // shoulders
        body.lineTo(80, 150); body.lineTo(72, 154); body.lineTo(66, 112);                 // right arm
        body.lineTo(66, 130); body.lineTo(70, 226); body.lineTo(52, 226); body.lineTo(48, 150);   // right leg
        body.lineTo(44, 226); body.lineTo(26, 226); body.lineTo(30, 130); body.lineTo(30, 112);   // left leg
        body.lineTo(24, 154); body.lineTo(16, 150); body.closePath();                      // left arm
        g.setColor(new Color(9, 9, 13));
        g.fill(body);
        g.fill(new Ellipse2D.Double(32, 8, 32, 42));                                       // head
        g.fillRect(42, 44, 12, 18);                                                        // neck
        g.setColor(new Color(34, 38, 52, 120));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(body);
        g.draw(new Ellipse2D.Double(32, 8, 32, 42));
        g.setColor(new Color(215, 225, 245, 150));
        g.fillOval(41, 26, 4, 3); g.fillOval(52, 26, 4, 3);                                // faint eyes
        g.dispose();
        return new Sprite(96, 230, pixels(im));
    }

    private static Sprite flashlightSprite() {
        BufferedImage im = new BufferedImage(72, 36, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        g.setPaint(new GradientPaint(0, 8, new Color(120, 124, 134), 0, 28, new Color(34, 36, 42)));
        g.fillRoundRect(18, 10, 48, 16, 8, 8);
        g.setColor(new Color(60, 62, 70));
        g.fillRoundRect(6, 6, 18, 24, 6, 6);
        g.setColor(new Color(255, 244, 190));
        g.fillOval(3, 8, 10, 20);
        g.dispose();
        return new Sprite(72, 36, pixels(im));
    }

    private static Sprite keySprite() {
        BufferedImage im = new BufferedImage(56, 56, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 226, 120), 56, 56, new Color(176, 130, 40)));
        g.setStroke(new BasicStroke(6f));
        g.draw(new Ellipse2D.Double(8, 6, 20, 20));
        g.fillRect(24, 18, 28, 7);
        g.fillRect(38, 24, 6, 12);
        g.fillRect(47, 24, 5, 9);
        g.dispose();
        return new Sprite(56, 56, pixels(im));
    }

    private static Sprite batterySprite() {
        BufferedImage im = new BufferedImage(36, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        g.setPaint(new GradientPaint(4, 0, new Color(70, 190, 90), 32, 0, new Color(20, 100, 44)));
        g.fillRoundRect(4, 10, 28, 46, 8, 8);
        g.setColor(new Color(214, 214, 220));
        g.fillRect(12, 2, 12, 9);
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("+", 12, 36);
        g.dispose();
        return new Sprite(36, 60, pixels(im));
    }

    private static Sprite noteSprite() {
        BufferedImage im = new BufferedImage(60, 76, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        g.rotate(-0.12, 30, 38);
        g.setColor(new Color(236, 230, 208));
        g.fillRect(8, 6, 42, 60);
        g.setColor(new Color(100, 96, 90));
        for (int i = 0; i < 7; i++) g.fillRect(13, 14 + i * 7, 32 - (i % 3) * 6, 2);
        g.dispose();
        return new Sprite(60, 76, pixels(im));
    }

    private static Sprite medkitSprite() {
        BufferedImage im = new BufferedImage(68, 52, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(im);
        g.setPaint(new GradientPaint(0, 0, new Color(240, 240, 240), 0, 52, new Color(176, 176, 182)));
        g.fillRoundRect(4, 8, 60, 40, 8, 8);
        g.setColor(new Color(200, 30, 36));
        g.fillRect(28, 16, 12, 24); g.fillRect(22, 22, 24, 12);
        g.setColor(new Color(110, 110, 116));
        g.fillRoundRect(24, 2, 20, 8, 4, 4);
        g.dispose();
        return new Sprite(68, 52, pixels(im));
    }
}
