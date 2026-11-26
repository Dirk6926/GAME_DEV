/**
 * The two gender choices from the "name and gender" screen.
 * Each one knows its own pronouns, so story text never has to check "if male ... else ...".
 * (ENCAPSULATION: private fields + getters. Enums are a special kind of class.)
 */
public enum Gender {
    //            label     he/she  him/her  his/her  his/hers
    MALE  ("Male",   "he",  "him", "his", "his"),
    FEMALE("Female", "she", "her", "her", "hers");

    private final String label, subject, object, possessive, possessivePronoun;

    Gender(String label, String subject, String object, String possessive, String possessivePronoun) {
        this.label = label;
        this.subject = subject;
        this.object = object;
        this.possessive = possessive;
        this.possessivePronoun = possessivePronoun;
    }

    public String getLabel() { return label; }
    /** "he" / "she"   ->  "{he} was tired" */
    public String getSubject() { return subject; }
    /** "him" / "her"  ->  "they saw {him}" */
    public String getObject() { return object; }
    /** "his" / "her"  ->  "{his} bag" */
    public String getPossessive() { return possessive; }
    /** "his" / "hers" ->  "the bag is {hers}" */
    public String getPossessivePronoun() { return possessivePronoun; }
}