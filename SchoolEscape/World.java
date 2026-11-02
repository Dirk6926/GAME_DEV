import java.util.ArrayList;
import java.util.List;

/**
 * The school map. The grid is a 2D ARRAY of tile ids; floors have their own array.
 * Layout: one long east-west hallway crossed by a north-south hallway, classrooms on both sides.
 */
public class World {
    public static final int W = 60, H = 44;

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

        // hallways
        w.carve(4, 20, 55, 22, 0);          // main east-west hall
        w.carve(28, 4, 30, 19, 0);          // north hall
        w.carve(28, 23, 30, 37, 0);         // south hall

        // chained emergency exits at the ends of the halls
        w.tiles[21][3] = Tile.EXIT; w.tiles[21][56] = Tile.EXIT; w.tiles[38][29] = Tile.EXIT;
        w.initial.add(new ChainedExit(4.2, 21.5));
        w.initial.add(new ChainedExit(54.8, 21.5));
        w.initial.add(new ChainedExit(29.5, 37.0));

        String locked = "Dirk: Locked. I need the right key.";
        // north classrooms (doors in the wall y=19)
        w.room(5, 12, 11, 18, 1, "101", 8, 19, null, null, Light.Mode.OFF);
        w.room(13, 12, 19, 18, 1, "102", 16, 19, null, null, Light.Mode.OFF);
        w.room(21, 12, 26, 18, 1, "103", 23, 19, null, null, Light.Mode.FLICKER);
        w.room(32, 12, 38, 18, 1, "104", 35, 19, null, null, Light.Mode.OFF);
        w.room(40, 12, 46, 18, 1, "105", 43, 19, null, null, Light.Mode.OFF);
        w.room(48, 12, 54, 18, 1, "106", 51, 19, null, null, Light.Mode.OFF);
        // south rooms (doors in the wall y=23)
        w.room(5, 24, 11, 30, 1, "107", 8, 23, null, null, Light.Mode.OFF);
        w.room(13, 24, 19, 30, 1, "LIBRARY", 16, 23, null, null, Light.Mode.OFF);
        w.room(21, 24, 26, 30, 1, "CLINIC", 23, 23, null, null, Light.Mode.ON);
        w.room(32, 24, 46, 30, 1, "CAFETERIA", 39, 23, null, null, Light.Mode.OFF);
        w.room(48, 24, 54, 30, 2, "JANITOR", 51, 23, null, null, Light.Mode.OFF);
        // south hall rooms
        w.room(20, 31, 26, 36, 1, "STAFF", 27, 33, "staff",
                "Dirk: The staff room is locked. Maybe a teacher left the key in a classroom.", Light.Mode.OFF);
        w.room(32, 31, 38, 36, 2, "STORAGE", 31, 33, null, null, Light.Mode.OFF);
        // Room 404: the way out
        w.carve(27, 1, 31, 2, 1);
        w.door(29, 3, "404", "404", null, true);
        w.roomLight(27, 1, 31, 2, Light.Mode.ON);

        // hallway fixtures (zone = the hall they belong to)
        double[] hx = {8.5, 14.5, 20.5, 26.5, 33.5, 39.5, 45.5, 51.5};
        Light.Mode[] hm = {Light.Mode.ON, Light.Mode.OFF, Light.Mode.FLICKER, Light.Mode.OFF,
                           Light.Mode.OFF, Light.Mode.ON, Light.Mode.OFF, Light.Mode.FLICKER};
        for (int i = 0; i < hx.length; i++) w.lights.add(new Light(hx[i], 21.5, true, 1.0, hm[i], 4, 20, 56, 23));
        w.lights.add(new Light(29.5, 21.5, false, 1.0, Light.Mode.ON, 28, 4, 31, 38));
        double[] vy = {7.5, 12.5, 17.0, 26.5, 31.5, 36.0};
        Light.Mode[] vm = {Light.Mode.OFF, Light.Mode.FLICKER, Light.Mode.OFF, Light.Mode.OFF, Light.Mode.ON, Light.Mode.OFF};
        for (int i = 0; i < vy.length; i++) w.lights.add(new Light(29.5, vy[i], false, 1.0, vm[i], 28, i < 3 ? 4 : 23, 31, i < 3 ? 20 : 38));

