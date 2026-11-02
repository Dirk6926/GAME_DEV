/**
 * ABSTRACT base class for everything that stands in the 3D world.
 * ENCAPSULATION: the position is private; other classes use getters/setters.
 * POLYMORPHISM: every subclass decides its own update() and how it looks.
 */
public abstract class GameObject {
    private double x, y;

    protected GameObject(double x, double y) { this.x = x; this.y = y; }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setPosition(double x, double y) { this.x = x; this.y = y; }

    public double distanceTo(double ox, double oy) { return Math.hypot(ox - x, oy - y); }

    /** Called every frame. */
    public abstract void update(Game g, double dt);

    /** Picture drawn in the world (null = invisible, like a door that is part of the wall). */
    public Sprite sprite() { return null; }
    /** Height of the picture in world units (a wall is 1.75 high). */
    public double height() { return 0.5; }
    /** How far above the floor the picture floats. */
    public double lift() { return 0; }
    /** Self-lighting so important things can be spotted in the dark. */
    public double glow() { return 0; }
    /** Radius that blocks the player (0 = walk through). */
    public double solidRadius() { return 0; }
    public boolean visible() { return true; }
    public double alpha() { return 1.0; }
}
