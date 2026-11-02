import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.*;

/** Title / character creation screen and the loading screen. */
public class MenuScreen {
    public enum Phase { CREATE, LOADING }
    private static final String[] ROWS = {"Character", "Name", "Skin", "Hair style", "Hair color", "Outfit", "Glasses"};
    private static final String[] TIPS = {
        "Running is loud. The thing in the halls is listening.",
        "Crouch (C) or walk quietly (Ctrl) to stay unheard.",
        "Shine your flashlight at the shadow to drive it back.",
        "Notes you find are saved in your journal (J).",
        "Lockers and desk drawers hide batteries, medkits and keys.",
        "Room 404 is the only way out."};

    private final CharacterProfile profile = new CharacterProfile();
    private Phase phase = Phase.CREATE;
    private int row;
    private double t, loadTime;
    private boolean start;
    private final Font title = new Font("SansSerif", Font.BOLD, 64);
    private final Font big = new Font("SansSerif", Font.BOLD, 26);
    private final Font mid = new Font("SansSerif", Font.PLAIN, 22);
    private final Font small = new Font("SansSerif", Font.PLAIN, 16);

    public CharacterProfile profile() { return profile; }
    public Phase phase() { return phase; }
    public boolean wantsStart() { return start; }
    public void beginLoading() { phase = Phase.LOADING; start = false; loadTime = 0; }

    /* ---------------------------------------------------------- input */
    public void key(Input in, double dt) {
        t += dt;
        if (phase != Phase.CREATE) return;
        if (in.pressed(KeyEvent.VK_UP)) row = (row + ROWS.length) % (ROWS.length + 1);
        if (in.pressed(KeyEvent.VK_DOWN)) row = (row + 1) % (ROWS.length + 1);
        if (in.pressed(KeyEvent.VK_LEFT)) change(-1);
        if (in.pressed(KeyEvent.VK_RIGHT)) change(1);
        if (in.pressed(KeyEvent.VK_ENTER)) { if (row == 1) row = 2; else start = true; }
        in.clearEdges();
    }

    public void typed(char c) {
        if (phase != Phase.CREATE || row != 1) return;
        String n = profile.getRawName();
        if (c == '\b') { if (!n.isEmpty()) profile.setName(n.substring(0, n.length() - 1)); }
        else if (Character.isLetterOrDigit(c) || c == ' ') profile.setName(n + c);
    }

    private void change(int d) {
        switch (row) {
            case 0: profile.setPreset(profile.getPreset() + d); break;
            case 2: profile.setSkin(profile.getSkin() + d); break;
            case 3: profile.setStyle(profile.getStyle() + d); break;
            case 4: profile.setHair(profile.getHair() + d); break;
            case 5: profile.setShirt(profile.getShirt() + d); break;
            case 6: profile.setGlasses(!profile.hasGlasses()); break;
            default: break;
        }
    }

    public void click(int mx, int my, int w, int h) {
        if (phase != Phase.CREATE) return;
        double s = w / 1280.0;
        double x = mx / s, y = my / s;
        for (int i = 0; i <= ROWS.length; i++) {
            double ry = 190 + i * 54;
            if (y >= ry && y < ry + 46 && x >= 600 && x < 1180) {
                row = i;
                if (i == ROWS.length) start = true;
                else if (x < 800) change(-1);
                else if (x > 980) change(1);
                else if (i == 6) change(1);
            }
        }
    }

    /* ---------------------------------------------------------- drawing */
    public double progress() { return Math.min(1, loadTime / 3.2); }
    public void tickLoading(double dt) { loadTime += dt; }
    public boolean loadingDone(boolean gameReady) { return gameReady && loadTime >= 3.2; }

