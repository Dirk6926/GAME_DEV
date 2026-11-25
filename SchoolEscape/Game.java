import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The game controller: owns the world, the player, every object and the state stack.
 *
 * ================================================================== GAME FLOW
 * The top of the state stack is "what is happening right now":
 *
 *   NAME_ENTRY  player types a name and picks a gender
 *       |  (confirm -> pop)
 *   INTRO       bright classroom: walk around, talk to the 3 classmates
 *       |  DIALOGUE gets pushed on top while a conversation box is open
 *       |  (all 3 talked to -> short pause -> lights flicker)
 *   BLACKOUT    pitch black for BLACKOUT_TIME seconds; classmates removed
 *       |
 *   PLAYING     the original horror game, in the empty creepy classroom
 *
 * Search this file for "=====" to jump between sections.
 * Anything marked NEW was added for the intro; everything else is your original code.
 */
public class Game {
    public enum State { NAME_ENTRY, INTRO, DIALOGUE, BLACKOUT,                // NEW
                        PLAYING, PAUSED, INVENTORY, JOURNAL, DEAD, WON }

    // ================================================================== INTRO SETTINGS (tweak me)  NEW
    /** true = jump straight into the horror part (handy while testing other stuff). */
    private static final boolean SKIP_INTRO = false;

    /** Where the player starts in the bright classroom.
     *  CHANGE THESE to the middle of a classroom in your map (map tile units, same as Player/Stalker). */
    private static final double INTRO_SPAWN_X = 21.0;
    private static final double INTRO_SPAWN_Y = 34.5;
    private static final double INTRO_SPAWN_ANGLE = -Math.PI / 2;   // which way the player faces

    /** Where the player "wakes up" after the blackout. Same spot by default, so it feels like the
     *  classroom itself changed. Point it at a different room if you'd rather "transport" them. */
    private static final double CREEPY_SPAWN_X = INTRO_SPAWN_X;
    private static final double CREEPY_SPAWN_Y = INTRO_SPAWN_Y;
    private static final double CREEPY_SPAWN_ANGLE = INTRO_SPAWN_ANGLE;

    /** Invisible fence (in tiles) around INTRO_SPAWN so the player can't leave the classroom during the intro. */
    private static final double INTRO_ROAM_RADIUS = 4.0;

    private static final double DELAY_BEFORE_FLICKER = 1.2;  // calm pause after the last conversation
    private static final double FLICKER_TIME = 0.8;          // lights stutter before dying (0 = no flicker)
    private static final double BLACKOUT_TIME = 1.5;         // seconds of total darkness
    private static final double TYPEWRITER_SPEED = 45;       // dialogue letters per second
    private static final int MAX_NAME_LENGTH = 12;

    private static final String INTRO_OBJECTIVE = "Talk to your classmates.";
    private static final String MAIN_OBJECTIVE = "Find the key to Room 404 and escape the school.";

    // ================================================================== ORIGINAL FIELDS
    private final World world;
    private Player player;                 // (no longer final: replaced with a fresh Player after the blackout)
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
    private String objective = INTRO_OBJECTIVE;
    private Interactable target;
    private Item shownNote;
    private int stage;
    private int menuSel;

    private final CharacterProfile profile;

    // ================================================================== INTRO FIELDS  NEW
    private boolean introActive = true;               // true until the creepy part begins
    private final List<Student> classmates = new ArrayList<>();
    private double introTimer = -1;                   // counts down to the blackout (-1 = not started)
    private boolean flickerStarted;
    private double blackoutTimer;
    private double uiTime;                            // always ticks (caret blink etc.), even in menus

    // name entry screen
    private final StringBuilder nameBuffer = new StringBuilder();
    private Gender nameEntryGender = Gender.MALE;
    private int nameEntryField;                       // 0 = name box selected, 1 = gender selected
    private String nameEntryError = "";

