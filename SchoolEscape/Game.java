import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** The game controller: owns the world, the player, every object and the state stack. */
public class Game {
    public enum State { NAME_ENTRY, INTRO, DIALOGUE, BLACKOUT,
                        PLAYING, PAUSED, INVENTORY, JOURNAL, DEAD, WON }

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
    private Item shownNote;
    private int stage;
    private int menuSel;

    private final IntroSequence intro;   // INTRO: the opening story (classroom -> blackout -> creepy classroom)
    private Dialogue dialogue;            // DIALOGUE: the conversation on screen right now (null = nobody is talking)

    private final CharacterProfile profile;

    public Game(Input input) { this(input, new CharacterProfile()); }

    public CharacterProfile profile() { return profile; }

    public Game(Input input, CharacterProfile profile) {
        this.input = input;
        this.profile = profile;
        Textures.init();
        world = World.build();
        player = new Player(21.0, 34.5, -Math.PI / 2);
        stalker = new Stalker();
        objects.addAll(world.initialObjects());
        states.push(State.PLAYING);

        // INTRO: play the opening story once. After a restart (or if IntroSequence.ENABLED is false) it is skipped.
        intro = new IntroSequence(this);
        if (IntroSequence.ENABLED && !profile.isIntroSeen()) {
            intro.begin();                         // the Stalker and the ambient sound start when the intro ends
        } else {
            intro.skip();
            objects.add(stalker);
            sound.startAmbient();
            say("Dirk: Everyone is gone... I need to find a way out.");
        }
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
    public Item shownNote() { return shownNote; }
    public double hurtFlash() { return hurtFlash; }
    public String toastText() { return toast > 0 ? toastText : ""; }
    public double toastAlpha() { return Math.min(1, toast); }
    public int menuSel() { return menuSel; }
    public Input input() { return input; }
    public IntroSequence intro() { return intro; }
    public Dialogue dialogue() { return dialogue; }
    public boolean isTalking() { return dialogue != null; }
    public boolean inIntro() { return intro != null && intro.isActive(); }
    public boolean introLightsOn() { return intro == null || !intro.isActive() || intro.getStage() == IntroSequence.Stage.CLASSROOM; }
    public boolean isBlackout() { return intro != null && intro.getStage() == IntroSequence.Stage.BLACKOUT; }

    public String nameEntryText() { return ""; }
    public Gender nameEntryGender() { return profile.getGender(); }
    public int nameEntryField() { return 0; }
    public String nameEntryError() { return ""; }
    public boolean nameEntryCaretVisible() { return false; }
    public void handleNameEntryMouse(int mx, int my, int w, int h) { }

    public String dialogueSpeaker() {
        if (dialogue == null || dialogue.current() == null) return "";
        return dialogue.current().getSpeaker();
    }
    public String dialogueText() { return dialogue == null ? "" : dialogue.visibleText(); }
    public boolean dialogueTyping() { return dialogue == null ? false : !dialogue.isFinished(); }

    /* ------------------------------------------------------------ services for objects */
    public void say(String s) {
        // "Dirk:" at the start becomes the player's name; {he} {his} {him} {hers} {name} become the right pronouns
        thoughts.enqueue(profile.fill(s.startsWith("Dirk:") ? profile.getName() + s.substring(4) : s));
    }

    /** DIALOGUE: opens a conversation. The player is frozen until every line has been read. */
    public void startDialogue(Dialogue d) { dialogue = d.filled(profile); }
    public void setObjective(String text) { objective = text; }
    public void startAmbient() { sound.startAmbient(); }
    public void shutdown() { sound.stopAmbient(); }
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
    public void showNote(Item n) { shownNote = n; states.push(State.JOURNAL); }

    public boolean flashlightOn() {
        Item it = player.getSelectedItem();
        return it != null && it.isFlashlight() && it.isOn();
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

        if (st == State.PLAYING) intro.update(this, dt);    // INTRO: timers, blackout, wake-up

        if (st == State.PLAYING && (dialogue != null || intro.freezesPlayer())) {
            frozen(in, dt);                                  // DIALOGUE / BLACKOUT: the player cannot move
        } else if (st == State.PLAYING) {
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
                    boolean wasFlash = it != null && it.isFlashlight();
                    it.use(this);
                    if (!wasFlash && (it != null && !it.isFlashlight())) { /* consumables remove themselves */ }
                }
            }
            for (GameObject o : objects) o.update(this, dt);
            objects.removeAll(toRemove); toRemove.clear();
            objects.addAll(toAdd); toAdd.clear();

            heartTimer -= dt;
            double d = stalker.distanceTo(player.getX(), player.getY());
            if (!intro.isActive() && d < 9 && heartTimer <= 0 && world.lineOfSight(stalker.getX(), stalker.getY(), player.getX(), player.getY())) {
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

    /** DIALOGUE / BLACKOUT: the world keeps its clock running but the player does not move. */
    private void frozen(Input in, double dt) {
        time += dt;
        if (dialogue != null) {
            dialogue.update(dt);
            boolean next = in.pressed(KeyEvent.VK_F) || in.pressed(KeyEvent.VK_E) || in.pressed(KeyEvent.VK_ENTER)
                    || in.pressed(KeyEvent.VK_SPACE) || in.takeClick();       // F / E / Enter / Space / mouse click = continue
            if (next) {
                dialogue.advance();
                if (dialogue.isFinished()) dialogue = null;
            }
        }
        for (GameObject o : objects) o.update(this, dt);     // idle animations keep running
        hurtFlash = Math.max(0, hurtFlash - dt * 1.6);
        in.takeDX(); in.takeDY(); in.takeWheel();            // ignore mouse-look and wheel while frozen
        in.clearEdges();
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
            double targetRange = intro.isActive() ? 3.5 : 1.9;
            if (d > targetRange) continue;
            double a = Math.atan2(o.getY() - player.getY(), o.getX() - player.getX()) - ang;
            while (a > Math.PI) a -= 2 * Math.PI;
            while (a < -Math.PI) a += 2 * Math.PI;
            if (Math.abs(a) > 0.65 && d > 0.5) continue;
            if (!world.isSolid((int) o.getX(), (int) o.getY())
                    && !world.lineOfSight(player.getX(), player.getY(), o.getX(), o.getY())) continue;
            double score = d + Math.abs(a) * 1.5;
            if (score < best) {
                best = score;
                target = it;
            }
        }
    }
}