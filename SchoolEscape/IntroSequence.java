import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The opening story, start to finish. Game owns one of these and calls update() every frame.
 *
 *   CLASSROOM  bright classroom, lights on, 3 classmates. The player walks around and talks to them.
 *   BLACKOUT   after the last chat: every light dies, the screen goes black (a few seconds).
 *              Hidden under the black we swap the room: classmates gone, furniture messy, lights flicker.
 *   FADE_IN    the screen fades back in on the empty, creepy classroom + a caption line.
 *   DONE       normal game. The Stalker is switched on and the creepy ambient sound starts.
 *
 * Everything you are likely to tweak is in the SETTINGS block right below.
 */
public class IntroSequence {

    /* ======================= SETTINGS (edit me) ======================= */

    /** false = skip the whole intro (handy while you test other parts of the game). */
    public static final boolean ENABLED = true;

    // -- where the player stands (x, y, facing angle in radians; Math.PI = facing west, toward the blackboard)
    private static final double[] INTRO_SPAWN = {23.9, 33.4, Math.PI};   // when the intro starts
    private static final double[] WAKE_SPAWN  = {20.6, 33.4, Math.PI};   // after the blackout (EFFECT: "transported" spot)

    // -- timing, in seconds
    private static final double TALK_DONE_DELAY   = 1.2;    // pause after the LAST chat before the lights die
    private static final double BLACKOUT_FADE_OUT = 0.12;   // EFFECT: how quickly the screen snaps to black (smaller = more sudden)
    private static final double BLACKOUT_HOLD     = 1.5;    // EFFECT: how long the screen stays fully black
    private static final double FADE_IN_SECONDS   = 1.6;    // EFFECT: how long the fade back in takes
    private static final double CAPTION_SECONDS   = 6.5;    // how long the wake-up caption stays on screen

    // -- the creepy classroom's ceiling light: ON, OFF or FLICKER
    private static final Light.Mode HAUNTED_ROOM_LIGHT = Light.Mode.FLICKER;

    // -- text. Tokens: {name} {he} {him} {his} {hers}  ({He} = capital letter)
    private static final String WAKE_CAPTION =
            "{name} was sure {he} had just been talking to {his} classmates. Now the room is silent, and {he} is alone.";
    private static final String OBJECTIVE_TALK = "Talk to your classmates";
    private static final String[] WAKE_THOUGHTS = {
            "Dirk: Hello...? Where did everybody go?",
            "Dirk: Everyone is gone... I need to find a way out."
    };

    // -- the classroom = Room 403 in World.java (tile coordinates, both corners included)
    private static final int ROOM_X0 = 17, ROOM_Y0 = 31, ROOM_X1 = 24, ROOM_Y1 = 37;
    // -- the blackboard: a big picture hung on the west wall (the wall surface is at x = 17.0, so keep x a bit above it)
    private static final double[] BOARD_POS = {17.08, 34.5};
    private static final double BOARD_HEIGHT = 1.05, BOARD_LIFT = 0.55;
    private static final String BOARD_TEXT = "TEST TODAY";            // chalk text while the room is normal
    private static final String BOARD_TEXT_HAUNTED = "404  404  404";  // chalk text after the blackout
    // -- desks: every column x every row. Chairs are put just behind each desk.
    private static final double[] DESK_COLUMNS = {18.6, 22.0};
    private static final double[] DESK_ROWS = {32.3, 34.5, 36.7};

    /* ======================= the classmates (edit me) ======================= */
    private List<Student> buildStudents() {
        List<Student> list = new ArrayList<>();

        // Student 1: tired, studying. Has a 2-line talk (his line, then the player's reply).
        list.add(new Student("Paolo", 20.5, 33.8,
                ClassroomSprites.student(new ClassroomSprites.Look(
                        new Color(224, 188, 160), new Color(34, 24, 20), new Color(70, 110, 90), new Color(48, 56, 90), 0, true, true)),
                DialogueLine.say("Paolo", "I'm exhausted... but I still have to study for our test later."),
                DialogueLine.player("What?! What test?")));

        // Student 2
        list.add(new Student("Kyla", 20.5, 36.0,
                ClassroomSprites.student(new ClassroomSprites.Look(
                        new Color(247, 218, 190), new Color(92, 58, 34), new Color(190, 70, 90), new Color(48, 56, 90), 2, false, false)),
                DialogueLine.say("Kyla", "Hey, what's up?")));

        // Student 3
        list.add(new Student("Miguel", 20.5, 32.8,
                ClassroomSprites.student(new ClassroomSprites.Look(
                        new Color(190, 142, 104), new Color(40, 40, 46), new Color(210, 160, 40), new Color(48, 56, 90), 3, false, false)),
                DialogueLine.say("Miguel", "Do you wanna go to the canteen with me?")));

        // TIP: to add a reply for the player, add:  DialogueLine.player("your line here")
        // TIP: to add a 4th classmate, copy one block above and change the name, position and lines.
        return list;
    }