    // dialogue box
    private final MyQueue<DialogueLine> dialogueQueue = new MyQueue<>();   // QUEUE: lines of the conversation
    private String dialogueSpeaker = "";
    private String dialogueFull = "";
    private double dialogueChars;                     // how many letters are revealed (typewriter effect)

    // ================================================================== CONSTRUCTION
    public Game(Input input) { this(input, new CharacterProfile()); }

    public CharacterProfile profile() { return profile; }

    public Game(Input input, CharacterProfile profile) {
        this.input = input;
        this.profile = profile;
        Textures.init();
        world = World.build();
        player = new Player(INTRO_SPAWN_X, INTRO_SPAWN_Y, INTRO_SPAWN_ANGLE);
        stalker = new Stalker();
        // NOTE: the stalker is NOT added to `objects` here any more - it arrives in endIntro().
        objects.addAll(world.initialObjects());
        spawnClassmates();
        input.takeTyped();               // throw away anything typed on the title screen

        if (SKIP_INTRO) {
            clearIntroScene();
            states.push(State.BLACKOUT); // endIntro() swaps BLACKOUT for PLAYING
            endIntro();
        } else {
            // Stack: INTRO underneath, NAME_ENTRY on top. Confirming the name pops it and reveals INTRO.
            states.push(State.INTRO);
            states.push(State.NAME_ENTRY);
        }
        // (sound.startAmbient() moved to endIntro() so the bright classroom isn't scary yet)
    }

    // ================================================================== THE CLASSMATES (edit dialogue here)  NEW
    /**
     * Positions are offsets from INTRO_SPAWN. With the default angle (-PI/2) the player faces -Y,
     * so "INTRO_SPAWN_Y - 2" is in front of them. If a student is stuck in a wall you'll get a
     * warning in the console telling you which one to move.
     */
    private void spawnClassmates() {
        addClassmate(new Student("Student 1", INTRO_SPAWN_X - 1.5, INTRO_SPAWN_Y - 2.0, new Color(70, 110, 180),
                new DialogueLine("Student 1", "I'm exhausted... but I still have to study for our test later."),
                new DialogueLine(DialogueLine.PLAYER, "What?! What test?")));

        addClassmate(new Student("Student 2", INTRO_SPAWN_X, INTRO_SPAWN_Y - 2.5, new Color(180, 70, 70),
                new DialogueLine("Student 2", "Hey, what's up?")));

        addClassmate(new Student("Student 3", INTRO_SPAWN_X + 1.5, INTRO_SPAWN_Y - 2.0, new Color(80, 150, 90),
                new DialogueLine("Student 3", "Do you wanna go to the canteen with me?")));

        // Add a 4th student by copying one of the blocks above. The blackout waits for ALL of them.
    }

    private void addClassmate(Student s) {
        if (world.isSolid((int) s.getX(), (int) s.getY()))
            System.err.println("[Intro] " + s.getName() + " is inside a wall at (" + s.getX() + ", " + s.getY()
                    + ") - move them in Game.spawnClassmates()");
        classmates.add(s);
        objects.add(s);
    }

    /** Lines shown right after the blackout, once the player is alone. */
    private DialogueLine[] afterBlackoutLines() {
        return new DialogueLine[] {
            new DialogueLine(DialogueLine.NARRATOR, "{name} blinks. The room is cold and silent."),
            new DialogueLine(DialogueLine.NARRATOR, "{Their} classmates are gone. Only the desks, the chairs and the blackboard remain."),
            new DialogueLine(DialogueLine.PLAYER, "Everyone is gone... I need to find a way out."),
        };
    }

    // ================================================================== GETTERS (original)
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

    // ================================================================== GETTERS FOR YOUR UI / RENDERER  NEW
    /** True during NAME_ENTRY, INTRO, DIALOGUE-in-intro and BLACKOUT. Use it to hide the horror HUD. */
    public boolean inIntro() { return introActive; }

