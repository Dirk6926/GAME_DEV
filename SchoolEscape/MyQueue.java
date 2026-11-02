import java.util.NoSuchElementException;

/** Linked FIFO queue (DATA STRUCTURE: Queue). */
public class MyQueue<T> {

    private class Node {
        T data;
        Node next;
        Node(T data) { this.data = data; }
    }

    private Node front, back;
    private int size;

    public void enqueue(T value) {
        Node n = new Node(value);
        if (back == null) front = back = n;
        else { back.next = n; back = n; }
        size++;
    }

    public T dequeue() {
        if (front == null) throw new NoSuchElementException("queue is empty");
        T v = front.data;
        front = front.next;
        if (front == null) back = null;
        size--;
        return v;
    }

    public T peek() {
        if (front == null) throw new NoSuchElementException("queue is empty");
        return front.data;
    }

    public boolean isEmpty() { return size == 0; }
    public int size() { return size; }
    public void clear() { front = back = null; size = 0; }
}
