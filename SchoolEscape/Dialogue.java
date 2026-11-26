/**
 * A conversation: a QUEUE of DialogueLines shown one at a time with a typewriter effect.
 * The front of the queue is the line on screen. advance() either finishes typing the line
 * or moves to the next one.
 */
public class Dialogue {
    /** EFFECT: TYPEWRITER SPEED - how many letters appear per second (bigger = faster). */
    private static final double CHARS_PER_SECOND = 45;

    private final MyQueue<DialogueLine> lines = new MyQueue<>();
    private double shown;          // how many letters of the current line are visible so far

    public Dialogue(DialogueLine... script) {
        for (DialogueLine line : script) lines.enqueue(line);
    }

    /** Same conversation, with {name}/{he}/{his}... replaced. Game does this when a talk starts. */
    public Dialogue filled(CharacterProfile profile) {
        DialogueLine[] copy = new DialogueLine[lines.size()];
        MyQueue<DialogueLine> temp = new MyQueue<>();
        int i = 0;
        while (!lines.isEmpty()) { DialogueLine l = lines.dequeue(); temp.enqueue(l); copy[i++] = l.filled(profile); }
        while (!temp.isEmpty()) lines.enqueue(temp.dequeue());      // put the original back
        return new Dialogue(copy);
    }

    public boolean isFinished() { return lines.isEmpty(); }
    public DialogueLine current() { return lines.isEmpty() ? null : lines.peek(); }

    public void update(double dt) { shown += dt * CHARS_PER_SECOND; }

    /** The part of the current line that should be visible right now. */
    public String visibleText() {
        if (lines.isEmpty()) return "";
        String t = lines.peek().getText();
        return t.substring(0, Math.min(t.length(), (int) shown));
    }

    public boolean isLineComplete() { return lines.isEmpty() || shown >= lines.peek().getText().length(); }

    /** Player pressed the "continue" button. */
    public void advance() {
        if (lines.isEmpty()) return;
        if (!isLineComplete()) { shown = lines.peek().getText().length(); return; }   // 1st press: show everything
        lines.dequeue();                                                               // 2nd press: next line
        shown = 0;
    }
}