    /** Renderer: when true, draw the world fully lit (ignore darkness/fog). Flickers just before the blackout. */
    public boolean introLightsOn() {
        if (!introActive || state() == State.BLACKOUT) return false;
        if (flickerStarted) return Math.sin(time * 41.0) + Math.sin(time * 17.3) > 0.4;   // random-looking stutter
        return true;
    }

    /** Renderer: when true, paint the whole screen black. */
    public boolean isBlackout() { return state() == State.BLACKOUT; }

    // name entry screen
    public String nameEntryText() { return nameBuffer.toString(); }
    public Gender nameEntryGender() { return nameEntryGender; }
    public int nameEntryField() { return nameEntryField; }              // 0 = name, 1 = gender
    public String nameEntryError() { return nameEntryError; }
    public boolean nameEntryCaretVisible() { return (uiTime % 1.0) < 0.5; }
    public int maxNameLength() { return MAX_NAME_LENGTH; }

    // dialogue box
    public String dialogueSpeaker() { return dialogueSpeaker; }          // "" = narration, no name tag
    public String dialogueText() {                                       // grows letter by letter
        return dialogueFull.substring(0, (int) Math.min(dialogueFull.length(), dialogueChars));
    }
    public boolean dialogueTyping() { return dialogueChars < dialogueFull.length(); }

