/**
 * The shadow figure that haunts the halls. It patrols a loop of waypoints (an array),
 * follows BFS routes stored in a Queue, hears noise and fears the flashlight beam.
 */
public class Stalker extends Entity {

    public enum Mode { IDLE, PATROL, CHASE, FLEE }

    private static final double[][] PATROL = {
        {53.5, 21.5}, {29.5, 21.5}, {29.5, 6.5}, {29.5, 21.5}, {29.5, 36.0}, {29.5, 21.5}, {6.5, 21.5}
    };

    private Mode mode = Mode.IDLE;
    private MyQueue<double[]> route = new MyQueue<>();
    private int patrolIndex;
    private double repath, exposure, cooldown, lostTimer, idleTimer = 14;

    public Stalker() { super(53.5, 21.5, 1.5); }

    public Mode getMode() { return mode; }

    @Override
    protected void think(Game g, double dt) {
        Player p = g.player();
        World w = g.world();
        double d = distanceTo(p.getX(), p.getY());
        boolean los = d < 30 && w.lineOfSight(getX(), getY(), p.getX(), p.getY());
        cooldown -= dt;

        // is the flashlight beam on the stalker?
        boolean lit = g.flashlightOn() && los && d < 14 && g.inBeam(getX(), getY());
        exposure = lit ? exposure + dt : Math.max(0, exposure - dt * 0.7);
        if (exposure > 1.1 && mode != Mode.FLEE) {
            exposure = 0;
            flee(g);
        }

        double hearing = 3.0 + p.getNoise() * 9.0;          // how far away it notices the player
        switch (mode) {
            case IDLE:
                idleTimer -= dt;
                if (d < 16 || idleTimer <= 0) { mode = Mode.PATROL; }
                break;
            case PATROL:
                follow(dt, 1.5);
                if (route.isEmpty()) planTo(w, PATROL[patrolIndex = (patrolIndex + 1) % PATROL.length]);
                if (cooldown <= 0 && los && d < hearing) { mode = Mode.CHASE; lostTimer = 0; repath = 0; g.sfx("sting"); g.say("Dirk: It saw me!"); }
                break;
            case CHASE:
                repath -= dt;
                if (repath <= 0) { repath = 0.4; route = Pathfinder.findPath(w, (int) getX(), (int) getY(), (int) p.getX(), (int) p.getY()); }
                follow(dt, 2.9);
                lostTimer = los ? 0 : lostTimer + dt;
                if (lostTimer > 5 || d > 26) { mode = Mode.PATROL; route = new MyQueue<>(); }
                if (d < 0.95) {
                    g.hurtPlayer(15);
                    flee(g);
                    cooldown = 5;
                }
                break;
            case FLEE:
                follow(dt, 3.6);
                if (route.isEmpty()) { mode = Mode.PATROL; cooldown = Math.max(cooldown, 6); }
                break;
        }

        // lights flicker when it is close
        if (d < 10) g.disturbLights(getX(), getY(), 9, 0.5);
    }

    private void flee(Game g) {
        mode = Mode.FLEE;
        Player p = g.player();
        double best = -1;
        double[] target = PATROL[0];
        for (double[] wp : PATROL) {
            double dd = Math.hypot(wp[0] - p.getX(), wp[1] - p.getY());
            if (dd > best) { best = dd; target = wp; }
        }
        planTo(g.world(), target);
        cooldown = 6;
    }

    private void planTo(World w, double[] target) {
        route = Pathfinder.findPath(w, (int) getX(), (int) getY(), (int) target[0], (int) target[1]);
    }

    private void follow(double dt, double spd) {
        if (route.isEmpty()) return;
        double[] wp = route.peek();
        if (stepToward(wp[0], wp[1], dt, spd)) route.dequeue();
    }

    @Override public Sprite sprite() { return Textures.stalker; }
    @Override public double height() { return 1.62; }
    @Override public double glow() { return 0.05; }
}
