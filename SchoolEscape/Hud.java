import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Draws the heads-up display in the style of the reference screenshot. */
public class Hud {
    private static final Color RED = new Color(205, 30, 36);
    private static final Color PANEL = new Color(8, 10, 14, 185);
    private static final Color EDGE = new Color(210, 215, 225, 150);
    private int curW = 1280;
    private final Font f12 = new Font("SansSerif", Font.PLAIN, 14);
    private final Font b16 = new Font("SansSerif", Font.BOLD, 17);
    private final Font b22 = new Font("SansSerif", Font.BOLD, 24);
    private final Font b40 = new Font("SansSerif", Font.BOLD, 44);

    public void draw(Graphics2D g, Game game, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        curW = w;
        double s = w / 1672.0;
        Player p = game.player();

        if (game.hurtFlash() > 0) {
            g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, w * 0.65f, new float[]{0.45f, 1f},
                    new Color[]{new Color(160, 0, 0, 0), new Color(160, 0, 0, (int) (150 * game.hurtFlash()))}));
            g.fillRect(0, 0, w, h);
        }
        drawFlashlight(g, game, w, h);

        AffineTransform base = g.getTransform();
        g.scale(s, s);
        int vh = (int) (h / s);
        statusPanel(g, p, game.profile());
        objectivePanel(g, game, 1672);
        minimap(g, game, 1672);
        keyLegend(g, vh);
        hotbar(g, game, 1672, vh);
        crosshair(g, game, 1672, vh);
        messages(g, game, 1672, vh);
        g.setTransform(base);
        overlay(g, game, w, h, s);
    }

    /** Draws the 80x84 character bust used for the HUD portrait and the menus. */
    public static void drawBust(Graphics2D g, CharacterProfile c, double t) {
        g.setColor(new Color(30, 34, 44)); g.fillRoundRect(0, 0, 80, 84, 8, 8);
        Shape clip = g.getClip();
        g.clip(new RoundRectangle2D.Double(0, 0, 80, 84, 8, 8));
        Color hair = c.hairColor(), skin = c.skinColor();
        if (c.getStyle() == 1) { g.setColor(hair); g.fillRoundRect(16, 22, 48, 60, 24, 24); }          // long hair back
        g.setColor(c.shirtColor()); g.fillRoundRect(8, 62, 64, 30, 22, 22);                             // shirt
        g.setColor(skin.darker()); g.fillRect(34, 56, 12, 12);                                          // neck
        g.setColor(skin); g.fillOval(22, 24, 36, 42);                                                   // face
        g.setColor(hair);
        switch (c.getStyle()) {
            case 0: g.fillArc(20, 18, 40, 34, 0, 180); break;
            case 1: g.fillArc(20, 18, 40, 34, 0, 180); g.fillRect(20, 34, 5, 28); g.fillRect(55, 34, 5, 28); break;
            case 2: g.fillArc(20, 20, 40, 30, 0, 180);
                    g.fillPolygon(new int[]{22, 28, 34}, new int[]{34, 10, 30}, 3);
                    g.fillPolygon(new int[]{34, 40, 46}, new int[]{30, 6, 30}, 3);
                    g.fillPolygon(new int[]{46, 52, 58}, new int[]{30, 10, 34}, 3); break;
            default: g.setColor(new Color(170, 30, 36)); g.fillArc(20, 16, 40, 36, 0, 180); g.fillRect(18, 34, 46, 5);
                    g.setColor(hair); g.fillRect(22, 38, 36, 3); break;
        }
        boolean blink = ((int) (t * 1.0)) % 5 == 4 && (t * 10) % 1 < 0.5;
        g.setColor(new Color(24, 18, 18));
        if (blink) { g.fillRect(30, 45, 7, 2); g.fillRect(43, 45, 7, 2); }
        else { g.fillOval(31, 42, 6, 6); g.fillOval(43, 42, 6, 6); }
        g.setColor(new Color(150, 80, 70)); g.fillRoundRect(35, 56, 10, 3, 3, 3);                       // mouth
        if (c.hasGlasses()) {
            g.setColor(new Color(15, 15, 20)); g.setStroke(new BasicStroke(1.6f));
            g.drawOval(28, 39, 12, 12); g.drawOval(41, 39, 12, 12); g.drawLine(40, 45, 41, 45);
        }
        g.setClip(clip);
        g.setColor(new Color(220, 220, 230, 160)); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(0, 0, 80, 84, 8, 8);
    }

    /* ----------------------------------------------------------- pieces */
    private void box(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL); g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(EDGE); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(x, y, w, h, 12, 12);
    }

    private void statusPanel(Graphics2D g, Player p, CharacterProfile profile) {
        box(g, 24, 22, 340, 104);
        AffineTransform ps = g.getTransform();
        g.translate(34, 32);
        drawBust(g, profile, 0);
        g.setTransform(ps);
        g.setColor(Color.WHITE); g.setFont(b22); g.drawString(profile.getName(), 128, 56);
        g.setFont(f12); g.setColor(new Color(220, 225, 235)); g.drawString("Lv. " + p.getLevel(), 128, 76);
        g.drawString("HP: " + p.getHp() + " / " + p.getMaxHp(), 128, 98);
        bar(g, 128, 104, 224, 11, p.getHp() / (double) p.getMaxHp(), RED);
        bar(g, 128, 118, 224, 4, p.getStamina() / 100.0, new Color(80, 170, 220));
    }

    private void bar(Graphics2D g, int x, int y, int w, int h, double v, Color c) {
        g.setColor(new Color(0, 0, 0, 170)); g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(c); g.fillRoundRect(x, y, (int) (w * Math.max(0, Math.min(1, v))), h, h, h);
        g.setColor(new Color(255, 255, 255, 90)); g.drawRoundRect(x, y, w, h, h, h);
    }

    private void objectivePanel(Graphics2D g, Game game, int vw) {
        int x = vw - 24 - 340, y = 22;
        box(g, x, y, 340, 86);
        g.setFont(b16); g.setColor(new Color(235, 90, 70)); g.drawString("Objective:", x + 14, y + 26);
        g.setFont(f12); g.setColor(Color.WHITE);
        wrap(g, game.objective(), x + 14, y + 48, 312, 18);
    }

    private void wrap(Graphics2D g, String text, int x, int y, int maxW, int lh) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (fm.stringWidth(line + word) > maxW) { g.drawString(line.toString().trim(), x, y); y += lh; line.setLength(0); }
            line.append(word).append(' ');
        }
        g.drawString(line.toString().trim(), x, y);
    }

    private void minimap(Graphics2D g, Game game, int vw) {
        int size = 190, cx = vw - 24 - 170, cy = 118 + 100;
        World wd = game.world();
        Player p = game.player();
        Shape oldClip = g.getClip();
        g.setColor(PANEL); g.fillOval(cx - size / 2 - 4, cy - size / 2 - 4, size + 8, size + 8);
        g.setClip(new Ellipse2D.Double(cx - size / 2.0, cy - size / 2.0, size, size));
        AffineTransform t0 = g.getTransform();
        g.translate(cx, cy);
        g.rotate(-p.getAngle() - Math.PI / 2);
        double sc = 7;
        int r = 14;
        for (int ty = (int) p.getY() - r; ty <= p.getY() + r; ty++)
            for (int tx = (int) p.getX() - r; tx <= p.getX() + r; tx++) {
                int t = wd.tile(tx, ty);
                g.setColor(t == Tile.EMPTY ? new Color(70, 86, 108) : (t >= Tile.DOOR_BASE ? new Color(170, 120, 60) : new Color(18, 22, 30)));
                g.fill(new Rectangle2D.Double((tx - p.getX()) * sc, (ty - p.getY()) * sc, sc + 0.6, sc + 0.6));
            }
        for (GameObject o : game.objects()) {
            if (!o.visible()) continue;
            boolean pick = o instanceof Item;
            boolean st = o instanceof Stalker;
            if (!pick && !st) continue;
            if (st && ((Stalker) o).distanceTo(p.getX(), p.getY()) > 14) continue;
            g.setColor(st ? new Color(255, 40, 40) : new Color(255, 200, 80));
            g.fill(new Ellipse2D.Double((o.getX() - p.getX()) * sc - 3, (o.getY() - p.getY()) * sc - 3, 6, 6));
        }
        g.setTransform(t0);
        g.setClip(oldClip);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{cx, cx - 6, cx + 6}, new int[]{cy - 8, cy + 7, cy + 7}, 3);
        g.setColor(EDGE); g.setStroke(new BasicStroke(2f));
        g.drawOval(cx - size / 2 - 4, cy - size / 2 - 4, size + 8, size + 8);
    }

    private void keyLegend(Graphics2D g, int vh) {
        String[][] k = {{"W A S D", "Move"}, {"Mouse", "Look"}, {"F", "Interact"}, {"Shift", "Run"}, {"Tab", "Inventory"}};
        int x = 28, y = vh - 196;
        for (String[] e : k) {
            g.setFont(f12);
            int kw = Math.max(32, g.getFontMetrics().stringWidth(e[0]) + 18);
            g.setColor(new Color(10, 12, 16, 200)); g.fillRoundRect(x, y, kw, 26, 6, 6);
            g.setColor(EDGE); g.drawRoundRect(x, y, kw, 26, 6, 6);
            g.setColor(Color.WHITE); g.drawString(e[0], x + 9, y + 18);
            g.setColor(new Color(230, 230, 235)); g.drawString(e[1], x + kw + 10, y + 18);
            y += 32;
        }
    }

    private void hotbar(Graphics2D g, Game game, int vw, int vh) {
        Player p = game.player();
        int sz = 72, gap = 8, total = 7 * sz + 6 * gap;
        int x0 = (vw - total) / 2, y = vh - sz - 22;
        for (int i = 0; i < 7; i++) {
            int x = x0 + i * (sz + gap);
            boolean sel = i == p.getSelected();
            g.setColor(new Color(8, 10, 14, 200)); g.fillRoundRect(x, y, sz, sz, 10, 10);
            g.setColor(sel ? new Color(255, 255, 255, 235) : EDGE);
            g.setStroke(new BasicStroke(sel ? 3f : 1.5f));
            g.drawRoundRect(x, y, sz, sz, 10, 10);
            g.setFont(f12); g.setColor(new Color(255, 255, 255, 170)); g.drawString("" + (i + 1), x + 6, y + 16);
            Item it = p.getInventory().get(i);
            if (it != null) {
                Sprite sp = it.sprite();
                if (it instanceof FlashlightItem) icon(g, x, y, sz, sp, FlashlightItem.class.cast(it));
                else icon(g, x, y, sz, sp, null);
            }
        }
        Item cur = p.getSelectedItem();
        if (cur != null) {
            g.setFont(f12); g.setColor(Color.WHITE);
            String n = cur.getName();
            if (cur instanceof FlashlightItem) n += "  " + (int) ((FlashlightItem) cur).getCharge() + "%" + (((FlashlightItem) cur).isOn() ? "  ON" : "  OFF");
            int sw = g.getFontMetrics().stringWidth(n);
            g.drawString(n, vw / 2 - sw / 2, y - 8);
        }
    }

    private void icon(Graphics2D g, int x, int y, int sz, Sprite sp, FlashlightItem f) {
        if (sp == null) return;
        BufferedImage im = new BufferedImage(sp.w, sp.h, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, sp.w, sp.h, sp.px, 0, sp.w);
        double sc = (sz - 18.0) / Math.max(sp.w, sp.h);
        int dw = (int) (sp.w * sc), dh = (int) (sp.h * sc);
        g.drawImage(im, x + (sz - dw) / 2, y + (sz - dh) / 2 + 3, dw, dh, null);
    }

    private void crosshair(Graphics2D g, Game game, int vw, int vh) {
        int cx = vw / 2, cy = vh / 2;
        g.setColor(new Color(255, 255, 255, 220)); g.setStroke(new BasicStroke(2f));
        g.drawLine(cx - 8, cy, cx + 8, cy); g.drawLine(cx, cy - 8, cx, cy + 8);
        Interactable t = game.target();
        if (t != null) {
            g.setFont(b16);
            String txt = "[F]  " + t.prompt();
            int sw = g.getFontMetrics().stringWidth(txt);
            g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect(cx - sw / 2 - 12, cy + 30, sw + 24, 30, 8, 8);
            g.setColor(Color.WHITE); g.drawString(txt, cx - sw / 2, cy + 51);
        }
    }

    private void messages(Graphics2D g, Game game, int vw, int vh) {
        String l = game.line();
        if (!l.isEmpty()) {
            g.setFont(b16);
            int sw = g.getFontMetrics().stringWidth(l);
            g.setColor(new Color(0, 0, 0, 160)); g.fillRoundRect(vw / 2 - sw / 2 - 16, vh - 200, sw + 32, 34, 10, 10);
            g.setColor(new Color(240, 240, 245)); g.drawString(l, vw / 2 - sw / 2, vh - 177);
        }
        String t = game.toastText();
        if (!t.isEmpty()) {
            g.setFont(b22);
            int sw = g.getFontMetrics().stringWidth(t);
            g.setColor(new Color(255, 220, 120, (int) (255 * game.toastAlpha())));
            g.drawString(t, vw / 2 - sw / 2, 150);
        }
    }

    /** The first-person hand and flashlight in the bottom-right corner. */
    private void drawFlashlight(Graphics2D g, Game game, int w, int h) {
        Player p = game.player();
        if (!(p.getSelectedItem() instanceof FlashlightItem)) return;
        double bob = p.isMoving() ? Math.sin(game.time() * (p.isSprinting() ? 13 : 8)) * 7 : Math.sin(game.time() * 1.6) * 2;
        double bx = w * 0.80, by = h * 0.98 + bob + Math.abs(bob) * 0.4;
        AffineTransform t0 = g.getTransform();
        g.translate(bx, by);
        g.rotate(-0.26);
        double u = h / 720.0;
        g.scale(u, u);
        // forearm
        g.setPaint(new GradientPaint(0, 0, new Color(55, 40, 34), 120, 0, new Color(30, 22, 20)));
        g.fill(new RoundRectangle2D.Double(-70, 40, 190, 260, 60, 60));
        g.setColor(game.profile().shirtColor().darker()); g.fill(new RoundRectangle2D.Double(-78, 150, 205, 190, 40, 40)); // sleeve
        // body of the flashlight
        g.setPaint(new GradientPaint(-40, 0, new Color(10, 10, 12), 40, 0, new Color(58, 58, 64)));
        g.fill(new RoundRectangle2D.Double(-34, -170, 72, 260, 24, 24));
        g.setPaint(new GradientPaint(-48, 0, new Color(18, 18, 20), 52, 0, new Color(80, 80, 88)));
        g.fill(new RoundRectangle2D.Double(-50, -214, 104, 62, 18, 18));                             // head
        boolean on = game.flashlightOn();
        g.setColor(on ? new Color(255, 250, 215) : new Color(60, 62, 68));
        g.fill(new Ellipse2D.Double(-42, -222, 88, 22));
        g.setColor(new Color(255, 255, 255, 28)); g.fill(new RoundRectangle2D.Double(-26, -150, 14, 220, 8, 8));
        // fingers
        g.setPaint(new GradientPaint(0, 0, game.profile().skinColor(), 0, 90, game.profile().skinColor().darker().darker()));
        for (int i = 0; i < 4; i++) g.fill(new RoundRectangle2D.Double(-46 + i * 2, -90 + i * 36, 100, 32, 28, 28));
        g.fill(new RoundRectangle2D.Double(-70, 10, 50, 100, 30, 30));                              // thumb
        g.setTransform(t0);
    }

    /* ----------------------------------------------------------- screens */
    private void overlay(Graphics2D g, Game game, int w, int h, double s) {
        Game.State st = game.state();
        if (st == Game.State.PLAYING) return;
        g.setColor(new Color(0, 0, 0, st == Game.State.DEAD ? 190 : 150)); g.fillRect(0, 0, w, h);
        g.setFont(b40.deriveFont((float) (44 * s)));
        FontMetrics fm = g.getFontMetrics();
        Player p = game.player();
        switch (st) {
            case PAUSED: center(g, "PAUSED", h / 3, new Color(240, 240, 245));
                small(g, "Esc - resume     T flashlight     Tab inventory     J journal", h / 3 + (int) (50 * s), s); break;
            case DEAD: center(g, "YOU DIED", h / 3, new Color(210, 40, 40));
                small(g, "Press Enter to try again", h / 3 + (int) (50 * s), s); break;
            case WON: center(g, "YOU ESCAPED!", h / 3, new Color(255, 225, 120));
                small(g, game.profile().getName() + " made it out of the school. Press Enter to play again.", h / 3 + (int) (50 * s), s); break;
            case INVENTORY: {
                center(g, "INVENTORY", (int) (h * 0.22), Color.WHITE);
                int y = (int) (h * 0.30);
                g.setFont(f12.deriveFont((float) (20 * s)));
                for (int i = 0; i < 7; i++) {
                    Item it = p.getInventory().get(i);
                    String txt = (i + 1) + ".  " + (it == null ? "-" : it.getName());
                    g.setColor(i == game.menuSel() ? new Color(255, 230, 140) : new Color(220, 220, 225));
                    g.drawString((i == game.menuSel() ? "> " : "  ") + txt, (int) (w * 0.36), y);
                    y += (int) (34 * s);
                }
                Item sel = p.getInventory().get(game.menuSel());
                if (sel != null) { g.setColor(new Color(200, 205, 215)); g.drawString(sel.getDescription(), (int) (w * 0.36), y + (int) (20 * s)); }
                small(g, "A / D choose     Enter use     Tab close", (int) (h * 0.88), s);
                break;
            }
            case JOURNAL: {
                if (game.shownNote() != null) {
                    center(g, "NOTE " + game.shownNote().getNumber(), (int) (h * 0.28), new Color(255, 235, 170));
                    g.setFont(f12.deriveFont((float) (21 * s)));
                    g.setColor(Color.WHITE);
                    wrapPx(g, game.shownNote().getText(), (int) (w * 0.25), (int) (h * 0.38), (int) (w * 0.5), (int) (30 * s));
                } else {
                    center(g, "JOURNAL", (int) (h * 0.2), Color.WHITE);
                    g.setFont(f12.deriveFont((float) (18 * s)));
                    g.setColor(new Color(235, 235, 240));
                    int[] y = {(int) (h * 0.28)};
                    MyTree<Integer, NoteItem> tree = p.getJournal();
                    if (tree.size() == 0) g.drawString("No notes found yet.", (int) (w * 0.3), y[0]);
                    for (NoteItem n : tree.inOrder()) {
                        y[0] = wrapPx(g, n.getNumber() + ".  " + n.getText(), (int) (w * 0.2), y[0], (int) (w * 0.6), (int) (26 * s)) + (int) (14 * s);
                    }
                }
                small(g, "Press J, F or Esc to close", (int) (h * 0.92), s);
                break;
            }
            default: break;
        }
    }

    private int wrapPx(Graphics2D g, String text, int x, int y, int maxW, int lh) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (fm.stringWidth(line + word) > maxW) { g.drawString(line.toString().trim(), x, y); y += lh; line.setLength(0); }
            line.append(word).append(' ');
        }
        g.drawString(line.toString().trim(), x, y);
        return y + lh;
    }

    private void center(Graphics2D g, String s, int y, Color c) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(c);
        g.drawString(s, curW / 2 - fm.stringWidth(s) / 2, y);
    }

    private void small(Graphics2D g, String s, int y, double sc) {
        g.setFont(f12.deriveFont((float) (18 * sc)));
        center(g, s, y, new Color(210, 214, 222));
        g.setFont(b40.deriveFont((float) (44 * sc)));
    }
}
