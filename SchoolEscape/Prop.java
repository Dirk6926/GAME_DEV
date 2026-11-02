/**
 * Furniture and doors. ABSTRACT: each kind of prop decides what happens in onInteract().
 */
public abstract class Prop extends GameObject implements Interactable {
    private boolean used;

    protected Prop(double x, double y) { super(x, y); }

    protected abstract void onInteract(Game g);

    @Override
    public final void interact(Game g) {
        used = true;
        onInteract(g);
    }

    public boolean isUsed() { return used; }
    @Override public boolean canInteract(Game g) { return true; }
    @Override public String prompt() { return "Use"; }
    @Override public void update(Game g, double dt) { }
}

/** A potted plant: decoration that blocks the way. */
class Plant extends Prop {
    public Plant(double x, double y) { super(x, y); }
    @Override protected void onInteract(Game g) { }
    @Override public boolean canInteract(Game g) { return false; }
    @Override public Sprite sprite() { return Textures.plant; }
    @Override public double height() { return 0.95; }
    @Override public double solidRadius() { return 0.22; }
}

/** Something the player can search (INHERITANCE: lockers and drawers share this behaviour). */
abstract class Searchable extends Prop {
    private final Item loot;
    private final String emptyMessage;

    protected Searchable(double x, double y, Item loot, String emptyMessage) {
        super(x, y);
        this.loot = loot;
        this.emptyMessage = emptyMessage;
    }

    protected abstract String verb();
    protected abstract String sound();

    @Override public String prompt() { return verb(); }
    @Override public boolean canInteract(Game g) { return !isUsed(); }

    @Override
    protected void onInteract(Game g) {
        g.sfx(sound());
        if (loot == null) { g.say(emptyMessage); return; }
        loot.setPosition(getX(), getY());
        loot.interact(g);
    }
}

class LockerSpot extends Searchable {
    public LockerSpot(double x, double y, Item loot) { super(x, y, loot, "Dirk: Just old gym clothes and a rotten apple."); }
    @Override protected String verb() { return "Search locker"; }
    @Override protected String sound() { return "locker"; }
}

class DeskDrawer extends Searchable {
    private final double lx, ly;
    public DeskDrawer(double x, double y, Item loot) { super(x, y, loot, "Dirk: Nothing useful in here."); lx = x; ly = y; }
    @Override protected String verb() { return "Search desk drawer"; }
    @Override protected String sound() { return "drawer"; }
    @Override public Sprite sprite() { return null; }
}

/** The chained emergency exits: they never open. */
class ChainedExit extends Prop {
    public ChainedExit(double x, double y) { super(x, y); }
    @Override public String prompt() { return "Try the exit"; }
    @Override protected void onInteract(Game g) {
        g.sfx("locked");
        g.say("Dirk: Chained shut from the outside. The only way out is Room 404.");
    }
}

/** A classroom or office door that is part of the wall until it is opened. */
class Door extends Prop {
    private final int tx, ty, tileId;
    private final String label, keyId, lockedMessage;
    private boolean open;
    private boolean locked;

    public Door(int tx, int ty, int tileId, String label, String keyId, String lockedMessage) {
        super(tx + 0.5, ty + 0.5);
        this.tx = tx; this.ty = ty; this.tileId = tileId;
        this.label = label; this.keyId = keyId; this.lockedMessage = lockedMessage;
        this.locked = keyId != null;
    }

    public int getTileId() { return tileId; }
    public String getLabel() { return label; }
    public boolean isLocked() { return locked; }
    public boolean isOpen() { return open; }

    @Override
    public String prompt() {
        if (open) return "Close door";
        return locked ? "Locked door (" + label + ")" : "Open door (" + label + ")";
    }

    @Override
    protected void onInteract(Game g) {
        if (open) {
            if (g.player().distanceTo(getX(), getY()) > 1.1) { open = false; g.world().setTile(tx, ty, tileId); g.sfx("creak"); }
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

/** Room 404: opening it with the right key ends the game. POLYMORPHISM: overrides onUnlocked(). */
class EscapeDoor extends Door {
    public EscapeDoor(int tx, int ty, int tileId, String keyId) {
        super(tx, ty, tileId, "404", keyId, "Dirk: Room 404 is locked. There must be a key somewhere in this school.");
    }

    @Override protected void onUnlocked(Game g) { g.win(); }
}
