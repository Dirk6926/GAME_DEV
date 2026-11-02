/** The hotbar items, stored in our own linked list (max 7). */
public class Inventory {
    public static final int CAPACITY = 7;
    private final MyLinkedList<Item> items = new MyLinkedList<>();

    public boolean add(Item item) {
        if (items.size() >= CAPACITY) return false;
        items.addLast(item);
        return true;
    }

    public boolean remove(Item item) { return items.remove(item); }
    public int size() { return items.size(); }
    public Item get(int i) { return i >= 0 && i < items.size() ? items.get(i) : null; }

    public boolean hasKey(String id) {
        for (Item it : items) if (it instanceof KeyItem && ((KeyItem) it).getId().equals(id)) return true;
        return false;
    }
}
