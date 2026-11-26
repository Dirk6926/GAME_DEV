import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws everything the intro adds on top of the game: the dialogue box, the wake-up caption and the
 * black screen. Hud.draw() calls DialogueBox.draw(...) as its LAST line so it sits above everything.
 */
public final class DialogueBox {
    private DialogueBox() {}

    /* ======================= LOOK (edit me) ======================= */
    private static final Color PANEL = new Color(8, 8, 14, 220);
    private static final Color PANEL_EDGE = new Color(210, 220, 235, 120);
    private static final Color PLAYER_TAG = new Color(120, 190, 255);    // name tag colour when the PLAYER talks
    private static final Color OTHER_TAG = new Color(255, 205, 120);     // name tag colour for everybody else
    private static final Color TEXT = new Color(240, 240, 245);
    private static final Font NAME_FONT = new Font("SansSerif", Font.BOLD, 22);
    private static final Font TEXT_FONT = new Font("SansSerif", Font.PLAIN, 26);
    private static final Font HINT_FONT = new Font("SansSerif", Font.PLAIN, 15);
    private static final Font CAPTION_FONT = new Font("SansSerif", Font.ITALIC, 28);

    public static void draw(Graphics2D g, Game game, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = w / 1280.0;

        Dialogue d = game.dialogue();
        if (d != null && !d.isFinished()) drawDialogue(g, d, w, h, s);
        drawCaption(g, game.intro(), w, h, s);

        // EFFECT: BLACKOUT - a black rectangle over everything. blackAlpha() goes 0 -> 1 -> 0 (see IntroSequence).
        float black = (float) game.intro().blackAlpha();
        if (black > 0.001f) {
            g.setColor(new Color(0, 0, 0, Math.min(255, (int) (black * 255))));
            g.fillRect(0, 0, w, h);
        }
    }

    /** The conversation box at the bottom of the screen. */
    private static void drawDialogue(Graphics2D g, Dialogue d, int w, int h, double s) {
        DialogueLine line = d.current();
        int bx = (int) (140 * s), bw = (int) (1000 * s), bh = (int) (170 * s), by = h - bh - (int) (50 * s);

        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 22 * s, 22 * s));
        g.setColor(PANEL_EDGE);
        g.setStroke(new BasicStroke((float) (2 * s)));
        g.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 22 * s, 22 * s));

        // name tag
        g.setFont(NAME_FONT.deriveFont((float) (22 * s)));
        FontMetrics nf = g.getFontMetrics();
        String name = line.getSpeaker();
        int tagW = nf.stringWidth(name) + (int) (36 * s), tagH = (int) (36 * s);
        g.setColor(line.isFromPlayer() ? PLAYER_TAG : OTHER_TAG);
        g.fill(new RoundRectangle2D.Double(bx + 24 * s, by - tagH / 2.0, tagW, tagH, 14 * s, 14 * s));
        g.setColor(new Color(12, 12, 18));
        g.drawString(name, (int) (bx + 24 * s + 18 * s), by + nf.getAscent() / 2 - (int) (3 * s));

        // text (wrapped, typewriter)
        g.setFont(TEXT_FONT.deriveFont((float) (26 * s)));
        FontMetrics tf = g.getFontMetrics();
        g.setColor(TEXT);
        int tx = bx + (int) (40 * s), ty = by + (int) (62 * s), maxW = bw - (int) (80 * s);
        String full = line.getText(), shown = d.visibleText();
        int drawn = 0;
        for (String row : wrap(full, tf, maxW)) {
            if (drawn >= shown.length()) break;
            int end = Math.min(row.length(), shown.length() - drawn);
            g.drawString(row.substring(0, end), tx, ty);
            drawn += row.length() + 1;                       // +1 for the space that wrapping removed
            ty += tf.getHeight();
        }

        // "press to continue" hint (blinks once the line is complete)
        if (d.isLineComplete() && (System.currentTimeMillis() / 450) % 2 == 0) {
            g.setFont(HINT_FONT.deriveFont((float) (15 * s)));
            String hint = "F / E / Click  ▶";
            g.setColor(new Color(200, 205, 215));
            g.drawString(hint, bx + bw - g.getFontMetrics().stringWidth(hint) - (int) (28 * s), by + bh - (int) (18 * s));
        }
    }

    /** The italic story line that fades in and out after waking up. */
    private static void drawCaption(Graphics2D g, IntroSequence intro, int w, int h, double s) {
        float a = (float) intro.captionAlpha();
        String text = intro.caption();
        if (a <= 0.01f || text.isEmpty()) return;
        g.setFont(CAPTION_FONT.deriveFont((float) (28 * s)));
        FontMetrics fm = g.getFontMetrics();
        int maxW = (int) (900 * s), y = (int) (h * 0.30);
        for (String row : wrap(text, fm, maxW)) {
            int x = w / 2 - fm.stringWidth(row) / 2;
            g.setColor(new Color(0, 0, 0, (int) (160 * a)));
            g.drawString(row, x + (int) (2 * s), y + (int) (2 * s));
            g.setColor(new Color(235, 235, 240, (int) (255 * a)));
            g.drawString(row, x, y);
            y += fm.getHeight();
        }
    }

    private static java.util.List<String> wrap(String text, FontMetrics fm, int maxW) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = row.length() == 0 ? word : row + " " + word;
            if (fm.stringWidth(test) > maxW && row.length() > 0) { rows.add(row.toString()); row = new StringBuilder(word); }
            else row = new StringBuilder(test);
        }
        if (row.length() > 0) rows.add(row.toString());
        return rows;
    }
}
