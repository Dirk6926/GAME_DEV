/**
 * Living things. TEMPLATE METHOD: update() is final and calls the abstract think(),
 * so every creature moves the same way but decides differently.
 */
public abstract class Entity extends GameObject {
    private double speed;

    protected Entity(double x, double y, double speed) {
        super(x, y);
        this.speed = speed;
    }

    public double getSpeed() { return speed; }
    public void setSpeed(double s) { speed = s; }

    @Override
    public final void update(Game g, double dt) { think(g, dt); }

    protected abstract void think(Game g, double dt);

    /** Walks toward a point. Returns true once it has arrived. */
    protected boolean stepToward(double tx, double ty, double dt, double spd) {
        double dx = tx - getX(), dy = ty - getY();
        double d = Math.hypot(dx, dy);
        double step = spd * dt;
        if (d <= step || d < 0.001) { setPosition(tx, ty); return true; }
        setPosition(getX() + dx / d * step, getY() + dy / d * step);
        return false;
    }
}