    public void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = w / 1280.0;
        g.setPaint(new GradientPaint(0, 0, new Color(4, 6, 12), 0, h, new Color(14, 20, 34)));
        g.fillRect(0, 0, w, h);
        // flickering ceiling light strip like the hallway
        double fl = Math.sin(t * 23) * Math.sin(t * 7.3) > -0.3 ? 1 : 0.25;
        g.setPaint(new RadialGradientPaint(w / 2f, 0, w * 0.7f, new float[]{0, 1}, new Color[]{new Color(120, 160, 220, (int) (70 * fl)), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        AffineTransform base = g.getTransform();
        g.scale(s, s);
        g.setFont(title); g.setColor(new Color(235, 235, 240));
        String ttl = "SCHOOL ESCAPE";
        g.drawString(ttl, 640 - g.getFontMetrics().stringWidth(ttl) / 2, 100);
        g.setColor(new Color(205, 30, 36)); g.fillRect(440, 114, 400, 4);

        if (phase == Phase.CREATE) create(g); else loading(g);
        g.setTransform(base);
    }

    private void create(Graphics2D g) {
        // preview card
        g.setColor(new Color(8, 10, 14, 200)); g.fillRoundRect(110, 170, 400, 440, 18, 18);
        g.setColor(new Color(210, 215, 225, 150)); g.setStroke(new BasicStroke(2f)); g.drawRoundRect(110, 170, 400, 440, 18, 18);
        AffineTransform a = g.getTransform();
        g.translate(190, 215); g.scale(3.0, 3.0);
        Hud.drawBust(g, profile, t);
        g.setTransform(a);
        g.setFont(big); g.setColor(Color.WHITE);
        String n = profile.getName();
        g.drawString(n, 310 - g.getFontMetrics().stringWidth(n) / 2, 580);

        String[] vals = {CharacterProfile.PRESET_NAMES[profile.getPreset()], profile.getRawName() + (row == 1 && ((int) (t * 2)) % 2 == 0 ? "|" : ""),
            CharacterProfile.SKIN_NAMES[profile.getSkin()], CharacterProfile.STYLE_NAMES[profile.getStyle()],
            CharacterProfile.HAIR_NAMES[profile.getHair()], CharacterProfile.SHIRT_NAMES[profile.getShirt()], profile.hasGlasses() ? "On" : "Off"};
        for (int i = 0; i < ROWS.length; i++) {
            int y = 190 + i * 54;
            boolean sel = i == row;
            g.setColor(sel ? new Color(40, 50, 70, 220) : new Color(8, 10, 14, 190)); g.fillRoundRect(600, y, 580, 46, 10, 10);
            g.setColor(sel ? new Color(255, 255, 255, 230) : new Color(210, 215, 225, 110)); g.setStroke(new BasicStroke(sel ? 2.5f : 1.2f));
            g.drawRoundRect(600, y, 580, 46, 10, 10);
            g.setFont(mid); g.setColor(new Color(225, 228, 235)); g.drawString(ROWS[i], 620, y + 31);
            g.setColor(Color.WHITE);
            String v = vals[i];
            int vw = g.getFontMetrics().stringWidth(v);
            g.drawString(v, 890 - vw / 2, y + 31);
            if (i != 1) { g.setColor(new Color(235, 90, 70)); g.drawString("<", 810, y + 31); g.drawString(">", 960, y + 31); }
            if (i >= 2 && i <= 5) {
                Color sw = i == 2 ? profile.skinColor() : i == 4 ? profile.hairColor() : i == 5 ? profile.shirtColor() : null;
                if (sw != null) { g.setColor(sw); g.fillRoundRect(1130, y + 11, 28, 24, 6, 6); g.setColor(Color.WHITE); g.drawRoundRect(1130, y + 11, 28, 24, 6, 6); }
            }
        }
        int by = 190 + ROWS.length * 54;
        boolean sel = row == ROWS.length;
        g.setPaint(new GradientPaint(0, by, sel ? new Color(225, 50, 50) : new Color(160, 28, 34), 0, by + 52, new Color(110, 14, 20)));
        g.fillRoundRect(600, by + 6, 580, 52, 12, 12);
        g.setColor(Color.WHITE); g.setFont(big);
        String st = "START GAME";
        g.drawString(st, 890 - g.getFontMetrics().stringWidth(st) / 2, by + 41);
        g.setFont(small); g.setColor(new Color(190, 195, 205));
        String hint = "Up / Down: choose     Left / Right: change     type your name     Enter: start     (or use the mouse)";
        g.drawString(hint, 640 - g.getFontMetrics().stringWidth(hint) / 2, 670);
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
}
