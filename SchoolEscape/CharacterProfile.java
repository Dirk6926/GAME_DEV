import java.awt.Color;

/** The look and name of the player's character (ENCAPSULATION: private fields, safe setters). */
public class CharacterProfile {
    public static final Color[] SKIN = {
        new Color(247, 218, 190), new Color(224, 188, 160), new Color(190, 142, 104), new Color(141, 96, 66), new Color(96, 62, 44)};
    public static final String[] SKIN_NAMES = {"Fair", "Light", "Tan", "Brown", "Deep"};
    public static final Color[] HAIR = {
        new Color(34, 24, 20), new Color(92, 58, 34), new Color(190, 150, 70), new Color(170, 52, 36),
        new Color(200, 200, 205), new Color(60, 90, 200), new Color(200, 70, 160)};
    public static final String[] HAIR_NAMES = {"Black", "Brown", "Blond", "Red", "Silver", "Blue", "Pink"};
    public static final Color[] SHIRT = {
        new Color(60, 90, 140), new Color(170, 40, 44), new Color(50, 120, 80), new Color(210, 160, 40),
        new Color(110, 70, 150), new Color(40, 40, 46), new Color(220, 220, 225)};
    public static final String[] SHIRT_NAMES = {"Navy", "Red", "Green", "Yellow", "Purple", "Black", "White"};
    public static final String[] STYLE_NAMES = {"Short", "Long", "Spiky", "Cap"};
    public static final String[] PRESET_NAMES = {"Dirk", "Mika", "Alex", "Custom"};

    private String name = "Dirk";
    private int preset, skin = 1, style, hair, shirt;
    private boolean glasses;

    public String getName() { return name.isBlank() ? "Dirk" : name.trim(); }
    public String getRawName() { return name; }
    public void setName(String n) { name = n.length() > 12 ? n.substring(0, 12) : n; }
    public int getPreset() { return preset; }
    public int getSkin() { return skin; }
    public int getStyle() { return style; }
    public int getHair() { return hair; }
    public int getShirt() { return shirt; }
    public boolean hasGlasses() { return glasses; }
    public Color skinColor() { return SKIN[skin]; }
    public Color hairColor() { return HAIR[hair]; }
    public Color shirtColor() { return SHIRT[shirt]; }

    public void setSkin(int i) { skin = wrap(i, SKIN.length); preset = 3; }
    public void setStyle(int i) { style = wrap(i, STYLE_NAMES.length); preset = 3; }
    public void setHair(int i) { hair = wrap(i, HAIR.length); preset = 3; }
    public void setShirt(int i) { shirt = wrap(i, SHIRT.length); preset = 3; }
    public void setGlasses(boolean g) { glasses = g; preset = 3; }

    /** Picks one of the ready-made characters (the last entry keeps your own changes). */
    public void setPreset(int i) {
        i = wrap(i, PRESET_NAMES.length);
        switch (i) {
            case 0: name = "Dirk"; skin = 1; style = 0; hair = 0; shirt = 0; glasses = false; break;
            case 1: name = "Mika"; skin = 0; style = 1; hair = 1; shirt = 1; glasses = true; break;
            case 2: name = "Alex"; skin = 3; style = 2; hair = 5; shirt = 2; glasses = false; break;
            default: break;
        }
        preset = i;
    }

    private static int wrap(int i, int n) { return ((i % n) + n) % n; }
}
