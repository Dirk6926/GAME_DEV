/** A picture with transparency (ARGB) that is drawn as a billboard in the 3D world. */
public final class Sprite {
    public final int w, h;
    public final int[] px;

    public Sprite(int w, int h, int[] px) { this.w = w; this.h = h; this.px = px; }
}
