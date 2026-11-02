import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** The game controller: owns the world, the player, every object and the state stack. */
public class Game {
    public enum State { PLAYING, PAUSED, INVENTORY, JOURNAL, DEAD, WON }

    private final World world;
    private final Player player;
    private final Stalker stalker;
    private final Sound sound = new Sound();
    private final Input input;
    private final List<GameObject> objects = new ArrayList<>();
    private final List<GameObject> toAdd = new ArrayList<>();
    private final List<GameObject> toRemove = new ArrayList<>();
    private final MyStack<State> states = new MyStack<>();     // STACK: menus pile on top of play
    private final MyQueue<String> thoughts = new MyQueue<>();  // QUEUE: lines shown one after another
    private String line = "";
    private double lineTimer, time, heartTimer, hurtFlash, toast;
    private String toastText = "";
    private String objective = "Find the key to Room 404 and escape the school.";
    private Interactable target;
    private NoteItem shownNote;
    private int stage;
    private int menuSel;

    public Game(Input input) {
        this.input = input;
        Textures.init();
        world = World.build();
        player = new Player(5.5, 21.5, 0);
        stalker = new Stalker();
        objects.add(stalker);
        objects.addAll(world.initialObjects());
        states.push(State.PLAYING);
        sound.startAmbient();
        say("Dirk: Everyone is gone... I need to find a way out.");
    }

    /* ------------------------------------------------------------ getters */
    public World world() { return world; }
    public Player player() { return player; }
    public Stalker stalker() { return stalker; }
    public List<GameObject> objects() { return objects; }
    public double time() { return time; }
    public State state() { return states.peek(); }
    public String objective() { return objective; }
    public String line() { return lineTimer > 0 ? line : ""; }
    public Interactable target() { return target; }
    public NoteItem shownNote() { return shownNote; }
    public double hurtFlash() { return hurtFlash; }
    public String toastText() { return toast > 0 ? toastText : ""; }
    public double toastAlpha() { return Math.min(1, toast); }
    public int menuSel() { return menuSel; }
    public Input input() { return input; }

    /* ------------------------------------------------------------ services for objects */
    public void say(String s) { thoughts.enqueue(s); }
    public void sfx(String n) { sound.play(n); }
    public void addObject(GameObject o) { toAdd.add(o); }
    public void removeObject(GameObject o) { toRemove.add(o); }

    public void addXp(int n) {
        if (player.addXp(n)) { sfx("pickup"); toast("LEVEL UP!  Lv. " + player.getLevel(), 3); }
    }

    public void hurtPlayer(int n) {
        player.damage(n);
        hurtFlash = 1;
        sfx("hurt");
        if (player.getHp() <= 0) { states.push(State.DEAD); sound.stopAmbient(); }
    }

    public void onKeyFound(String id) {
        if (id.equals("staff") && stage < 1) {
            stage = 1;
            objective = "Open the Staff Room in the south hall and find the key to Room 404.";
            toast("Objective updated", 3);
        } else if (id.equals("404")) {
            stage = 2;
            objective = "Go to Room 404 at the north end of the school and escape.";
            toast("Objective updated", 3);
        }
    }

    public void win() { states.push(State.WON); sound.stopAmbient(); sfx("win"); }
    public void showNote(NoteItem n) { shownNote = n; states.push(State.JOURNAL); }

    public boolean flashlightOn() {
        Item it = player.getSelectedItem();
        return it instanceof FlashlightItem && ((FlashlightItem) it).isOn();
    }

    public boolean inBeam(double x, double y) {
        double a = Math.atan2(y - player.getY(), x - player.getX()) - player.getAngle();
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return Math.abs(a) < 0.28;
    }

    public void disturbLights(double x, double y, double radius, double seconds) {
        for (Light l : world.lights())
            if (Math.hypot(l.getX() - x, l.getY() - y) < radius) l.disturb(time + seconds);
    }

    public boolean blockedByObject(double x, double y, double r) {
        for (GameObject o : objects) {
            double s = o.solidRadius();
            if (s > 0 && o.distanceTo(x, y) < s + r) return true;
        }
        return false;
    }

    private void toast(String s, double secs) { toastText = s; toast = secs; }

    public void restart() { /* handled by Main creating a new Game */ }

