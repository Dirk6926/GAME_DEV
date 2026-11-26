/**
 * ONE line somebody says. Immutable: it never changes after it is made.
 * Use the text tokens {name} {he} {him} {his} {hers} (capital first letter = capital in the text,
 * like {He}). They are swapped for the player's name and pronouns when the talk starts.
 */
public class DialogueLine {
    private final String speaker;
    private final String text;
    private final boolean fromPlayer;

    public DialogueLine(String speaker, String text, boolean fromPlayer) {
        this.speaker = speaker;
        this.text = text;
        this.fromPlayer = fromPlayer;
    }

    /** A line said by somebody else, e.g. DialogueLine.say("Kyla", "Hey, what's up?") */
    public static DialogueLine say(String speaker, String text) { return new DialogueLine(speaker, text, false); }

    /** A line said by the player. The speaker label automatically becomes the player's name. */
    public static DialogueLine player(String text) { return new DialogueLine("{name}", text, true); }

    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public boolean isFromPlayer() { return fromPlayer; }

    /** Copy of this line with the tokens replaced by the player's name and pronouns. */
    public DialogueLine filled(CharacterProfile profile) {
        return new DialogueLine(profile.fill(speaker), profile.fill(text), fromPlayer);
    }
}