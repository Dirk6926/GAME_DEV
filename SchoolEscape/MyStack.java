import java.util.EmptyStackException;

/** Array-backed LIFO stack that grows automatically (DATA STRUCTURE: Stack + Array). */
public class MyStack<T> {

    private Object[] items = new Object[8];
    private int top = 0;

    public void push(T value) {
        if (top == items.length) {
            Object[] bigger = new Object[items.length * 2];
            System.arraycopy(items, 0, bigger, 0, items.length);
            items = bigger;
        }
        items[top++] = value;
    }

    @SuppressWarnings("unchecked")
    public T pop() {
        if (top == 0) throw new EmptyStackException();
        T v = (T) items[--top];
        items[top] = null;
        return v;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        if (top == 0) throw new EmptyStackException();
        return (T) items[top - 1];
    }

    public boolean isEmpty() { return top == 0; }
    public int size() { return top; }
    public void clear() { items = new Object[8]; top = 0; }
}
