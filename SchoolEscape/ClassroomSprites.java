import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Draws the classroom pictures (desk, chair, students) with code, so no image files are needed.
 * Every sprite is drawn on a transparent canvas; the BOTTOM of the canvas is the floor.
 * Want your own art instead? Make a Sprite from a PNG and pass it to Student / Furniture.
 */
public final class ClassroomSprites {
    private ClassroomSprites() {}

    /** How a student looks. Edit the Look(...) lines in IntroSequence to change the classmates. */
    public static final class Look {
        public final Color skin, hair, shirt, pants;
        public final int style;                // 0 short hair, 1 long hair, 2 ponytail, 3 cap
        public final boolean glasses, tired;

        public Look(Color skin, Color hair, Color shirt, Color pants, int style, boolean glasses, boolean tired) {
            this.skin = skin; this.hair = hair; this.shirt = shirt; this.pants = pants;
            this.style = style; this.glasses = glasses; this.tired = tired;
        }
    }

    /* ------------------------------------------------------------ helpers */
    private static BufferedImage canvas(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }

    private static Graphics2D gfx(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static Sprite toSprite(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] px = new int[w * h];
        im.getRGB(0, 0, w, h, px, 0, w);
        return new Sprite(w, h, px);
    }

    /** Dust and gloom: darker and a little bluer. Used for the empty, creepy classroom. */
    private static BufferedImage dusty(BufferedImage im) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) {
                int c = im.getRGB(x, y), a = c >>> 24;
                if (a == 0) continue;
                int r = (int) (((c >> 16) & 255) * 0.62), g = (int) (((c >> 8) & 255) * 0.64), b = (int) ((c & 255) * 0.72);
                im.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        return im;
    }

    /** Brightness multiplier for the bright classroom. The renderer's lights are strong, so colours are drawn a bit darker. */
    private static final double BRIGHT_TONE = 0.66;

    private static BufferedImage tone(BufferedImage im, double f) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) {
                int c = im.getRGB(x, y), a = c >>> 24;
                if (a == 0) continue;
                im.setRGB(x, y, (a << 24) | ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f));
            }
        return im;
    }

    /* ------------------------------------------------------------ furniture */
    private static BufferedImage deskImage() {
        BufferedImage im = canvas(132, 96);
        Graphics2D g = gfx(im);
        Color metal = new Color(70, 74, 82), metalDark = new Color(44, 47, 54);
        g.setColor(metalDark);                                    // back legs
        g.fillRect(32, 26, 5, 62); g.fillRect(95, 26, 5, 62);
        g.setColor(new Color(58, 44, 36));                        // shelf under the top
        g.fillRect(16, 42, 100, 10);
        g.setColor(new Color(40, 30, 26));
        g.fillRect(16, 50, 100, 3);
        g.setColor(metal);                                        // front legs
        g.fillRect(10, 26, 6, 68); g.fillRect(116, 26, 6, 68);
        g.setColor(new Color(30, 31, 35));
        g.fillRect(8, 92, 10, 4); g.fillRect(114, 92, 10, 4);     // feet
        g.setPaint(new GradientPaint(0, 4, new Color(206, 160, 100), 0, 28, new Color(150, 106, 62)));   // table top
        g.fill(new RoundRectangle2D.Double(2, 10, 128, 18, 6, 6));
        g.setColor(new Color(226, 184, 124));                     // lit top surface
        g.fillPolygon(new int[]{12, 120, 130, 2}, new int[]{4, 4, 12, 12}, 4);
        g.setColor(new Color(96, 66, 38));
        g.drawLine(4, 27, 128, 27);
        g.dispose();
        return im;
    }

    public static Sprite desk(boolean dusty) { BufferedImage im = deskImage(); return toSprite(dusty ? dusty(im) : tone(im, BRIGHT_TONE)); }

    private static BufferedImage chairImage() {
        BufferedImage im = canvas(60, 100);
        Graphics2D g = gfx(im);
        Color metal = new Color(66, 70, 78);
        g.setColor(metal);                                        // legs
        g.fillRect(8, 60, 4, 38); g.fillRect(48, 60, 4, 38);
        g.setColor(new Color(44, 47, 54));
        g.fillRect(18, 60, 4, 34); g.fillRect(38, 60, 4, 34);
        g.setPaint(new GradientPaint(0, 52, new Color(70, 120, 190), 0, 62, new Color(40, 78, 140)));   // seat
        g.fill(new RoundRectangle2D.Double(4, 52, 52, 10, 5, 5));
        for (int i = 0; i < 3; i++) {                             // back rest slats
            g.setPaint(new GradientPaint(0, 8 + i * 13, new Color(78, 130, 200), 0, 18 + i * 13, new Color(44, 84, 150)));
            g.fill(new RoundRectangle2D.Double(7, 8 + i * 13, 46, 10, 4, 4));
        }
        g.setColor(metal);
        g.fillRect(9, 8, 3, 46); g.fillRect(48, 8, 3, 46);
        g.dispose();
        return im;
    }

    public static Sprite chair(boolean dusty) { BufferedImage im = chairImage(); return toSprite(dusty ? dusty(im) : tone(im, BRIGHT_TONE)); }

    /** A chair that has fallen over (lying on its side). Always dusty. */
    public static Sprite chairFallen() {
        BufferedImage src = dusty(chairImage());
        BufferedImage im = canvas(104, 62);
        Graphics2D g = gfx(im);
        AffineTransform t = new AffineTransform();
        t.translate(4, 58);
        t.rotate(-Math.PI / 2 + 0.12);
        t.translate(-60, -50);
        g.drawImage(src, t, null);
        g.dispose();
        return toSprite(im);
    }

    /** A desk knocked a little crooked. Always dusty. */
    public static Sprite deskTipped() {
        BufferedImage src = dusty(deskImage());
        BufferedImage im = canvas(150, 112);
        Graphics2D g = gfx(im);
        AffineTransform t = new AffineTransform();
        t.translate(8, 104);
        t.rotate(-0.2);
        t.translate(0, -96);
        g.drawImage(src, t, null);
        g.dispose();
        return toSprite(im);
    }

    /* ------------------------------------------------------------ students */
    public static Sprite student(Look look) {
        int W = 80, H = 200;
        BufferedImage im = canvas(W, H);
        Graphics2D g = gfx(im);
        Color skinDark = shade(look.skin, 0.82), pantsDark = shade(look.pants, 0.7), shirtDark = shade(look.shirt, 0.75);
        int headDrop = look.tired ? 3 : 0;                         // tired = head hangs a little lower

        if (look.style == 1) {                                     // long hair hangs behind the shoulders
            g.setColor(look.hair);
            g.fill(new RoundRectangle2D.Double(21, 12 + headDrop, 38, 80, 22, 22));
        }
        g.setPaint(new GradientPaint(0, 0, look.pants, 80, 0, pantsDark));                            // legs
        g.fill(new RoundRectangle2D.Double(23, 112, 16, 76, 6, 6));
        g.fill(new RoundRectangle2D.Double(41, 112, 16, 76, 6, 6));
        g.setColor(new Color(26, 26, 32));                                                            // shoes
        g.fill(new Ellipse2D.Double(20, 184, 22, 13)); g.fill(new Ellipse2D.Double(38, 184, 22, 13));

        g.setColor(look.skin);                                                                        // arms
        g.fill(new RoundRectangle2D.Double(9, 86, 10, 38, 6, 6)); g.fill(new RoundRectangle2D.Double(61, 86, 10, 38, 6, 6));
        g.setPaint(new GradientPaint(0, 0, look.shirt, 80, 0, shirtDark));                            // shirt
        g.fill(new RoundRectangle2D.Double(18, 56, 44, 66, 14, 14));
        g.fill(new RoundRectangle2D.Double(7, 58, 14, 36, 8, 8)); g.fill(new RoundRectangle2D.Double(59, 58, 14, 36, 8, 8));
        g.setColor(shade(look.shirt, 0.6));
        g.drawLine(40, 62, 40, 120);

        g.setColor(skinDark);                                                                         // neck
        g.fillRect(35, 44 + headDrop, 10, 16);
        g.setPaint(new GradientPaint(22, 0, look.skin, 58, 0, skinDark));                             // head
        g.fill(new Ellipse2D.Double(24, 8 + headDrop, 32, 42));

        g.setColor(look.hair);                                                                        // hair on top
        switch (look.style) {
            case 3 -> {                                                                               // cap
                g.fill(new Arc2D.Double(22, 4 + headDrop, 36, 34, 0, 180, Arc2D.CHORD));
                g.fill(new RoundRectangle2D.Double(36, 26 + headDrop, 26, 5, 4, 4));
            }
            case 2 -> {                                                                               // ponytail
                g.fill(new Arc2D.Double(22, 4 + headDrop, 36, 36, 0, 180, Arc2D.CHORD));
                g.fill(new Ellipse2D.Double(53, 16 + headDrop, 12, 36));
            }
            default -> g.fill(new Arc2D.Double(22, 4 + headDrop, 36, 38, 0, 180, Arc2D.CHORD));
        }

        int ey = 28 + headDrop;                                                                       // face
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(31, ey, 7, 6)); g.fill(new Ellipse2D.Double(43, ey, 7, 6));
        g.setColor(new Color(28, 22, 22));
        g.fill(new Ellipse2D.Double(33, ey + 1, 4, 4)); g.fill(new Ellipse2D.Double(45, ey + 1, 4, 4));
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine(30, ey - 4, 38, ey - 4 + (look.tired ? 2 : 0)); g.drawLine(43, ey - 4 + (look.tired ? 2 : 0), 51, ey - 4);
        if (look.tired) {                                                                             // eye bags + flat mouth
            g.setColor(new Color(120, 80, 100, 170));
            g.draw(new Arc2D.Double(31, ey + 4, 7, 5, 200, 140, Arc2D.OPEN)); g.draw(new Arc2D.Double(43, ey + 4, 7, 5, 200, 140, Arc2D.OPEN));
            g.setColor(new Color(120, 60, 60));
            g.drawLine(36, 42 + headDrop, 45, 43 + headDrop);
        } else {
            g.setColor(new Color(150, 60, 60));
            g.draw(new Arc2D.Double(35, 36 + headDrop, 11, 8, 200, 140, Arc2D.OPEN));
        }
        if (look.glasses) {
            g.setColor(new Color(30, 30, 36));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Ellipse2D.Double(29, ey - 2, 11, 10)); g.draw(new Ellipse2D.Double(41, ey - 2, 11, 10));
            g.drawLine(40, ey + 3, 41, ey + 3);
        }
        g.dispose();
        return toSprite(tone(im, 0.78));
    }

    /**
     * The blackboard (a wide picture hung on the wall). chalk = the text written on it.
     * haunted = true gives a smudged, dusty board.
     */
    public static Sprite blackboard(String chalk, boolean haunted) {
        int W = 384, H = 140;
        BufferedImage im = canvas(W, H);
        Graphics2D g = gfx(im);
        g.setColor(new Color(92, 62, 36));                                   // wooden frame
        g.fillRoundRect(0, 0, W, H, 10, 10);
        g.setPaint(new GradientPaint(0, 8, new Color(30, 46, 40), 0, H - 20, new Color(18, 30, 26)));   // board
        g.fillRect(10, 8, W - 20, H - 26);
        g.setColor(new Color(150, 110, 70));                                 // chalk tray
        g.fillRect(6, H - 12, W - 12, 8);
        g.setColor(new Color(230, 230, 225));
        g.fillRect(40, H - 15, 22, 4); g.fillRect(70, H - 15, 14, 4);        // chalk sticks
        if (haunted) {                                                       // smudges, as if wiped in a hurry
            g.setColor(new Color(120, 130, 125, 70));
            for (int i = 0; i < 7; i++) g.fillOval(24 + i * 48, 22 + (i * 37) % 60, 70, 26);
        }
        g.setFont(new Font("SansSerif", Font.ITALIC | Font.BOLD, 44));
        g.setColor(haunted ? new Color(210, 210, 205, 190) : new Color(235, 235, 230, 230));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(chalk, (W - fm.stringWidth(chalk)) / 2, 78);
        g.setStroke(new BasicStroke(2.2f));
        g.drawLine((W - fm.stringWidth(chalk)) / 2, 90, (W + fm.stringWidth(chalk)) / 2 - 20, 92);
        g.dispose();
        return toSprite(haunted ? dusty(im) : tone(im, 0.8));
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }
}