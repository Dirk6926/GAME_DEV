import java.util.ArrayList;
import java.util.List;

/**
 * The school map (4th floor). The grid is a 2D ARRAY of tile ids; floors have their own array.
 * Layout: a ring hallway around a courtyard, classrooms on all four sides.
 *
 * Every number below is a TILE. Everything not carved stays solid wall, so a one-tile gap
 * between two carved rectangles is a wall. To resize the map later, change the numbers
 * in the "MAP LAYOUT" block only.
 */
public class World {
    public static final int W = 70, H = 40;

    /** false = no lockers anywhere (the wall tiles stay plain and loot lies on the floor instead). */
    private static final boolean LOCKERS = false;

    private static final class RoomSpec {
        final int x0, y0, x1, y1, floorType;
        final String label, keyId, msg;
        final int doorX, doorY;
        final Light.Mode mode;

        RoomSpec(int x0, int y0, int x1, int y1, int floorType, String label,
                 int doorX, int doorY, String keyId, String msg, Light.Mode mode) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.floorType = floorType;
            this.label = label; this.keyId = keyId; this.msg = msg;
            this.doorX = doorX; this.doorY = doorY; this.mode = mode;
        }
    }

    private final int[][] tiles = new int[H][W];
    private final int[][] floor = new int[H][W];            // 0 = glossy hallway tile, 1 = room wood, 2 = concrete
    private final List<Light> lights = new ArrayList<>();
    private final List<Door> doors = new ArrayList<>();
    private final List<GameObject> initial = new ArrayList<>();

    public int tile(int x, int y) { return x < 0 || y < 0 || x >= W || y >= H ? Tile.WALL : tiles[y][x]; }
    public void setTile(int x, int y, int t) { tiles[y][x] = t; }
    public boolean isSolid(int x, int y) { return tile(x, y) != Tile.EMPTY; }
    public int floorType(int x, int y) { return x < 0 || y < 0 || x >= W || y >= H ? 2 : floor[y][x]; }
    public List<Light> lights() { return lights; }
    public List<Door> doors() { return doors; }
    public List<GameObject> initialObjects() { return initial; }

    public boolean lineOfSight(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        int steps = (int) (Math.hypot(dx, dy) / 0.12) + 1;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            if (isSolid((int) Math.floor(x0 + dx * t), (int) Math.floor(y0 + dy * t))) return false;
        }
        return true;
    }

    /** Debug helper: prints the map to the console. . hall, "," room, # wall, D door, x locker/window/etc. */
    public void dump() {
        for (int y = 0; y < H; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < W; x++) {
                int t = tiles[y][x];
                sb.append(t == Tile.EMPTY ? (floor[y][x] == 0 ? '.' : ',')
                        : t >= Tile.DOOR_BASE ? 'D' : t == Tile.WALL ? '#' : 'x');
            }
            System.out.println(sb);
        }
    }

    /* ------------------------------------------------------------ builder */
    private void carve(int x0, int y0, int x1, int y1, int floorType) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) { tiles[y][x] = Tile.EMPTY; floor[y][x] = floorType; }
    }

    private Door door(int x, int y, String label, String keyId, String msg, boolean escape) {
        int id = Tile.DOOR_BASE + doors.size();
        Door d = escape ? new EscapeDoor(x, y, id, keyId) : new Door(x, y, id, label, keyId, msg);
        tiles[y][x] = id;
        floor[y][x] = 1;
        Textures.registerDoor(id, label);
        doors.add(d);
        initial.add(d);
        return d;
    }

    private void roomLight(int x0, int y0, int x1, int y1, Light.Mode mode) {
        lights.add(new Light((x0 + x1 + 1) / 2.0, (y0 + y1 + 1) / 2.0, true, 1.0, mode, x0, y0, x1 + 1, y1 + 1));
    }

    private void room(int x0, int y0, int x1, int y1, int floorType, String label, int dx, int dy,
                      String keyId, String msg, Light.Mode mode) {
        carve(x0, y0, x1, y1, floorType);
        door(dx, dy, label, keyId, msg, false);
        roomLight(x0, y0, x1, y1, mode);
    }

    private boolean hallAdjacent(int x, int y) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < W && ny < H && tiles[ny][nx] == Tile.EMPTY && floor[ny][nx] == 0) return true;
        }
        return false;
    }

    private boolean roomBehind(int x, int y) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < W && ny < H && tiles[ny][nx] == Tile.EMPTY && floor[ny][nx] == 1) return true;
        }
        return false;
    }

    public static World build() {
        World w = new World();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { w.tiles[y][x] = Tile.WALL; w.floor[y][x] = 2; }

        /* ===================== MAP LAYOUT =====================
         * Hallways are 5 tiles wide. The courtyard (x 16..52, y 14..24) is never carved,
         * so it stays solid.
         */
        w.carve(11, 9, 57, 13, 0);
        w.carve(11, 25, 57, 29, 0);
        w.carve(11, 1, 15, 29, 0);
        w.carve(53, 1, 57, 37, 0);

        w.tiles[0][13] = Tile.EXIT; w.tiles[0][55] = Tile.EXIT; w.tiles[38][55] = Tile.EXIT;
        w.initial.add(Prop.chainedExit(13.5, 1.3));
        w.initial.add(Prop.chainedExit(55.5, 1.3));
        w.initial.add(Prop.chainedExit(55.5, 37.7));

        List<RoomSpec> rooms = List.of(
                new RoomSpec(17, 1, 30, 7, 1, "BIO LAB", 24, 8, null, null, Light.Mode.OFF),
                new RoomSpec(32, 1, 40, 7, 1, "410", 36, 8, null, null, Light.Mode.OFF),
                new RoomSpec(42, 1, 51, 7, 1, "409", 46, 8, null, null, Light.Mode.FLICKER),
                new RoomSpec(17, 31, 24, 37, 1, "403", 20, 30, null, null, Light.Mode.ON),
                new RoomSpec(35, 31, 42, 37, 1, "405", 38, 30, null, null, Light.Mode.OFF),
                new RoomSpec(44, 31, 51, 37, 1, "406", 47, 30, null, null, Light.Mode.OFF),
                new RoomSpec(1, 1, 9, 6, 2, "BOYS CR", 10, 3, null, null, Light.Mode.OFF),
                new RoomSpec(1, 8, 9, 14, 1, "401", 10, 11, null, null, Light.Mode.OFF),
                new RoomSpec(1, 16, 9, 22, 1, "402", 10, 19, null, null, Light.Mode.OFF),
                new RoomSpec(1, 24, 9, 29, 1, "FACULTY", 10, 27, "staff",
                        "Dirk: The faculty room is locked. Maybe a teacher left the key in a classroom.", Light.Mode.OFF),
                new RoomSpec(59, 1, 67, 12, 1, "CL 10", 58, 6, null, null, Light.Mode.OFF),
                new RoomSpec(59, 14, 67, 20, 1, "408", 58, 17, null, null, Light.Mode.OFF),
                new RoomSpec(59, 22, 67, 28, 1, "407", 58, 25, null, null, Light.Mode.OFF),
                new RoomSpec(59, 30, 67, 36, 2, "GIRLS CR", 58, 33, null, null, Light.Mode.OFF)
        );

        for (RoomSpec room : rooms) w.room(room.x0, room.y0, room.x1, room.y1, room.floorType,
                room.label, room.doorX, room.doorY, room.keyId, room.msg, room.mode);

        w.carve(26, 31, 33, 37, 1);
        w.door(29, 30, "404", "404", null, true);
        w.roomLight(26, 31, 33, 37, Light.Mode.ON);

        Light.Mode[] cyc = {Light.Mode.ON, Light.Mode.OFF, Light.Mode.FLICKER, Light.Mode.OFF,
                            Light.Mode.OFF, Light.Mode.ON, Light.Mode.OFF, Light.Mode.FLICKER};
        int n = 0;
        for (double x = 14.5; x < 57; x += 7) w.lights.add(new Light(x, 11.5, true, 1.0, cyc[n++ % cyc.length], 11, 9, 58, 14));
        for (double x = 14.5; x < 57; x += 7) w.lights.add(new Light(x, 27.5, true, 1.0, cyc[n++ % cyc.length], 11, 25, 58, 30));
        for (double y = 4.5; y < 30; y += 7) w.lights.add(new Light(13.5, y, false, 1.0, cyc[n++ % cyc.length], 11, 1, 16, 30));
        for (double y = 4.5; y < 38; y += 7) w.lights.add(new Light(55.5, y, false, 1.0, cyc[n++ % cyc.length], 53, 1, 58, 38));

        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if (w.tiles[y][x] != Tile.WALL || !w.hallAdjacent(x, y)) continue;
                int blk = Textures.hash(x / 3, y / 3, 99) & 255;
                int t = Tile.WALL;
                if (blk < 100) { if (LOCKERS) t = Tile.LOCKER; }
                else if (blk < 150 && w.roomBehind(x, y)) t = Tile.WINDOW;
                else if (blk < 156) t = Tile.BOARD;
                w.tiles[y][x] = t;
            }

        w.tiles[8][28] = Tile.BANNER; w.tiles[30][40] = Tile.BANNER; w.tiles[19][16] = Tile.BANNER;
        w.tiles[8][33] = Tile.EXTINGUISHER; w.tiles[30][24] = Tile.EXTINGUISHER; w.tiles[19][52] = Tile.EXTINGUISHER;

        w.initial.add(Prop.drawer(5.5, 8.6, Item.key("staff", "Faculty Room Key", 0, 0)));
        w.initial.add(Prop.drawer(5.5, 24.8, Item.key("404", "Room 404 Key", 0, 0)));
        return w;
    }

    /** Locker in a hall wall. side = which side of the hall the wall is on. */
    private void lockerAt(int x, int y, char side, Item loot) {
        double sx = x + 0.5, sy = y + 0.5;
        switch (side) {
            case 'N': sy = y + 1.2; break;   // wall above the hall
            case 'S': sy = y - 0.2; break;   // wall below the hall
            case 'W': sx = x + 1.2; break;   // wall left of the hall
            case 'E': sx = x - 0.2; break;   // wall right of the hall
            default: break;
        }
        if (LOCKERS) {
            tiles[y][x] = Tile.LOCKER;
            initial.add(Prop.locker(sx, sy, loot));
        } else if (loot != null) {              // no lockers: the loot just lies in the hallway
            loot.setPosition(sx, sy);
            initial.add(loot);
        }
    }
}