        // decorate the hallway walls: lockers, windows into classrooms, banners...
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if (w.tiles[y][x] != Tile.WALL || !w.hallAdjacent(x, y)) continue;
                int blk = Textures.hash(x / 3, y / 3, 99) & 255;
                int t = Tile.WALL;
                if (blk < 100) t = Tile.LOCKER;
                else if (blk < 150 && w.roomBehind(x, y)) t = Tile.WINDOW;
                else if (blk < 156) t = Tile.BOARD;
                w.tiles[y][x] = t;
            }
        // hand-placed decoration
        w.tiles[19][12] = Tile.BANNER; w.tiles[23][41] = Tile.BANNER; w.tiles[10][27] = Tile.BANNER;
        w.tiles[19][22] = Tile.EXTINGUISHER; w.tiles[23][47] = Tile.EXTINGUISHER; w.tiles[31][31] = Tile.EXTINGUISHER;

        // searchable lockers next to the hall walls (tile forced to be a locker)
        w.lockerAt(9, 19, true, new BatteryItem(0, 0));
        w.lockerAt(14, 23, false, null);
        w.lockerAt(33, 19, true, new MedkitItem(0, 0));
        w.lockerAt(36, 23, false, new BatteryItem(0, 0));
        w.lockerAt(53, 19, true, null);
        w.lockerAt(27, 8, null);

        // plants
        double[][] plants = {{5.2, 20.5}, {18.4, 22.5}, {36.6, 20.5}, {54.4, 22.5}, {28.5, 8.0}, {30.5, 30.0}};
        for (double[] p : plants) w.initial.add(new Plant(p[0], p[1]));

        // notes (the school's story) and supplies inside rooms
        w.initial.add(new NoteItem(1, "They told us to STUDY HARDER. Nobody told us the doors would lock behind us.", 8.5, 15.0));
        w.initial.add(new NoteItem(2, "The lights only stay on in places where it is not watching. Keep your flashlight pointed at it.", 16.5, 15.0));
        w.initial.add(new NoteItem(3, "Mr. Reyes locked the staff room and hid the key in a classroom drawer. 'Safest place', he said.", 35.5, 15.0));
        w.initial.add(new NoteItem(4, "It moves when you are loud. Crouch. Walk slowly. Do not run unless you have to.", 16.5, 27.0));
        w.initial.add(new NoteItem(5, "Room 404 is the only door that was never chained. The key is with the teachers.", 40.0, 27.0));
        w.initial.add(new NoteItem(6, "If you are reading this, keep going. The way out is at the end of the north hall.", 51.5, 26.0));
        w.initial.add(new MedkitItem(23.5, 27.0));
        w.initial.add(new BatteryItem(51.5, 24.8));
        w.initial.add(new BatteryItem(34.5, 33.5));

        // the two keys inside drawers
        w.initial.add(new DeskDrawer(23.5, 12.6, new KeyItem("staff", "Staff Room Key", 0, 0)));
        w.initial.add(new DeskDrawer(23.5, 31.8, new KeyItem("404", "Room 404 Key", 0, 0)));
        return w;
    }

    private void lockerAt(int x, int y, boolean northSide, Item loot) {
        tiles[y][x] = Tile.LOCKER;
        initial.add(new LockerSpot(x + 0.5, northSide ? 20.2 : 22.8, loot));
    }

    private void lockerAt(int x, int y, Item loot) {      // locker on a vertical wall of the north hall
        tiles[y][x] = Tile.LOCKER;
        initial.add(new LockerSpot(x + 1.2, y + 0.5, loot));
    }
}
