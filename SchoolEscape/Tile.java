/** Tile ids used by the map grid. Doors use ids from DOOR_BASE upwards. */
public final class Tile {
    private Tile() {}
    public static final int EMPTY = 0, WALL = 1, LOCKER = 2, WINDOW = 3, BANNER = 4, BOARD = 5,
            EXTINGUISHER = 6, EXIT = 7, DOOR_BASE = 32;
}