    /* ======================= internals ======================= */
    public enum Stage { CLASSROOM, BLACKOUT, FADE_IN, DONE }

    private final Game game;
    private Stage stage = Stage.DONE;
    private double timer, countdown, captionClock = 999;
    private boolean swapped;
    private int lastTalkedCount = -1;
    private String savedObjective = "";

    private final List<Student> students = new ArrayList<>();
    private final List<GameObject> brightProps = new ArrayList<>();   // desks/chairs/barrier of the bright classroom
    private final Map<Light, Light.Mode> originalModes = new HashMap<>();

    public IntroSequence(Game game) { this.game = game; }

    /* ------------------------------------------------------------ queries used by Game / DialogueBox */
    public Stage getStage() { return stage; }
    /** True until the intro is over (the Stalker and the creepy sounds wait for this). */
    public boolean isActive() { return stage != Stage.DONE; }
    /** True while the player must not move (during the blackout). */
    public boolean freezesPlayer() { return stage == Stage.BLACKOUT; }

    /** 0 = no black screen, 1 = fully black. */
    public double blackAlpha() {
        if (stage == Stage.BLACKOUT) return Math.min(1, timer / BLACKOUT_FADE_OUT);
        if (stage == Stage.FADE_IN) {
            double k = Math.min(1, timer / FADE_IN_SECONDS);
            return 1 - k * k * (3 - 2 * k);                  // smooth 1 -> 0
        }
        return 0;
    }

    /** The wake-up caption (empty when it should not be shown). */
    public String caption() {
        return captionClock < CAPTION_SECONDS ? game.profile().fill(WAKE_CAPTION) : "";
    }

    public double captionAlpha() {
        if (captionClock >= CAPTION_SECONDS) return 0;
        return Math.min(1, Math.min(captionClock / 1.0, (CAPTION_SECONDS - captionClock) / 1.0));   // 1 s fade in / out
    }

    /* ------------------------------------------------------------ setup */
    private Furniture blackboard(boolean haunted) {
        return new Furniture(BOARD_POS[0], BOARD_POS[1],
                ClassroomSprites.blackboard(haunted ? BOARD_TEXT_HAUNTED : BOARD_TEXT, haunted), BOARD_HEIGHT, 0, BOARD_LIFT);
    }

    /** Starts the intro: bright classroom, students, player at INTRO_SPAWN. */
    public void begin() {
        Furniture board = blackboard(false);
        brightProps.add(board);
        game.objects().add(board);
        for (Light l : game.world().lights()) { originalModes.put(l, l.getMode()); l.setMode(Light.Mode.ON); }   // "the world is still bright"

        game.world().setTile(20, 30, Tile.EMPTY);
        addBrightFurniture();
        students.addAll(buildStudents());
        game.objects().addAll(students);

        game.player().teleport(INTRO_SPAWN[0], INTRO_SPAWN[1], INTRO_SPAWN[2]);
        savedObjective = game.objective();
        stage = Stage.CLASSROOM;
    }

    /** No intro (disabled, or the player already saw it): go straight to the creepy classroom. */
    public void skip() {
        for (Light l : game.world().lights()) originalModes.put(l, l.getMode());
        addHauntedFurniture();
        applyHauntedLights();
        stage = Stage.DONE;
    }

    private void addBrightFurniture() {
        Sprite desk = ClassroomSprites.desk(false), chair = ClassroomSprites.chair(false);
        for (double cx : DESK_COLUMNS)
            for (double ry : DESK_ROWS) {
                Furniture d = new Furniture(cx, ry, desk, 0.78, 0.4);
                Furniture c = new Furniture(cx + 0.75, ry, chair, 0.88, 0.2);
                brightProps.add(d); brightProps.add(c);
                game.objects().add(d); game.objects().add(c);
            }
    }

