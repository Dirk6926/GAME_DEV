/**
 * A classmate. INHERITANCE: it is a GameObject (it has a position and a picture in the world)
 * and it implements Interactable, so pressing F / E next to it starts its dialogue.
 */
public class Student extends GameObject implements Interactable {
    private static final double HEIGHT = 1.5;

    private final String name;
    private final Sprite sprite;
    private final DialogueLine[] script;
    private boolean talked;
    private double phase;

    public Student(String name, double x, double y, Sprite sprite, DialogueLine... script) {
        super(x, y);
        this.name = name;
        this.sprite = sprite;
        this.script = script;
    }

    public String getName() { return name; }
    public boolean hasTalked() { return talked; }

    @Override public String prompt() { return "Talk to " + name; }
    @Override public boolean canInteract(Game g) { return !g.isTalking(); }

    @Override
    public void interact(Game g) {
        talked = true;
        g.sfx("click");
        g.startDialogue(new Dialogue(script));
    }

    @Override public void update(Game g, double dt) { phase += dt; }

    @Override public Sprite sprite() { return sprite; }
    /** EFFECT: IDLE BREATHING - the student grows/shrinks a hair so they do not look frozen. */
    @Override public double height() { return HEIGHT + 0.012 * Math.sin(phase * 2.2); }
    @Override public double solidRadius() { return 0.3; }
}