    // ================================================================== SERVICES FOR OBJECTS
    /** Queue a thought line. Supports "Dirk:" at the start (old style) and {name}/{they}/{their} tokens. */
    public void say(String s) {
        thoughts.enqueue(profile.fill(s.startsWith("Dirk:") ? profile.getName() + s.substring(4) : s));
    }
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
        // NEW: invisible fence that keeps the player inside the classroom during the intro.
        // (Works if your Player checks blockedByObject() when moving, which its signature suggests.)
        if (introActive && Math.hypot(x - INTRO_SPAWN_X, y - INTRO_SPAWN_Y) > INTRO_ROAM_RADIUS) return true;
        for (GameObject o : objects) {
            double s = o.solidRadius();
            if (s > 0 && o.distanceTo(x, y) < s + r) return true;
        }
        return false;
    }

    private void toast(String s, double secs) { toastText = s; toast = secs; }

    public void restart() { /* handled by Main creating a new Game */ }

    // ================================================================== MAIN LOOP
    public void update(double dt) {
        Input in = input;
        String typed = in.takeTyped();     // NEW: letters typed this frame (needs the Input.java addition)
        uiTime += dt;
        State st = state();

        if (st == State.NAME_ENTRY) {               // NEW
            updateNameEntry(in, typed);
        } else if (st == State.INTRO) {             // NEW
            updateIntro(in, dt);
        } else if (st == State.DIALOGUE) {          // NEW
            updateDialogue(in, dt);
        } else if (st == State.BLACKOUT) {          // NEW
            updateBlackout(in, dt);
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
                if (it != null) it.use(this);   // consumables remove themselves
            }
            for (GameObject o : objects) o.update(this, dt);
            flushObjectChanges();

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

    private void flushObjectChanges() {
        objects.removeAll(toRemove); toRemove.clear();
        objects.addAll(toAdd); toAdd.clear();
    }

    /** Throw away mouse movement/clicks so the camera doesn't jump when you leave a menu. */
    private void drainMouse(Input in) { in.takeDX(); in.takeDY(); in.takeClick(); in.takeWheel(); }

    // ================================================================== NAME ENTRY SCREEN  NEW
    /*
     * Keyboard controls (your custom UI can draw whatever it wants on top of these):
     *   type letters / BACKSPACE   edit the name
     *   ENTER / DOWN / TAB         go to the gender choice
     *   LEFT / RIGHT / A / D       switch Male <-> Female
     *   UP / TAB                   back to the name box
     *   ENTER (on gender)          confirm and start the intro
     *
     * If your UI uses Swing buttons / a JTextField instead, just call submitCharacter(name, gender).
     */
    private void updateNameEntry(Input in, String typed) {
        if (nameEntryField == 0) {
            for (char c : typed.toCharArray()) {
                boolean allowed = Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '.';
                if (allowed && nameBuffer.length() < MAX_NAME_LENGTH) { nameBuffer.append(c); nameEntryError = ""; }
            }
            if (in.pressed(KeyEvent.VK_BACK_SPACE) && nameBuffer.length() > 0) nameBuffer.setLength(nameBuffer.length() - 1);
            if (in.pressed(KeyEvent.VK_ENTER) || in.pressed(KeyEvent.VK_DOWN) || in.pressed(KeyEvent.VK_TAB)) nameEntryField = 1;
        } else {
            if (in.pressed(KeyEvent.VK_LEFT) || in.pressed(KeyEvent.VK_RIGHT)
                    || in.pressed(KeyEvent.VK_A) || in.pressed(KeyEvent.VK_D)) nameEntryGender = nameEntryGender.other();
            if (in.pressed(KeyEvent.VK_UP) || in.pressed(KeyEvent.VK_TAB)) nameEntryField = 0;
            else if (in.pressed(KeyEvent.VK_ENTER)) confirmNameEntry();
        }
        drainMouse(in);
    }

    /** For mouse-driven UI: select a gender button. */
    public void setNameEntryGender(Gender g) { if (g != null) nameEntryGender = g; }

    /** For mouse-driven UI: select the name box (0) or gender (1). */
    public void setNameEntryField(int field) { nameEntryField = field == 1 ? 1 : 0; }

    /** For Swing-style UI: hand over the name + gender directly. Returns false if the name was empty. */
    public boolean submitCharacter(String name, Gender g) {
        nameBuffer.setLength(0);
        if (name != null) nameBuffer.append(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        setNameEntryGender(g);
        return confirmNameEntry();
    }

    /** Saves name + gender into the profile and starts the intro. Returns false if the name is empty. */
    public boolean confirmNameEntry() {
        if (state() != State.NAME_ENTRY) return false;
        String name = nameBuffer.toString().trim();
        if (name.isEmpty()) { nameEntryError = "Please enter a name."; nameEntryField = 0; return false; }
        profile.setName(name);
        profile.setGender(nameEntryGender);
        states.pop();                    // NAME_ENTRY gone -> INTRO is on top now
        input.clearEdges();
        sfx("click");
        say("{name}: Ugh... did I fall asleep in class again?");   // first thought in the bright classroom
        return true;
    }

    // ================================================================== BRIGHT CLASSROOM  NEW
    private void updateIntro(Input in, double dt) {
        if (in.pressed(KeyEvent.VK_ESCAPE)) { states.push(State.PAUSED); menuSel = 0; in.clearEdges(); return; }
        time += dt;
        player.update(this, in, dt);             // walking + mouse look work like normal
        findTarget();                            // only students can be targeted during the intro
        if (target != null && (in.pressed(KeyEvent.VK_F) || in.pressed(KeyEvent.VK_E))) target.interact(this);
        in.takeClick(); in.takeWheel();          // no items / flashlight during the intro
        for (GameObject o : objects) o.update(this, dt);
        flushObjectChanges();

        // countdown that starts once every classmate has been talked to (see closeDialogue)
        if (introTimer >= 0) {
            introTimer -= dt;
            if (!flickerStarted && introTimer <= FLICKER_TIME) {
                flickerStarted = true;           // introLightsOn() starts stuttering
                sfx("click");                    // swap for a "power cut"/buzz sound if you have one
                disturbLights(player.getX(), player.getY(), 30, FLICKER_TIME + BLACKOUT_TIME);
            }
            if (introTimer <= 0) startBlackout();
        }
    }

    private boolean allClassmatesTalkedTo() {
        for (Student s : classmates) if (!s.hasTalkedTo()) return false;
        return true;
    }

    // ================================================================== BLACKOUT  NEW
    private void startBlackout() {
        states.pop();                            // remove INTRO
        states.push(State.BLACKOUT);
        blackoutTimer = BLACKOUT_TIME;
        sfx("click");
        clearIntroScene();                       // swap the scene while the screen is black
    }

    /** Removes the classmates and puts the player at the creepy spawn. */
    private void clearIntroScene() {
        objects.removeAll(classmates);
        classmates.clear();
        target = null;
        // A fresh Player is the simplest way to "teleport" without touching Player.java.
        // If you add a teleport(x, y, angle) method to Player later, you can call that instead.
        player = new Player(CREEPY_SPAWN_X, CREEPY_SPAWN_Y, CREEPY_SPAWN_ANGLE);
    }

    private void updateBlackout(Input in, double dt) {
        blackoutTimer -= dt;
        drainMouse(in);
        if (blackoutTimer <= 0) endIntro();
    }

    /** The horror game begins. */
    private void endIntro() {
        states.pop();                            // remove BLACKOUT
        states.push(State.PLAYING);
        introActive = false;
        objects.add(stalker);                    // the stalker only exists from now on
        objective = MAIN_OBJECTIVE;
        sound.startAmbient();
        startDialogue(afterBlackoutLines());     // DIALOGUE on top of PLAYING; stalker is frozen until it closes
    }

    // ================================================================== DIALOGUE BOX  NEW
    /*
     * Controls: E / F / ENTER / SPACE / click = finish the line, or go to the next one.
     *           ESC = skip the rest of the conversation.
     * The game world is frozen while the box is open.
     */

    /** Opens a conversation. Any object can call this (Student does). */
    public void startDialogue(DialogueLine... lines) {
        if (lines == null || lines.length == 0) return;
        for (DialogueLine l : lines) dialogueQueue.enqueue(l);
        if (state() != State.DIALOGUE) states.push(State.DIALOGUE);
        input.clearEdges();                      // so the F press that opened it doesn't skip line 1
        nextDialogueLine();
    }

    private void updateDialogue(Input in, double dt) {
        dialogueChars += dt * TYPEWRITER_SPEED;
        boolean click = in.takeClick();
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            while (!dialogueQueue.isEmpty()) dialogueQueue.dequeue();
            closeDialogue();
        } else if (click || in.pressed(KeyEvent.VK_E) || in.pressed(KeyEvent.VK_F)
                || in.pressed(KeyEvent.VK_ENTER) || in.pressed(KeyEvent.VK_SPACE)) {
            if (dialogueTyping()) dialogueChars = dialogueFull.length();   // first press: show the whole line
            else nextDialogueLine();                                        // second press: next line
        }
        drainMouse(in);
    }

    private void nextDialogueLine() {
        if (dialogueQueue.isEmpty()) { closeDialogue(); return; }
        DialogueLine l = dialogueQueue.dequeue();
        dialogueSpeaker = profile.fill(l.getSpeaker());   // "{name}" becomes the player's name
        dialogueFull = profile.fill(l.getText());
        dialogueChars = 0;
    }

    private void closeDialogue() {
        dialogueSpeaker = "";
        dialogueFull = "";
        if (state() == State.DIALOGUE) states.pop();
        input.clearEdges();
        // Last classmate done? Start the countdown to the blackout.
        if (introActive && introTimer < 0 && allClassmatesTalkedTo()) introTimer = DELAY_BEFORE_FLICKER + FLICKER_TIME;
    }

    // ================================================================== MENUS (original)
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
        drainMouse(in);
    }

    private void findTarget() {
        target = null;
        double best = 1e9;
        double ang = player.getAngle();
        for (GameObject o : objects) {
            if (!(o instanceof Interactable) || !o.visible()) continue;
            if (introActive && !(o instanceof Student)) continue;   // NEW: no picking up items during the intro
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
