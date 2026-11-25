/** NEW FILE - the two choices on the "input name and gender" screen. */
public enum Gender {
    MALE("Male"),
    FEMALE("Female");

    private final String label;

    Gender(String label) { this.label = label; }

    /** Text to show on the button / selector in your UI. */
    public String label() { return label; }

    /** The other option (handy for LEFT/RIGHT toggling). */
    public Gender other() { return this == MALE ? FEMALE : MALE; }
}
