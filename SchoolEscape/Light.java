/** A ceiling fluorescent fixture. It only lights the zone (a rectangle of the map) it belongs to. */
public class Light {
    public enum Mode { ON, OFF, FLICKER }

    private final double x, y;
    private final boolean alongX;
    private final double intensity;
    private Mode mode;
    private final double seed;
    private double disturbUntil;
    private final double zx0, zy0, zx1, zy1;

    public Light(double x, double y, boolean alongX, double intensity, Mode mode,
                 double zx0, double zy0, double zx1, double zy1) {
        this.x = x; this.y = y; this.alongX = alongX;
        this.intensity = intensity; this.mode = mode;
        this.zx0 = zx0; this.zy0 = zy0; this.zx1 = zx1; this.zy1 = zy1;
        this.seed = (x * 12.9898 + y * 78.233) % 100;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isAlongX() { return alongX; }
    public double getIntensity() { return intensity; }
    public Mode getMode() { return mode; }
    public void setMode(Mode m) { mode = m; }
    public double getZx0() { return zx0; }
    public double getZy0() { return zy0; }
    public double getZx1() { return zx1; }
    public double getZy1() { return zy1; }

    /** 0..1 brightness of the tube at a time. */
    public void disturb(double until) { if (until > disturbUntil) disturbUntil = until; }

    public double level(double t) {
        Mode m = mode;
        if (t < disturbUntil && m == Mode.ON) m = Mode.FLICKER;
        switch (m) {
            case ON: return 1.0;
            case OFF: return 0.0;
            default: {
                double a = Math.sin(t * 23 + seed) * Math.sin(t * 7.1 + seed * 1.7) + 0.6 * Math.sin(t * 53 + seed * 3);
                return a > -0.15 ? 1.0 : (a > -0.7 ? 0.22 : 0.0);
            }
        }
    }
}
