import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/** Keyboard, mouse-look, click and wheel input collected for the game loop. */
public class Input extends KeyAdapter implements MouseWheelListener {
    private final boolean[] down = new boolean[512];
    private final boolean[] edge = new boolean[512];
    private double mouseDX, mouseDY;
    private int wheel;
    private boolean click;

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k < 0 || k >= 512) return;
        if (!down[k]) edge[k] = true;
        down[k] = true;
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k >= 0 && k < 512) down[k] = false;
    }

    @Override public synchronized void mouseWheelMoved(MouseWheelEvent e) { wheel += e.getWheelRotation(); }

    public synchronized boolean isDown(int key) { return down[key]; }

    /** True once per key press. */
    public synchronized boolean pressed(int key) {
        boolean v = edge[key];
        edge[key] = false;
        return v;
    }

    public synchronized void addMouse(double dx, double dy) { mouseDX += dx; mouseDY += dy; }
    public synchronized double takeDX() { double d = mouseDX; mouseDX = 0; return d; }
    public synchronized double takeDY() { double d = mouseDY; mouseDY = 0; return d; }
    public synchronized int takeWheel() { int w = wheel; wheel = 0; return w; }
    public synchronized void setClick() { click = true; }
    public synchronized boolean takeClick() { boolean c = click; click = false; return c; }

    /** Forget presses that happened while a menu was open. */
    public synchronized void clearEdges() {
        java.util.Arrays.fill(edge, false);
        click = false; wheel = 0; mouseDX = 0; mouseDY = 0;
    }
}