    /** Same desks, but dusty, crooked, with a few chairs knocked over. Nobody has been here for years. */
    private void addHauntedFurniture() {
        game.objects().add(blackboard(true));
        Sprite desk = ClassroomSprites.desk(true), tipped = ClassroomSprites.deskTipped();
        Sprite chair = ClassroomSprites.chair(true), fallen = ClassroomSprites.chairFallen();
        Random r = new Random(404);                          // same "random" mess every time
        for (double cx : DESK_COLUMNS)
            for (double ry : DESK_ROWS) {
                double jx = (r.nextDouble() - 0.5) * 0.3, jy = (r.nextDouble() - 0.5) * 0.3;
                boolean crooked = r.nextInt(4) == 0, knocked = r.nextInt(3) == 0;
                game.objects().add(crooked ? new Furniture(cx + jx, ry + jy, tipped, 0.9, 0.4)
                                           : new Furniture(cx + jx, ry + jy, desk, 0.78, 0.4));
                game.objects().add(knocked ? new Furniture(cx + 0.85 + jx, ry + 0.2 + jy, fallen, 0.5, 0.15)
                                           : new Furniture(cx + 0.75 + jx, ry + jy, chair, 0.88, 0.2));
            }
    }

    private boolean inClassroom(Light l) {
        return l.getX() >= ROOM_X0 && l.getX() < ROOM_X1 + 1 && l.getY() >= ROOM_Y0 && l.getY() < ROOM_Y1 + 1;
    }

    /** Lights back to how World.java made them, except the classroom's own light. */
    private void applyHauntedLights() {
        for (Light l : game.world().lights()) {
            Light.Mode m = originalModes.getOrDefault(l, l.getMode());
            l.setMode(inClassroom(l) ? HAUNTED_ROOM_LIGHT : m);
        }
    }

    /* ------------------------------------------------------------ the loop */
    public void update(Game g, double dt) {
        switch (stage) {
            case CLASSROOM -> {
                int talked = 0;
                for (Student s : students) if (s.hasTalked()) talked++;
                if (talked != lastTalkedCount) {                       // keep the objective panel up to date
                    lastTalkedCount = talked;
                    g.setObjective(OBJECTIVE_TALK + " (" + talked + "/" + students.size() + ")");
                }
                if (talked == students.size() && !g.isTalking()) {
                    countdown += dt;
                    if (countdown >= TALK_DONE_DELAY) startBlackout(g);
                } else countdown = 0;
            }
            case BLACKOUT -> {
                timer += dt;
                if (!swapped && timer >= BLACKOUT_FADE_OUT + 0.1) { swapRoom(g); swapped = true; }   // hidden under the black
                if (timer >= BLACKOUT_FADE_OUT + BLACKOUT_HOLD) { stage = Stage.FADE_IN; timer = 0; captionClock = 0; g.sfx("sting"); }
            }
            case FADE_IN -> {
                timer += dt;
                captionClock += dt;
                if (timer >= FADE_IN_SECONDS) finish(g);
            }
            case DONE -> captionClock += dt;
        }
    }

    /** EFFECT: BLACKOUT - every light dies at once and the screen snaps to black. */
    private void startBlackout(Game g) {
        stage = Stage.BLACKOUT;
        timer = 0;
        for (Light l : g.world().lights()) l.setMode(Light.Mode.OFF);
        g.sfx("click");
    }

    /** Runs while the screen is black: classmates vanish, the room turns creepy, the player is moved. */
    private void swapRoom(Game g) {
        g.objects().removeAll(students);
        g.objects().removeAll(brightProps);
        students.clear();
        brightProps.clear();
        addHauntedFurniture();
        applyHauntedLights();
        g.player().teleport(WAKE_SPAWN[0], WAKE_SPAWN[1], WAKE_SPAWN[2]);
    }

    private void finish(Game g) {
        stage = Stage.DONE;
        g.setObjective(savedObjective);
        g.objects().add(g.stalker());                 // the Stalker only exists once the nightmare starts
        g.startAmbient();
        g.profile().setIntroSeen(true);
        for (String t : WAKE_THOUGHTS) g.say(t);
    }
}
