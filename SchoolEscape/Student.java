import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * NEW FILE - a classmate in the bright intro classroom. Walk up, press F/E, and they talk.
 *
 * !! I haven't seen your GameObject.java / Interactable.java / Renderer yet, so the lines marked
 * !! "CHECK" are my best guess. If something doesn't compile, the error will point right at them.
 * !! Easiest fix: open another sprite object you already have (e.g. Stalker.java or a pickup Item)
 * !! and copy how it calls super(...) and how it hands its picture to the Renderer.
 */
public class Student extends GameObject implements Interactable {
    private final String name;
    private final DialogueLine[] lines;
    private final Sprite sprite;
    private boolean talkedTo;

    /**
     * @param name   shown in the dialogue box and the "Talk to ..." prompt
     * @param x,y    position in map tiles (same units as Player / Stalker)
     * @param shirt  shirt colour for the placeholder sprite, so the 3 students look different
     * @param lines  what happens when you talk to them, in order
     */
    public Student(String name, double x, double y, Color shirt, DialogueLine... lines) {
        super(x, y);                                   // CHECK: match your GameObject constructor
        this.name = name;
        this.lines = lines;
        this.sprite = toSprite(makePlaceholderSprite(shirt));    // swap for your real art later, e.g. Textures.get("student1")
    }

    /* ------------------------------------------------------------ talking */
    // CHECK: these two come from your Interactable interface (Game.java calls them already).
    public boolean canInteract(Game game) { return true; }

    public void interact(Game game) {
        talkedTo = true;               // Game counts these to know when to trigger the blackout
        game.startDialogue(lines);
    }

    /** CHECK: if your Interactable has a prompt/label method (e.g. "Press F to ..."), rename this to match. */
    public String prompt() { return "Talk to " + name; }

    public boolean hasTalkedTo() { return talkedTo; }
    public String getName() { return name; }

    /* ------------------------------------------------------------ world behaviour */
    /** Students block movement a little so you can't walk through them. Set to 0 to allow walking through. */
    public double solidRadius() { return 0.3; }

    public boolean visible() { return true; }

    public void update(Game game, double dt) { /* idle - add head bobbing / turning here if you like */ }

    /**
     * CHECK: rename to whatever method your Renderer calls to get an object's billboard picture
     * (look at how Stalker gives the Renderer its image).
     */
    public Sprite sprite() { return sprite; }

    /* ------------------------------------------------------------ placeholder art */
    /** Draws a simple student silhouette so you can test before you have real sprites. */
    private static BufferedImage makePlaceholderSprite(Color shirt) {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);   // transparent background
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(40, 40, 55));                      // trousers / skirt
        g.fillRect(24, 46, 7, 18);
        g.fillRect(33, 46, 7, 18);
        g.setColor(shirt);                                      // uniform top
        g.fillRoundRect(18, 26, 28, 24, 10, 10);
        g.setColor(Color.WHITE);                                // collar
        g.setStroke(new BasicStroke(2));
        g.drawLine(28, 27, 32, 32);
        g.drawLine(36, 27, 32, 32);
        g.setColor(new Color(235, 195, 160));                   // face
        g.fillOval(22, 6, 20, 22);
        g.setColor(new Color(35, 25, 20));                      // hair
        g.fillArc(21, 4, 22, 16, 0, 180);
        g.setColor(Color.BLACK);                                // eyes
        g.fillOval(27, 15, 3, 3);
        g.fillOval(35, 15, 3, 3);
        g.dispose();
        return img;
    }

    private static Sprite toSprite(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        return new Sprite(w, h, pixels);
    }
}
