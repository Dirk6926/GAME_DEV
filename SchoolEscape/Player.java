import java.awt.event.KeyEvent;

/**
 * Dirk, the student. ENCAPSULATION: all fields are private, other classes use the methods,
 * so the player can never be put into an invalid state (for example HP above the maximum).
 */
public class Player {
    public static final double RADIUS = 0.22;
    public static final double EYE = 0.98;

    private double x, y, angle, pitch;
    private double stamina = 100, crouch, z, vz, bobPhase;
    private boolean exhausted, moving, sprinting, quiet, crouching;
    private double stepAcc;
    private int hp = 100, maxHp = 100, level = 1, xp;
    private int selected;
    private final Inventory inventory = new Inventory();
    private final MyTree<Integer, NoteItem> journal = new MyTree<>();
    private final FlashlightItem flashlight = new FlashlightItem();

    public Player(double x, double y, double angle) {
        this.x = x; this.y = y; this.angle = angle;
        inventory.add(flashlight);
    }

    /* ---------------------------------------------------------- getters */
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public double getPitch() { return pitch; }
    public double getStamina() { return stamina; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getLevel() { return level; }
    public int getXp() { return xp; }
    public int getXpNeeded() { return level * 60; }
    public boolean isSprinting() { return sprinting; }
    public boolean isMoving() { return moving; }
    public Inventory getInventory() { return inventory; }
    public MyTree<Integer, NoteItem> getJournal() { return journal; }
    public FlashlightItem getFlashlight() { return flashlight; }
    public int getSelected() { return selected; }
    public Item getSelectedItem() { return inventory.get(selected); }
    public double distanceTo(double ox, double oy) { return Math.hypot(ox - x, oy - y); }

    public void teleport(double nx, double ny, double na) { x = nx; y = ny; angle = na; }
    public void setAngle(double a) { angle = a; }
    public void setPitch(double p) { pitch = p; }
    public void select(int i) { if (i >= 0 && i < Inventory.CAPACITY) selected = i; }
    public void cycle(int dir) { selected = ((selected + dir) % Inventory.CAPACITY + Inventory.CAPACITY) % Inventory.CAPACITY; }

    public void heal(int n) { hp = Math.min(maxHp, hp + n); }
    public void damage(int n) { hp = Math.max(0, hp - n); }

    /** Returns true when this gained a level. */
    public boolean addXp(int n) {
        xp += n;
        boolean up = false;
        while (xp >= getXpNeeded()) { xp -= getXpNeeded(); level++; maxHp += 10; hp = maxHp; up = true; }
        return up;
    }

    /** Camera height: standing, crouching, jumping and walking bob. */
    public double getEye() { return EYE - 0.36 * crouch + z + Math.sin(bobPhase) * (moving ? 0.012 : 0); }

    /** 0..1 how loud the player is right now (the Stalker listens). */
    public double getNoise() {
        if (!moving) return 0.05;
        if (crouching) return 0.15;
        if (quiet) return 0.25;
        return sprinting ? 1.0 : 0.55;
    }

    /* ---------------------------------------------------------- update */
    public void update(Game g, Input in, double dt) {
        angle += in.takeDX() * 0.0021;
        pitch -= in.takeDY() * 0.45;
        if (in.isDown(KeyEvent.VK_LEFT)) angle -= 2.0 * dt;
        if (in.isDown(KeyEvent.VK_RIGHT)) angle += 2.0 * dt;
        if (in.isDown(KeyEvent.VK_UP)) pitch += 110 * dt;
        if (in.isDown(KeyEvent.VK_DOWN)) pitch -= 110 * dt;
        pitch = Math.max(-140, Math.min(140, pitch));

        double fwd = 0, side = 0;
        if (in.isDown(KeyEvent.VK_W)) fwd += 1;
        if (in.isDown(KeyEvent.VK_S)) fwd -= 1;
        if (in.isDown(KeyEvent.VK_D)) side += 1;
        if (in.isDown(KeyEvent.VK_A)) side -= 1;
        moving = fwd != 0 || side != 0;
        crouching = in.isDown(KeyEvent.VK_C);
        quiet = in.isDown(KeyEvent.VK_CONTROL);
        sprinting = moving && in.isDown(KeyEvent.VK_SHIFT) && !crouching && !quiet && !exhausted && stamina > 0;

        double speed = 2.4;
        if (crouching) speed = 1.3;
        else if (quiet) speed = 1.6;
        else if (sprinting) speed = 4.1;

        if (sprinting) {
            stamina = Math.max(0, stamina - 20 * dt);
            if (stamina <= 0) exhausted = true;
        } else {
            stamina = Math.min(100, stamina + (moving ? 8 : 16) * dt);
            if (exhausted && stamina > 25) exhausted = false;
        }
        crouch += ((crouching ? 1 : 0) - crouch) * Math.min(1, dt * 9);

        if (in.pressed(KeyEvent.VK_SPACE) && z <= 0.001 && !crouching && stamina > 8) { vz = 2.3; stamina -= 6; }
        if (vz != 0 || z > 0) {
            vz -= 8.0 * dt;
            z += vz * dt;
            if (z <= 0) { z = 0; vz = 0; g.sfx("land"); }
        }

        if (moving) {
            double len = Math.hypot(fwd, side);
            double dx = Math.cos(angle), dy = Math.sin(angle), rx = -dy, ry = dx;
            double mx = (dx * fwd + rx * side) / len * speed * dt;
            double my = (dy * fwd + ry * side) / len * speed * dt;
            if (free(g, x + mx, y)) x += mx;
            if (free(g, x, y + my)) y += my;
            bobPhase += dt * speed * 3.0;
            stepAcc += speed * dt;
            double stride = sprinting ? 1.5 : 1.2;
            if (stepAcc > stride && z <= 0) {
                stepAcc = 0;
                if (!quiet) g.sfx(sprinting ? "run" : (crouching ? "stepSoft" : "step"));
            }
        }
        flashlight.drain(dt);
    }

    private boolean free(Game g, double nx, double ny) {
        World w = g.world();
        for (int cy = (int) Math.floor(ny - RADIUS); cy <= (int) Math.floor(ny + RADIUS); cy++)
            for (int cx = (int) Math.floor(nx - RADIUS); cx <= (int) Math.floor(nx + RADIUS); cx++)
                if (w.isSolid(cx, cy)) return false;
        return !g.blockedByObject(nx, ny, RADIUS);
    }
}
