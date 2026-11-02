/**
 * Something lying in the school that the player can pick up.
 * INHERITANCE: Item extends GameObject. Each subclass overrides use() differently (POLYMORPHISM).
 */
public abstract class Item extends GameObject implements Interactable {
    private final String name, description;
    private boolean taken;
    private double t;

    protected Item(String name, String description, double x, double y) {
        super(x, y);
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    /** What happens when the player uses this item from the hotbar. */
    public abstract void use(Game g);

    /** Notes are read instead of being stored in the hotbar. */
    protected boolean goesToInventory() { return true; }
    protected void onPickUp(Game g) { }

    @Override
    public final void interact(Game g) {
        if (taken) return;
        if (goesToInventory() && !g.player().getInventory().add(this)) {
            g.say("Dirk: I can't carry anything else.");
            return;
        }
        taken = true;
        g.removeObject(this);
        g.sfx("pickup");
        g.addXp(10);
        onPickUp(g);
    }

    @Override public String prompt() { return "Pick up " + name; }
    @Override public boolean canInteract(Game g) { return !taken; }
    @Override public void update(Game g, double dt) { t += dt; }
    @Override public double lift() { return 0.05 + 0.02 * Math.sin(t * 2.2); }
    @Override public double glow() { return 0.30; }
    @Override public boolean visible() { return !taken; }
}

/** The flashlight: has a battery that drains while it is switched on. */
class FlashlightItem extends Item {
    private double charge = 100;
    private boolean on = true;

    public FlashlightItem() { super("Flashlight", "A heavy black flashlight. Click to switch it on and off.", 0, 0); }

    public double getCharge() { return charge; }
    public boolean isOn() { return on && charge > 0; }
    public void recharge(double amount) { charge = Math.min(100, charge + amount); }
    public void toggle() { if (charge > 0) on = !on; }

    public void drain(double dt) {
        if (!isOn()) return;
        charge = Math.max(0, charge - dt * 0.30);
        if (charge <= 0) on = false;
    }

    @Override public void use(Game g) { toggle(); g.sfx("click"); }
    @Override public Sprite sprite() { return Textures.flashlight; }
    @Override public double height() { return 0.12; }
}

/** A key. The id says which door it opens. */
class KeyItem extends Item {
    private final String id;

    public KeyItem(String id, String name, double x, double y) {
        super(name, "A key. It will open a locked door somewhere in the school.", x, y);
        this.id = id;
    }

    public String getId() { return id; }
    @Override public void use(Game g) { g.say("Dirk: " + getName() + ". Now I need to find the door it fits."); }
    @Override public Sprite sprite() { return Textures.key; }
    @Override public double height() { return 0.2; }
    @Override public double glow() { return 0.6; }

    @Override
    protected void onPickUp(Game g) { g.onKeyFound(id); }
}

/** Recharges the flashlight. */
class BatteryItem extends Item {
    public BatteryItem(double x, double y) { super("Battery", "Recharges the flashlight (+60%).", x, y); }

    @Override
    public void use(Game g) {
        FlashlightItem f = g.player().getFlashlight();
        if (f == null) return;
        f.recharge(60);
        g.player().getInventory().remove(this);
        g.sfx("pickup");
        g.say("Dirk: The flashlight is brighter again.");
    }

    @Override public Sprite sprite() { return Textures.battery; }
    @Override public double height() { return 0.2; }
}

/** Heals the player. */
class MedkitItem extends Item {
    public MedkitItem(double x, double y) { super("Medkit", "Heals 40 HP.", x, y); }

    @Override
    public void use(Game g) {
        if (g.player().getHp() >= g.player().getMaxHp()) { g.say("Dirk: I'm not hurt right now."); return; }
        g.player().heal(40);
        g.player().getInventory().remove(this);
        g.sfx("pickup");
        g.say("Dirk: That feels a little better.");
    }

    @Override public Sprite sprite() { return Textures.medkit; }
    @Override public double height() { return 0.16; }
}

/** A page of writing. It goes into the journal, which is stored in a binary search tree. */
class NoteItem extends Item {
    private final int number;
    private final String text;

    public NoteItem(int number, String text, double x, double y) {
        super("Note " + number, text, x, y);
        this.number = number;
        this.text = text;
    }

    public int getNumber() { return number; }
    public String getText() { return text; }
    @Override protected boolean goesToInventory() { return false; }
    @Override public void use(Game g) { g.showNote(this); }
    @Override public Sprite sprite() { return Textures.note; }
    @Override public double height() { return 0.2; }

    @Override
    protected void onPickUp(Game g) {
        g.player().getJournal().insert(number, this);
        g.addXp(15);
        g.showNote(this);
    }
}
