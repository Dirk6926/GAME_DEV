import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Singly linked list written from scratch (DATA STRUCTURE: Linked List). */
public class MyLinkedList<T> implements Iterable<T> {

    private class Node {
        T data;
        Node next;
        Node(T data) { this.data = data; }
    }

    private Node head, tail;
    private int size;

    public void addLast(T value) {
        Node n = new Node(value);
        if (head == null) head = tail = n;
        else { tail.next = n; tail = n; }
        size++;
    }

    public void addFirst(T value) {
        Node n = new Node(value);
        n.next = head;
        head = n;
        if (tail == null) tail = n;
        size++;
    }

    public T get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index " + index);
        Node cur = head;
        for (int i = 0; i < index; i++) cur = cur.next;
        return cur.data;
    }

    public T removeFirst() {
        if (head == null) throw new NoSuchElementException("list is empty");
        T v = head.data;
        head = head.next;
        if (head == null) tail = null;
        size--;
        return v;
    }

    public boolean remove(T value) {
        Node prev = null, cur = head;
        while (cur != null) {
            if (cur.data == value || (cur.data != null && cur.data.equals(value))) {
                if (prev == null) head = cur.next; else prev.next = cur.next;
                if (cur == tail) tail = prev;
                size--;
                return true;
            }
            prev = cur;
            cur = cur.next;
        }
        return false;
    }

    public boolean contains(T value) {
        for (T t : this) if (t == value || (t != null && t.equals(value))) return true;
        return false;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void clear() { head = tail = null; size = 0; }

    /** Copy of the contents, so the list can be modified safely while looping. */
    public List<T> toList() {
        List<T> out = new ArrayList<>(size);
        for (T t : this) out.add(t);
        return out;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node cur = head;
            public boolean hasNext() { return cur != null; }
            public T next() {
                if (cur == null) throw new NoSuchElementException();
                T v = cur.data;
                cur = cur.next;
                return v;
            }
        };
    }
}
