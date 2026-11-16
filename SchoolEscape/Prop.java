/**
 * Furniture, decorative props, locked containers, and doors.
 * Similar object types are merged into one generalized Prop class to reduce clutter.
 */
public class Prop extends GameObject implements Interactable {
    public enum Kind { PLANT, LOCKER, DRAWER, CHAINED_EXIT }

    private final Kind kind;
    private final Item loot;
    private final String emptyMessage;
    private final String promptText;
    private final String sound;
    private boolean used;

    public static Prop plant(double x, double y) {
        return new Prop(Kind.PLANT, x, y, null, null, null, null);
    }

    public static Prop locker(double x, double y, Item loot) {
        return new Prop(Kind.LOCKER, x, y, loot,
                "Dirk: Just old gym clothes and a rotten apple.", "Search locker", "locker");
    }

    public static Prop drawer(double x, double y, Item loot) {
        return new Prop(Kind.DRAWER, x, y, loot,
                "Dirk: Nothing useful in here.", "Search desk drawer", "drawer");
    }

    public static Prop chainedExit(double x, double y) {
        return new Prop(Kind.CHAINED_EXIT, x, y, null,
                "Dirk: Chained shut from the outside. The only way out is Room 404.", "Try the exit", "locked");
    }

    protected Prop(Kind kind, double x, double y, Item loot, String emptyMessage, String promptText, String sound) {
        super(x, y);
        this.kind = kind;
        this.loot = loot;
        this.emptyMessage = emptyMessage;
        this.promptText = promptText;
        this.sound = sound;
    }

    public boolean isUsed() { return used; }

    @Override
    public final void interact(Game g) {
        if (kind == Kind.PLANT) return;
        if (kind == Kind.CHAINED_EXIT) {
            g.sfx("locked");
            g.say(emptyMessage);
            return;
        }
        used = true;
        g.sfx(sound);
        if (loot == null) { g.say(emptyMessage); return; }
        loot.setPosition(getX(), getY());
        loot.interact(g);
    }

    @Override public boolean canInteract(Game g) { return !used && kind != Kind.PLANT; }
    @Override public String prompt() { return promptText == null ? "Use" : promptText; }
    @Override public void update(Game g, double dt) { }
    @Override public Sprite sprite() { return kind == Kind.PLANT ? Textures.plant : null; }
    @Override public double height() { return kind == Kind.PLANT ? 0.95 : 0.0; }
    @Override public double solidRadius() { return kind == Kind.PLANT ? 0.22 : 0.0; }
}

/** A classroom or office door that is part of the wall until it is opened. */
class Door extends GameObject implements Interactable {
    private static final double SWING_SPEED = 2.8;
    private final int tx, ty, tileId;
    private final String label, keyId, lockedMessage;
    private final Sprite sprite;
    private boolean open;
    private boolean locked;
    private double swing;

    public Door(int tx, int ty, int tileId, String label, String keyId, String lockedMessage) {
        super(tx + 0.5, ty + 0.5);
        this.tx = tx; this.ty = ty; this.tileId = tileId;
        this.label = label; this.keyId = keyId; this.lockedMessage = lockedMessage;
        this.locked = keyId != null;
        this.sprite = makeDoorSprite(label);
    }

    private static Sprite makeDoorSprite(String text) {
        int w = 128, h = 224;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 100 + (x / 8) % 10, g = 70 + (y / 10) % 7, b = 55;
                px[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                if (x < 6 || x > w - 7 || y < 6 || y > h - 7) px[y * w + x] = 0xFF303128;
            }
        }
        for (int x = 10; x < w - 10; x++) {
            int y0 = 24, y1 = h - 24;
            int v = x < w / 2 ? 85 : 80;
            for (int y = y0; y <= y1; y++) if ((y - y0) % 18 == 0) px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        for (int y = 0; y < h; y++) {
            int edge = 0xFF1c1c1a;
            px[y * w + 0] = edge; px[y * w + w - 1] = edge;
        }
        return new Sprite(w, h, px);
    }

    public int getTileId() { return tileId; }
    public String getLabel() { return label; }
    public boolean isLocked() { return locked; }
    public boolean isOpen() { return open; }
    public double getSwing() { return swing; }

    @Override public String prompt() {
        if (open) return "Close door";
        return locked ? "Locked door (" + label + ")" : "Open door (" + label + ")";
    }

    @Override public boolean canInteract(Game g) { return true; }
    @Override public void update(Game g, double dt) {
        if (open && swing < 1.2) swing = Math.min(1.2, swing + dt * SWING_SPEED);
        else if (!open && swing > 0.0) swing = Math.max(0.0, swing - dt * SWING_SPEED);
    }

    @Override public Sprite sprite() { return open || swing > 0.02 ? sprite : null; }
    @Override public boolean visible() { return true; }
    @Override public double height() { return 1.75; }
    @Override public double lift() { return 0; }

    @Override
    public void interact(Game g) {
        if (open) {
            if (g.player().distanceTo(getX(), getY()) > 1.1) {
                open = false;
                g.world().setTile(tx, ty, tileId);
                g.sfx("creak");
            }
            return;
        }
        if (locked) {
            if (g.player().getInventory().hasKey(keyId)) {
                locked = false;
                g.sfx("unlock");
                g.say("Dirk: The key fits.");
                swing(g);
                onUnlocked(g);
            } else {
                g.sfx("locked");
                g.say(lockedMessage);
            }
            return;
        }
        swing(g);
    }

    private void swing(Game g) {
        open = true;
        g.world().setTile(tx, ty, Tile.EMPTY);
        g.sfx("creak");
    }

    protected void onUnlocked(Game g) { }
}

/** Room 404: opening it with the right key ends the game. */
class EscapeDoor extends Door {
    public EscapeDoor(int tx, int ty, int tileId, String keyId) {
        super(tx, ty, tileId, "404", keyId, "Dirk: Room 404 is locked. There must be a key somewhere in this school.");
    }

    @Override protected void onUnlocked(Game g) { g.win(); }
}