    /* ------------------------------------------------------------ loop */
    public void update(double dt) {
        Input in = input;
        State st = state();

        if (st == State.PLAYING) {
            if (in.pressed(KeyEvent.VK_ESCAPE)) { states.push(State.PAUSED); menuSel = 0; in.clearEdges(); return; }
            if (in.pressed(KeyEvent.VK_TAB)) { states.push(State.INVENTORY); menuSel = player.getSelected(); in.clearEdges(); return; }
            if (in.pressed(KeyEvent.VK_J)) { shownNote = null; states.push(State.JOURNAL); in.clearEdges(); return; }
            time += dt;
            player.update(this, in, dt);
            for (int i = 1; i <= 7; i++) if (in.pressed(KeyEvent.VK_0 + i)) player.select(i - 1);
            int w = in.takeWheel();
            if (w != 0) player.cycle(w > 0 ? 1 : -1);
            if (in.pressed(KeyEvent.VK_T)) { player.select(0); player.getFlashlight().toggle(); sfx("click"); }
            findTarget();
            if (target != null && (in.pressed(KeyEvent.VK_F) || in.pressed(KeyEvent.VK_E))) target.interact(this);
            boolean click = in.takeClick();
            if (click || in.pressed(KeyEvent.VK_Q)) {
                Item it = player.getSelectedItem();
                if (it != null) {
                    boolean wasFlash = it instanceof FlashlightItem;
                    it.use(this);
                    if (!wasFlash && !(it instanceof FlashlightItem)) { /* consumables remove themselves */ }
                }
            }
            for (GameObject o : objects) o.update(this, dt);
            objects.removeAll(toRemove); toRemove.clear();
            objects.addAll(toAdd); toAdd.clear();

            heartTimer -= dt;
            double d = stalker.distanceTo(player.getX(), player.getY());
            if (d < 9 && heartTimer <= 0 && world.lineOfSight(stalker.getX(), stalker.getY(), player.getX(), player.getY())) {
                sfx("heart"); heartTimer = 0.5 + d * 0.1;
            }
            hurtFlash = Math.max(0, hurtFlash - dt * 1.6);
        } else {
            menus(in, st);
        }
        if (toast > 0) toast -= dt;
        // thoughts: one line at a time from the queue
        if (lineTimer > 0) lineTimer -= dt;
        if (lineTimer <= 0 && !thoughts.isEmpty()) { line = thoughts.dequeue(); lineTimer = Math.max(2.5, line.length() * 0.055); }
    }

    private void menus(Input in, State st) {
        if (st == State.PAUSED) {
            if (in.pressed(KeyEvent.VK_ESCAPE)) { states.pop(); in.clearEdges(); }
        } else if (st == State.INVENTORY) {
            if (in.pressed(KeyEvent.VK_TAB) || in.pressed(KeyEvent.VK_ESCAPE)) { states.pop(); in.clearEdges(); return; }
            if (in.pressed(KeyEvent.VK_LEFT) || in.pressed(KeyEvent.VK_A)) menuSel = (menuSel + 6) % 7;
            if (in.pressed(KeyEvent.VK_RIGHT) || in.pressed(KeyEvent.VK_D)) menuSel = (menuSel + 1) % 7;
            if (in.pressed(KeyEvent.VK_ENTER) || in.pressed(KeyEvent.VK_F)) {
                player.select(menuSel);
                Item it = player.getSelectedItem();
                if (it != null) it.use(this);
            }
        } else if (st == State.JOURNAL) {
            if (in.pressed(KeyEvent.VK_J) || in.pressed(KeyEvent.VK_ESCAPE) || in.pressed(KeyEvent.VK_ENTER)
                    || in.pressed(KeyEvent.VK_F) || in.pressed(KeyEvent.VK_E)) { states.pop(); shownNote = null; in.clearEdges(); }
        }
        in.takeDX(); in.takeDY(); in.takeClick(); in.takeWheel();
    }

    private void findTarget() {
        target = null;
        double best = 1e9;
        double ang = player.getAngle();
        for (GameObject o : objects) {
            if (!(o instanceof Interactable) || !o.visible()) continue;
            Interactable it = (Interactable) o;
            if (!it.canInteract(this)) continue;
            double d = player.distanceTo(o.getX(), o.getY());
            if (d > 1.9) continue;
            double a = Math.atan2(o.getY() - player.getY(), o.getX() - player.getX()) - ang;
            while (a > Math.PI) a -= 2 * Math.PI;
            while (a < -Math.PI) a += 2 * Math.PI;
            if (Math.abs(a) > 0.65 && d > 0.5) continue;
            if (!world.isSolid((int) o.getX(), (int) o.getY())
                    && !world.lineOfSight(player.getX(), player.getY(), o.getX(), o.getY())) continue;
            double score = d + Math.abs(a) * 1.5;
            if (score < best) { best = score; target = it; }
        }
    }

    public void shutdown() { sound.stopAmbient(); }
}
