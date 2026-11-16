/**
 * A single generic item class that covers all pickupable objects in the game.
 * The different item types are distinguished by an enum instead of many subclasses.
 */
public class Item extends GameObject implements Interactable {
    public enum Type { FLASHLIGHT, KEY, BATTERY, MEDKIT, NOTE }

    private final Type type;
    private final String name, description;
    private final String id;
    private final int number;
    private final String text;
    private final boolean inventoryOnly;
    private boolean taken;
    private double t;
    private boolean on = true;
    private double charge = 100;

    public static Item flashlight() {
        return new Item(Type.FLASHLIGHT, "Flashlight",
                "A heavy black flashlight. Click to switch it on and off.", null, 0, null, true, 0, 0);
    }

    public static Item key(String id, String name, double x, double y) {
        return new Item(Type.KEY, name,
                "A key. It will open a locked door somewhere in the school.", id, 0, null, true, x, y);
    }

    public static Item battery(double x, double y) {
        return new Item(Type.BATTERY, "Battery",
                "Recharges the flashlight (+60%).", null, 0, null, true, x, y);
    }

    public static Item medkit(double x, double y) {
        return new Item(Type.MEDKIT, "Medkit",
                "Heals 40 HP.", null, 0, null, true, x, y);
    }

    public static Item note(int number, String text, double x, double y) {
        return new Item(Type.NOTE, "Note " + number, text, null, number, text, false, x, y);
    }

    private Item(Type type, String name, String description, String id, int number, String text,
                 boolean inventoryOnly, double x, double y) {
        super(x, y);
        this.type = type;
        this.name = name;
        this.description = description;
        this.id = id;
        this.number = number;
        this.text = text;
        this.inventoryOnly = inventoryOnly;
    }

    public Type getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getId() { return id; }
    public int getNumber() { return number; }
    public String getText() { return text; }

    public boolean isFlashlight() { return type == Type.FLASHLIGHT; }
    public boolean isOn() { return type == Type.FLASHLIGHT && on && charge > 0; }
    public double getCharge() { return charge; }
    public void recharge(double amount) { if (type == Type.FLASHLIGHT) charge = Math.min(100, charge + amount); }
    public void toggle() { if (type == Type.FLASHLIGHT && charge > 0) on = !on; }
    public void drain(double dt) {
        if (type != Type.FLASHLIGHT || !isOn()) return;
        charge = Math.max(0, charge - dt * 0.30);
        if (charge <= 0) on = false;
    }

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

    protected boolean goesToInventory() { return inventoryOnly; }
    protected void onPickUp(Game g) {
        switch (type) {
            case KEY:
                g.onKeyFound(id);
                return;
            case NOTE:
                g.player().getJournal().insert(number, this);
                g.addXp(15);
                g.showNote(this);
                return;
            case FLASHLIGHT:
            case BATTERY:
            case MEDKIT:
                default:
                    return;
        }
    }

    @Override public String prompt() {
        if (type == Type.NOTE) return "Read note";
        return "Pick up " + name;
    }
    @Override public boolean canInteract(Game g) { return !taken; }
    @Override public void update(Game g, double dt) { t += dt; }
    @Override public double lift() { return 0.05 + 0.02 * Math.sin(t * 2.2); }
    @Override public double glow() {
        if (type == Type.FLASHLIGHT) return 0.30;
        if (type == Type.KEY) return 0.6;
        return 0.0;
    }
    @Override public boolean visible() { return !taken; }

    public void use(Game g) {
        switch (type) {
            case FLASHLIGHT:
                toggle();
                g.sfx("click");
                return;
            case KEY:
                g.say("Dirk: " + getName() + ". Now I need to find the door it fits.");
                return;
            case BATTERY:
                Item flashlight = g.player().getFlashlight();
                if (flashlight != null) {
                    flashlight.recharge(60);
                    g.player().getInventory().remove(this);
                    g.sfx("pickup");
                    g.say("Dirk: The flashlight is brighter again.");
                }
                return;
            case MEDKIT:
                if (g.player().getHp() >= g.player().getMaxHp()) {
                    g.say("Dirk: I'm not hurt right now.");
                    return;
                }
                g.player().heal(40);
                g.player().getInventory().remove(this);
                g.sfx("pickup");
                g.say("Dirk: That feels a little better.");
                return;
            case NOTE:
                g.showNote(this);
                return;
            default:
                return;
        }
    }

    @Override public Sprite sprite() {
        switch (type) {
            case FLASHLIGHT: return Textures.flashlight;
            case KEY: return Textures.key;
            case BATTERY: return Textures.battery;
            case MEDKIT: return Textures.medkit;
            case NOTE: return Textures.note;
            default: return null;
        }
    }

    @Override public double height() {
        switch (type) {
            case FLASHLIGHT: return 0.12;
            case KEY: return 0.2;
            case BATTERY: return 0.2;
            case MEDKIT: return 0.16;
            case NOTE: return 0.2;
            default: return 0.2;
        }
    }
}
