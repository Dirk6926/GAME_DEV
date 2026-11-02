import java.util.ArrayList;
import java.util.List;

/** Binary search tree keyed by a Comparable (DATA STRUCTURE: Tree). Used for the journal pages. */
public class MyTree<K extends Comparable<K>, V> {

    private class Node {
        K key;
        V value;
        Node left, right;
        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private Node root;
    private int size;

    public void insert(K key, V value) { root = insert(root, key, value); }

    private Node insert(Node n, K key, V value) {
        if (n == null) { size++; return new Node(key, value); }
        int c = key.compareTo(n.key);
        if (c < 0) n.left = insert(n.left, key, value);
        else if (c > 0) n.right = insert(n.right, key, value);
        else n.value = value;
        return n;
    }

    public V find(K key) {
        Node cur = root;
        while (cur != null) {
            int c = key.compareTo(cur.key);
            if (c == 0) return cur.value;
            cur = (c < 0) ? cur.left : cur.right;
        }
        return null;
    }

    /** In-order traversal: values come out sorted by key. */
    public List<V> inOrder() {
        List<V> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private void walk(Node n, List<V> out) {
        if (n == null) return;
        walk(n.left, out);
        out.add(n.value);
        walk(n.right, out);
    }

    public int size() { return size; }
}
