/** A desk, chair or other classroom piece. Just a picture in the world that can block the player. */
public class Furniture extends GameObject {
    private final Sprite sprite;
    private final double height, radius, lift;

    /** height = how tall in world units (a wall is 1.75). radius = how far it pushes the player away (0 = walk through). */
    public Furniture(double x, double y, Sprite sprite, double height, double radius) { this(x, y, sprite, height, radius, 0); }

    /** lift = how far above the floor the bottom of the picture floats (used for things hung on a wall). */
    public Furniture(double x, double y, Sprite sprite, double height, double radius, double lift) {
        super(x, y);
        this.sprite = sprite;
        this.height = height;
        this.radius = radius;
        this.lift = lift;
    }

    @Override public void update(Game g, double dt) { }
    @Override public Sprite sprite() { return sprite; }
    @Override public double height() { return height; }
    @Override public double lift() { return lift; }
    @Override public double solidRadius() { return radius; }
}

/** Invisible wall of air. The intro uses one to keep the player inside the classroom. */
class Barrier extends GameObject {
    private final double radius;

    public Barrier(double x, double y, double radius) { super(x, y); this.radius = radius; }

    @Override public void update(Game g, double dt) { }
    @Override public double solidRadius() { return radius; }
}
