import java.awt.Color;

/**
 * The player's character: name + gender, and pronoun helpers.
 *
 * REPLACE or MERGE: if your current CharacterProfile.java only stores a name, replace it with this file.
 * If it already has other things (appearance, stats...), keep those and copy in everything marked NEW.
 *
 * ------------------------------------------------------------------
 * HOW TO USE PRONOUNS IN ANY DIALOGUE / THOUGHT TEXT
 * Write these tokens in your strings and fill() swaps them automatically:
 *
 *   token          male       female
 *   {name}         (typed name)
 *   {they}         he         she
 *   {them}         him        her
 *   {their}        his        her
 *   {theirs}       his        hers
 *   {themself}     himself    herself
 *   Capitalised versions ({They}, {Their}, ...) give "He", "His", "She"...
 *
 *   Example: "{name} rubs {their} eyes. {They} must have fallen asleep."
 *        ->  "Ana rubs her eyes. She must have fallen asleep."
 * ------------------------------------------------------------------
 */
public class CharacterProfile {
    private String name = "Dirk";          // default if nobody types anything (your old placeholder name)
    private Gender gender = Gender.MALE;   // NEW

    // Appearance variables (for compatibility with existing code)
    private Color skinColor = new Color(255, 224, 189); // default skin color
    private Color hairColor = Color.BLACK; // default hair color
    private Color eyeColor = Color.BLUE;  // default eye color
    private String hairStyle = "Short";    // default hair style

    public CharacterProfile() { }

    public CharacterProfile(String name, Gender gender) {   // NEW
        setName(name);
        setGender(gender);
    }

    /* ------------------------------------------------------------ name */
    public String getName() { return name; }

    public void setName(String name) {                       // NEW
        if (name != null && !name.trim().isEmpty()) this.name = name.trim();
    }

    /* ------------------------------------------------------------ gender (NEW) */
    public Gender getGender() { return gender; }

    public void setGender(Gender gender) { if (gender != null) this.gender = gender; }

    public boolean isFemale() { return gender == Gender.FEMALE; }

    /* ------------------------------------------------------------ pronouns (NEW) */
    public String they()     { return isFemale() ? "she" : "he"; }
    public String them()     { return isFemale() ? "her" : "him"; }
    public String their()    { return isFemale() ? "her" : "his"; }
    public String theirs()   { return isFemale() ? "hers" : "his"; }
    public String themself() { return isFemale() ? "herself" : "himself"; }

    /** Replaces {name}, {they}, {them}, {their}, {theirs}, {themself} (and capitalised versions) in text. */
    public String fill(String text) {
        if (text == null) return "";
        String[][] tokens = {
            {"themself", themself()},   // longer tokens first so {them} doesn't eat {themself}
            {"theirs", theirs()},
            {"their", their()},
            {"them", them()},
            {"they", they()},
        };
        String out = text;
        for (String[] t : tokens) {
            out = out.replace("{" + t[0] + "}", t[1]);
            out = out.replace("{" + cap(t[0]) + "}", cap(t[1]));
        }
        return out.replace("{name}", name);   // name last, in case the name itself has odd characters
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* ------------------------------------------------------------ appearance (for compatibility) */
    public Color getSkinColor() { return skinColor; }
    public void setSkinColor(Color color) { if (color != null) skinColor = color; }

    public Color getHairColor() { return hairColor; }
    public void setHairColor(Color color) { if (color != null) hairColor = color; }

    public Color getEyeColor() { return eyeColor; }
    public void setEyeColor(Color color) { if (color != null) eyeColor = color; }

    public String getHairStyle() { return hairStyle; }
    public void setHairStyle(String style) { if (style != null && !style.trim().isEmpty()) hairStyle = style.trim(); }

    /* ------------------------------------------------------------ legacy HUD appearance API */
    public Color hairColor() { return hairColor; }
    public Color skinColor() { return skinColor; }
    public Color shirtColor() { return new Color(100, 130, 170); }

    public int getStyle() { return 0; }
    public boolean hasGlasses() { return false; }
}
