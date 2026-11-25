import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * NEW FILE - TEMPORARY placeholder drawing for the intro screens, so you can test the backend
 * before your custom UI is ready. Replace any of these methods with your own UI later.
 *
 * HOW TO HOOK IT UP: at the very END of your Renderer's draw method (after the HUD), add:
 *
 *     IntroOverlay.draw(g2, game, screenWidth, screenHeight);
 *
 * It draws nothing during normal gameplay, so it's safe to leave in.
 */
public final class IntroOverlay {
    private IntroOverlay() { }

    public static void draw(Graphics2D g, Game game, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch (game.state()) {
            case NAME_ENTRY: drawNameEntry(g, game, w, h); break;
            case BLACKOUT:   g.setColor(Color.BLACK); g.fillRect(0, 0, w, h); break;
            case DIALOGUE:   drawDialogue(g, game, w, h); break;
            default: break;
        }
    }

    /* ------------------------------------------------------------ name + gender screen */
    private static void drawNameEntry(Graphics2D g, Game game, int w, int h) {
        g.setColor(new Color(12, 12, 16));
        g.fillRect(0, 0, w, h);
        int cx = w / 2;

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        center(g, "Who are you?", cx, h / 4);

        // name box
        boolean nameSel = game.nameEntryField() == 0;
        int bw = 360, bh = 48, bx = cx - bw / 2, by = h / 2 - 70;
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(Color.GRAY);
        center(g, "Name", cx, by - 10);
        g.setColor(new Color(30, 30, 38));
        g.fillRect(bx, by, bw, bh);
        g.setColor(nameSel ? Color.WHITE : Color.DARK_GRAY);
        g.setStroke(new BasicStroke(2));
        g.drawRect(bx, by, bw, bh);
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        String text = game.nameEntryText() + (nameSel && game.nameEntryCaretVisible() ? "|" : "");
        g.setColor(Color.WHITE);
        g.drawString(text, bx + 12, by + 33);

        // gender choice
        boolean genSel = game.nameEntryField() == 1;
        int gy = h / 2 + 40;
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(Color.GRAY);
        center(g, "Gender", cx, gy - 10);
        drawOption(g, Gender.MALE.label(), cx - 90, gy, game.nameEntryGender() == Gender.MALE, genSel);
        drawOption(g, Gender.FEMALE.label(), cx + 90, gy, game.nameEntryGender() == Gender.FEMALE, genSel);

        // help + error
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(Color.GRAY);
        center(g, nameSel ? "Type your name, then press ENTER"
                          : "LEFT/RIGHT to choose  -  ENTER to start  -  UP to edit name", cx, h - 60);
        if (!game.nameEntryError().isEmpty()) {
            g.setColor(new Color(220, 80, 80));
            center(g, game.nameEntryError(), cx, by + bh + 24);
        }
    }

    private static void drawOption(Graphics2D g, String label, int cx, int y, boolean chosen, boolean rowSelected) {
        int ow = 150, oh = 44;
        g.setColor(chosen ? new Color(120, 30, 30) : new Color(30, 30, 38));
        g.fillRect(cx - ow / 2, y, ow, oh);
        g.setColor(chosen && rowSelected ? Color.WHITE : Color.DARK_GRAY);
        g.drawRect(cx - ow / 2, y, ow, oh);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        center(g, label, cx, y + 29);
    }

    /* ------------------------------------------------------------ dialogue box */
    private static void drawDialogue(Graphics2D g, Game game, int w, int h) {
        int m = 30, bh = 130, by = h - bh - m;
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRoundRect(m, by, w - 2 * m, bh, 16, 16);
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(m, by, w - 2 * m, bh, 16, 16);

        int tx = m + 22, ty = by + 34;
        if (!game.dialogueSpeaker().isEmpty()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(new Color(255, 210, 120));
            g.drawString(game.dialogueSpeaker(), tx, ty);
            ty += 30;
        }
        g.setFont(new Font("SansSerif", game.dialogueSpeaker().isEmpty() ? Font.ITALIC : Font.PLAIN, 20));
        g.setColor(Color.WHITE);
        drawWrapped(g, game.dialogueText(), tx, ty, w - 2 * m - 44);

        if (!game.dialogueTyping()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(Color.GRAY);
            g.drawString("[E] continue", w - m - 110, by + bh - 14);
        }
    }

    /* ------------------------------------------------------------ helpers */
    private static void center(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = row.length() == 0 ? word : row + " " + word;
            if (fm.stringWidth(test) > maxW && row.length() > 0) {
                g.drawString(row.toString(), x, y);
                y += fm.getHeight();
                row.setLength(0);
                row.append(word);
            } else {
                row.setLength(0);
                row.append(test);
            }
        }
        g.drawString(row.toString(), x, y);
    }
}
