/**
 * NEW FILE - one line in a conversation box.
 *
 * speaker = who is talking (shown above the text). Use:
 *     DialogueLine.PLAYER   -> shows the player's typed name
 *     DialogueLine.NARRATOR -> no name (narration / description)
 *     "Student 1"           -> any other character
 * text may contain pronoun tokens like {name}, {they}, {their} (see CharacterProfile).
 */
public class DialogueLine {
    public static final String PLAYER = "{name}";
    public static final String NARRATOR = "";

    private final String speaker;
    private final String text;

    public DialogueLine(String speaker, String text) {
        this.speaker = speaker == null ? NARRATOR : speaker;
        this.text = text == null ? "" : text;
    }

    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public boolean isNarration() { return speaker.isEmpty(); }
}
