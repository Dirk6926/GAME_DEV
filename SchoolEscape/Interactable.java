/** Anything the player can use with the F key (interface = ABSTRACTION). */
public interface Interactable {
    String prompt();
    boolean canInteract(Game g);
    void interact(Game g);
}
