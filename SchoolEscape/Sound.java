import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** All sounds are generated from maths at start-up, so the game needs no audio files. */
public class Sound {
    private static final float RATE = 22050f;
    private final Map<String, byte[]> bank = new HashMap<>();
    private final Random rnd = new Random(11);
    private boolean enabled = true;
    private Clip ambient;
    private float volume = 0.8f;

    public Sound() {
        bank.put("step", pcm(0.16, (t, i) -> noise(i, 380) * env(t, 5) * 0.55 + Math.sin(t * 90) * env(t, 9) * 0.5));
        bank.put("stepSoft", pcm(0.12, (t, i) -> noise(i, 250) * env(t, 6) * 0.25));
        bank.put("run", pcm(0.15, (t, i) -> noise(i, 700) * env(t, 6) * 0.8 + Math.sin(t * 110) * env(t, 8) * 0.5));
        bank.put("land", pcm(0.25, (t, i) -> Math.sin(t * 70) * env(t, 6) * 0.8 + noise(i, 300) * env(t, 8) * 0.4));
        bank.put("click", pcm(0.05, (t, i) -> Math.sin(t * 9000) * env(t, 30) * 0.5));
        bank.put("pickup", pcm(0.28, (t, i) -> Math.sin(t * 5200 * (1 + t * 0.6)) * env(t, 6) * 0.45));
        bank.put("unlock", pcm(0.5, (t, i) -> (Math.sin(t * 3200) * (t < 0.12 ? 1 : 0) + Math.sin(t * 4300) * (t > 0.2 ? 1 : 0)) * env(t, 5) * 0.4));
        bank.put("locked", pcm(0.3, (t, i) -> Math.sin(t * 160) * env(t, 9) * 0.7 + noise(i, 500) * env(t, 20) * 0.5));
        bank.put("creak", pcm(0.9, (t, i) -> Math.sin(t * (900 - t * 500) * 0.9) * (0.2 + 0.15 * Math.sin(t * 90)) * Math.min(1, t * 8) * env(t, 2) + noise(i, 1500) * 0.06));
        bank.put("drawer", pcm(0.5, (t, i) -> noise(i, 600) * env(t, 4) * 0.5 + Math.sin(t * 180) * env(t, 8) * 0.3));
        bank.put("locker", pcm(0.55, (t, i) -> noise(i, 1200) * env(t, 5) * 0.55 + Math.sin(t * 700) * env(t, 14) * 0.4));
        bank.put("hurt", pcm(0.5, (t, i) -> Math.sin(t * 120) * env(t, 5) * 0.9 + noise(i, 800) * env(t, 8) * 0.6));
        bank.put("heart", pcm(0.7, (t, i) -> (Math.sin(t * 110) * env(t, 14) + 0.8 * Math.sin((t - 0.18) * 100) * env(Math.max(0, t - 0.18), 14) * (t > 0.18 ? 1 : 0)) * 0.9));
        bank.put("sting", pcm(1.4, (t, i) -> (Math.sin(t * 700 * (1 + t * 3)) * 0.4 + Math.sin(t * 90) * 0.6 + noise(i, 3000) * 0.3) * env(t, 2.2)));
        bank.put("win", pcm(2.2, (t, i) -> (Math.sin(t * 1320) + 0.6 * Math.sin(t * 1980) + 0.4 * Math.sin(t * 2640)) * env(t, 1.6) * 0.35));
    }

    private interface Gen { double at(double t, int i); }

    private static double env(double t, double k) { return Math.exp(-k * t); }

    private double noise(int i, double cutoff) { return rnd.nextDouble() * 2 - 1; }

    private byte[] pcm(double seconds, Gen g) {
        int n = (int) (seconds * RATE);
        byte[] out = new byte[n * 2];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double s = g.at(t * 2 * Math.PI, i);
            lp += 0.35 * (s - lp);                           // soften harsh edges
            int v = (int) Math.max(-32767, Math.min(32767, lp * 15000));
            out[2 * i] = (byte) (v & 255);
            out[2 * i + 1] = (byte) ((v >> 8) & 255);
        }
        return out;
    }

    public void setEnabled(boolean on) { enabled = on; if (!on) stopAmbient(); }

    public void play(String name) { play(name, 1f); }

    public void play(String name, float vol) {
        if (!enabled) return;
        byte[] data = bank.get(name);
        if (data == null) return;
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(new AudioFormat(RATE, 16, 1, true, false), data, 0, data.length);
            gain(clip, vol * volume);
            clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) clip.close(); });
            clip.start();
        } catch (Exception | Error e) {
            enabled = false;                                 // no audio device: switch sound off quietly
        }
    }

    private void gain(Clip c, float vol) {
        try {
            FloatControl fc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            fc.setValue((float) Math.max(fc.getMinimum(), 20 * Math.log10(Math.max(0.0001, vol))));
        } catch (Exception ignored) { }
    }

    /** A low unsettling drone that loops forever. */
    public void startAmbient() {
        if (!enabled || ambient != null) return;
        try {
            int n = (int) (8 * RATE);
            byte[] d = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                double t = i / RATE;
                double s = 0.5 * Math.sin(2 * Math.PI * 55 * t) + 0.35 * Math.sin(2 * Math.PI * 82.4 * t + Math.sin(t * 0.8))
                        + 0.2 * Math.sin(2 * Math.PI * 110.7 * t) * (0.5 + 0.5 * Math.sin(2 * Math.PI * t / 8))
                        + (rnd.nextDouble() - 0.5) * 0.04;
                int v = (int) (s * 6500);
                d[2 * i] = (byte) (v & 255);
                d[2 * i + 1] = (byte) ((v >> 8) & 255);
            }
            ambient = AudioSystem.getClip();
            ambient.open(new AudioFormat(RATE, 16, 1, true, false), d, 0, d.length);
            gain(ambient, 0.35f * volume);
            ambient.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception | Error e) {
            enabled = false;
        }
    }

    public void stopAmbient() {
        if (ambient != null) { ambient.stop(); ambient.close(); ambient = null; }
    }
}
