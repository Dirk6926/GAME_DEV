import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Software 3D renderer (no OpenGL, no libraries). Walls are raycast; floor and ceiling are textured per pixel.
 * Every pixel is lit by the ceiling fixtures (diffuse + glossy reflection on the tiles), the flashlight beam,
 * fog and a little moonlight. A bloom, vignette and film grain pass finishes the picture.
 */
public class Renderer {
    public static final int W = 640, H = 360;
    public static final double K = 0.89, CH = 1.75, LZ = 1.64, MAXD = 48;
    private static final double LR = 0.80, LG = 0.92, LB = 1.0;          // cool fluorescent colour
    private static final double AR = 0.026, AG = 0.036, AB = 0.075;      // moonlight ambient
    private static final int TW = Textures.TW, TH = Textures.TH, FS = Textures.FS;
    private static final int MAXL = 160;

    private final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private final int[] pix = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private final double[] zbuf = new double[W];
    private final float[] vig = new float[W * H];
    private final float[] bloom = new float[(W / 4) * (H / 4) * 3];
    private final float[] bloomTmp = new float[(W / 4) * (H / 4) * 3];

    // per-frame camera state
    private World world;
    private double px, py, dirX, dirY, planeX, planeY, camZ, horizon, time;
    private boolean flashOn;
    private double flashPow, vertC, c0len;
    private int frame;

    // per-frame light points (a fixture is two points so reflections are bar-shaped)
    private final double[] lxs = new double[MAXL], lys = new double[MAXL], lint = new double[MAXL];
    private final double[] zx0 = new double[MAXL], zy0 = new double[MAXL], zx1 = new double[MAXL], zy1 = new double[MAXL];
    private int nl;
    private final double[] fx = new double[96], fy = new double[96], flev = new double[96];
    private final boolean[] fAlong = new boolean[96];
    private int nf;

    public Renderer() {
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                double dx = (x - W / 2.0) / (W / 2.0), dy = (y - H / 2.0) / (H / 2.0);
                vig[y * W + x] = (float) Math.max(0.30, 1.0 - 0.50 * (dx * dx * 0.62 + dy * dy * 0.85));
            }
    }

    public BufferedImage getImage() { return image; }

    /* ------------------------------------------------------------ frame */
    public void render(Game g, double t) {
        world = g.world();
        Player p = g.player();
        time = t;
        frame++;
        px = p.getX(); py = p.getY();
        double a = p.getAngle();
        dirX = Math.cos(a); dirY = Math.sin(a);
        planeX = -dirY * K; planeY = dirX * K;
        camZ = p.getEye();
        horizon = H / 2.0 + p.getPitch();
        flashOn = g.flashlightOn();
        flashPow = 2.6;
        if (flashOn && p.getFlashlight().getCharge() < 15) flashPow *= Math.sin(t * 41) > 0.2 ? 1 : 0.35;
        vertC = -(H / 2.0 - horizon) / H;
        c0len = Math.sqrt(1 + vertC * vertC);
        buildLights(t);

        IntStream.range(0, H).parallel().forEach(this::row);
        IntStream.range(0, W).parallel().forEach(this::column);
        drawSprites(g);
        post();
    }

    private void buildLights(double t) {
        nl = 0; nf = 0;
        for (Light l : world.lights()) {
            double dx = l.getX() - px, dy = l.getY() - py, d2 = dx * dx + dy * dy;
            if (d2 > 45 * 45) continue;
            double lev = l.level(t);
            if (nf < fx.length) { fx[nf] = l.getX(); fy[nf] = l.getY(); fAlong[nf] = l.isAlongX(); flev[nf] = lev; nf++; }
            if (lev < 0.02 || d2 > 30 * 30) continue;
            for (int s = -1; s <= 1; s += 2) {
                if (nl >= MAXL) break;
                lxs[nl] = l.getX() + (l.isAlongX() ? s * 0.8 : 0);
                lys[nl] = l.getY() + (l.isAlongX() ? 0 : s * 0.8);
                lint[nl] = l.getIntensity() * lev * 1.15;
                zx0[nl] = l.getZx0(); zy0[nl] = l.getZy0(); zx1[nl] = l.getZx1(); zy1[nl] = l.getZy1();
                nl++;
            }
        }
    }

    /* ------------------------------------------------------------ lighting */
    /** Fills o[0..2] with diffuse light and o[3..5] with specular light reaching a point. */
    private void shade(double wx, double wy, double wz, double nx, double ny, double nz, double gl, double[] o) {
        double r = AR, g = AG, b = AB, sr = 0, sg = 0, sb = 0;
        double ex = px - wx, ey = py - wy, ez = camZ - wz;
        double ei = 1 / Math.sqrt(ex * ex + ey * ey + ez * ez + 1e-9);
        ex *= ei; ey *= ei; ez *= ei;
        for (int i = 0; i < nl; i++) {
            if (wx < zx0[i] || wx >= zx1[i] || wy < zy0[i] || wy >= zy1[i]) continue;
            double dx = lxs[i] - wx, dy = lys[i] - wy, dz = LZ - wz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 400) continue;
            double id = 1 / Math.sqrt(d2);
            double ndl = (dx * nx + dy * ny + dz * nz) * id;
            if (ndl <= 0.01) continue;
            double att = lint[i] / (1 + 0.20 * d2);
            double s = ndl * att;
            if (gl > 0.02) {
                double hx = dx * id + ex, hy = dy * id + ey, hz = dz * id + ez;
                double nh = (hx * nx + hy * ny + hz * nz) / Math.sqrt(hx * hx + hy * hy + hz * hz + 1e-9);
                if (nh > 0.7) {
                    double p = nh * nh; p *= p; p *= p; p *= p; p *= p;           // nh^32
                    double sp = gl * att * p * 2.6;
                    sr += sp * LR; sg += sp * LG; sb += sp * LB;
                }
            }
            r += s * LR; g += s * LG; b += s * LB;
        }
        o[0] = r; o[1] = g; o[2] = b; o[3] = sr; o[4] = sg; o[5] = sb;
    }

    /** Adds the flashlight (a cone from the camera) to a lit pixel. */
    private void flash(int x, int y, double depth, double nx, double ny, double nz, double gl, double[] o) {
        if (!flashOn) return;
        double c = 2.0 * (x + 0.5) / W - 1;
        double vert = -(y + 0.5 - horizon) / H;
        double lat = K * c;
        double al = Math.sqrt(1 + lat * lat + vert * vert);
        double cos = (1 + vert * vertC) / (al * c0len);
        if (cos <= 0.86) return;
        double sp = (cos - 0.86) / (0.985 - 0.86);
        if (sp > 1) sp = 1;
        sp = sp * sp * (3 - 2 * sp);
        double d3 = depth * al;
        double ax = dirX + planeX * c, ay = dirY + planeY * c;
        double ndl = -(ax * nx + ay * ny + vert * nz) / al;
        if (ndl < 0.14) ndl = 0.14;
        double att = flashPow * sp / (1 + 0.05 * d3 * d3);
        double dif = att * ndl;
        o[0] += dif; o[1] += dif * 0.96; o[2] += dif * 0.86;
        if (gl > 0.02) {
            double p = ndl * ndl; p *= p;                                           // broad glossy hot-spot
            double s = gl * att * p * 1.5;
            o[3] += s; o[4] += s * 0.97; o[5] += s * 0.9;
        }
    }

    private static int tone(double x) {
        double v = 255 * 1.12 * x / (1 + 0.45 * x);
        return v > 255 ? 255 : (v < 0 ? 0 : (int) v);
    }

    private static int pack(int col, double[] o, double emis, double depth) {
        double ar = ((col >> 16) & 255) * (1 / 255.0), ag = ((col >> 8) & 255) * (1 / 255.0), ab = (col & 255) * (1 / 255.0);
        double em = emis * 2.2;
        double haze = 1 - 1 / (1 + depth * 0.035);
        double r = ar * (o[0] + em) + o[3] + 0.010 * haze;
        double g = ag * (o[1] + em) + o[4] + 0.016 * haze;
        double b = ab * (o[2] + em) + o[5] + 0.032 * haze;
        return 0xFF000000 | (tone(r) << 16) | (tone(g) << 8) | tone(b);
    }

    /* ------------------------------------------------------------ floor and ceiling */
    private void row(int y) {
        double dy = y + 0.5 - horizon, ady = Math.abs(dy);
        int base = y * W;
        if (ady < 0.5) { for (int x = 0; x < W; x++) pix[base + x] = 0x04050a; return; }
        boolean fl = dy > 0;
        double zh = fl ? camZ : (CH - camZ);
        if (zh <= 0.01) { for (int x = 0; x < W; x++) pix[base + x] = 0x04050a; return; }
        double depth = zh * H / ady;
        if (depth > MAXD) { for (int x = 0; x < W; x++) pix[base + x] = 0x04050a; return; }
        double[] o = new double[6];
        double nz = fl ? 1 : -1, wz = fl ? 0 : CH;
        for (int x = 0; x < W; x++) {
            double c = 2.0 * (x + 0.5) / W - 1;
            double wx = px + depth * (dirX + planeX * c), wy = py + depth * (dirY + planeY * c);
            int idx = (((int) (wy * FS)) & (FS - 1)) * FS + (((int) (wx * FS)) & (FS - 1));
            int col;
            double gl, emis = 0;
            if (fl) {
                int ft = world.floorType((int) Math.floor(wx), (int) Math.floor(wy));
                col = Textures.floors[ft][idx];
                gl = Textures.gloss[ft][idx];
            } else {
                col = Textures.ceiling[idx];
                gl = 0.04;
                for (int f = 0; f < nf; f++) {
                    double ddx = Math.abs(wx - fx[f]), ddy = Math.abs(wy - fy[f]);
                    double a = fAlong[f] ? ddx : ddy, b = fAlong[f] ? ddy : ddx;
                    if (a < 1.15 && b < 0.34) {
                        if (a < 0.95 && b < 0.20) { col = flev[f] > 0.02 ? 0xFFEFF8FF : 0xFF6E767E; emis = flev[f]; }
                        else col = 0xFF8C9298;
                        break;
                    }
                }
            }
            shade(wx, wy, wz, 0, 0, nz, gl, o);
            flash(x, y, depth, 0, 0, nz, gl, o);
            pix[base + x] = pack(col, o, emis, depth);
        }
    }

    /* ------------------------------------------------------------ walls */
    private void column(int x) {
        double c = 2.0 * (x + 0.5) / W - 1;
        double rdx = dirX + planeX * c, rdy = dirY + planeY * c;
        int mx = (int) Math.floor(px), my = (int) Math.floor(py);
        double ddx = rdx == 0 ? 1e30 : Math.abs(1 / rdx), ddy = rdy == 0 ? 1e30 : Math.abs(1 / rdy);
        int stepX = rdx < 0 ? -1 : 1, stepY = rdy < 0 ? -1 : 1;
        double sdx = (rdx < 0 ? px - mx : mx + 1 - px) * ddx, sdy = (rdy < 0 ? py - my : my + 1 - py) * ddy;
        int side = 0, guard = 0;
        while (guard++ < 140) {
            if (sdx < sdy) { sdx += ddx; mx += stepX; side = 0; } else { sdy += ddy; my += stepY; side = 1; }
            if (world.tile(mx, my) != Tile.EMPTY) break;
        }
        double perp = side == 0 ? sdx - ddx : sdy - ddy;
        if (perp < 0.04) perp = 0.04;
        zbuf[x] = perp;
        Textures.Wall wt = Textures.wall(world.tile(mx, my));
        double hitX = px + perp * rdx, hitY = py + perp * rdy;
        double wallX = side == 0 ? hitY : hitX;
        wallX -= Math.floor(wallX);
        int texX = (int) (wallX * TW);
        if ((side == 0 && rdx < 0) || (side == 1 && rdy > 0)) texX = TW - 1 - texX;
        texX = Math.max(0, Math.min(TW - 1, texX));

        double yTop = horizon - (CH - camZ) * H / perp, yBot = horizon + camZ * H / perp;
        int y0 = Math.max(0, (int) Math.ceil(yTop)), y1 = Math.min(H - 1, (int) Math.floor(yBot));
        double nx = side == 0 ? -stepX : 0, ny = side == 1 ? -stepY : 0;
        double wx = hitX + nx * 0.05, wy = hitY + ny * 0.05;
        double[] o = new double[6];
        double inv = 1.0 / (yBot - yTop);
        for (int y = y0; y <= y1; y++) {
            double v = (y + 0.5 - yTop) * inv;
            int ty = (int) (v * TH);
            if (ty >= TH) ty = TH - 1; else if (ty < 0) ty = 0;
            int col = wt.px[ty * TW + texX];
            shade(wx, wy, CH * (1 - v), nx, ny, 0, wt.gloss, o);
            flash(x, y, perp, nx, ny, 0, wt.gloss, o);
            pix[y * W + x] = pack(col, o, (255 - (col >>> 24)) / 255.0, perp);
        }
    }

    /* ------------------------------------------------------------ sprites */
    private void drawSprites(Game g) {
        List<GameObject> list = new ArrayList<>();
        for (GameObject o : g.objects()) if (o.visible() && o.sprite() != null) list.add(o);
        list.sort((a, b) -> Double.compare(b.distanceTo(px, py), a.distanceTo(px, py)));
        double[] lit = new double[6], tmp = new double[6];
        for (GameObject o : list) {
            double dx = o.getX() - px, dy = o.getY() - py;
            double depth = dx * dirX + dy * dirY;
            if (depth < 0.25 || depth > MAXD) continue;
            double lateral = -dx * dirY + dy * dirX;
            double sxc = W / 2.0 * (1 + lateral / (K * depth));
            Sprite sp = o.sprite();
            double hPx = o.height() * H / depth, wPx = hPx * sp.w / sp.h;
            if (hPx < 1) continue;
            double bottom = horizon + (camZ - o.lift()) * H / depth, top = bottom - hPx;
            int x0 = (int) (sxc - wPx / 2), x1 = (int) (sxc + wPx / 2);
            double len = Math.hypot(dx, dy) + 1e-9;
            double nxs = -dx / len, nys = -dy / len;
            shade(o.getX(), o.getY(), o.lift() + o.height() * 0.5, nxs, nys, 0, 0, lit);
            double glow = o.glow(), alpha = o.alpha();
            for (int sx = Math.max(0, x0); sx <= Math.min(W - 1, x1); sx++) {
                if (depth >= zbuf[sx]) continue;
                int u = (int) ((sx - x0) / wPx * sp.w);
                if (u < 0 || u >= sp.w) continue;
                for (int sy = Math.max(0, (int) top); sy <= Math.min(H - 1, (int) bottom); sy++) {
                    int v = (int) ((sy - top) / hPx * sp.h);
                    if (v < 0 || v >= sp.h) continue;
                    int c = sp.px[v * sp.w + u];
                    int a = c >>> 24;
                    if (a < 12) continue;
                    System.arraycopy(lit, 0, tmp, 0, 6);
                    flash(sx, sy, depth, nxs, nys, 0, 0, tmp);
                    int out = pack(c, tmp, glow, depth);
                    double k = a / 255.0 * alpha;
                    int dst = pix[sy * W + sx];
                    int r = (int) (((dst >> 16) & 255) * (1 - k) + ((out >> 16) & 255) * k);
                    int gg = (int) (((dst >> 8) & 255) * (1 - k) + ((out >> 8) & 255) * k);
                    int bb = (int) ((dst & 255) * (1 - k) + (out & 255) * k);
                    pix[sy * W + sx] = (r << 16) | (gg << 8) | bb;
                }
            }
        }
    }

    /* ------------------------------------------------------------ bloom, vignette, grain */
    private void post() {
        final int bw = W / 4, bh = H / 4;
        IntStream.range(0, bh).parallel().forEach(by -> {
            for (int bx = 0; bx < bw; bx++) {
                float sr = 0, sg = 0, sb = 0;
                for (int j = 0; j < 4; j++)
                    for (int i = 0; i < 4; i++) {
                        int c = pix[(by * 4 + j) * W + bx * 4 + i];
                        sr += Math.max(0, ((c >> 16) & 255) - 165);
                        sg += Math.max(0, ((c >> 8) & 255) - 165);
                        sb += Math.max(0, (c & 255) - 165);
                    }
                int o = (by * bw + bx) * 3;
                bloom[o] = sr / 16f / 90f; bloom[o + 1] = sg / 16f / 90f; bloom[o + 2] = sb / 16f / 90f;
            }
        });
        for (int pass = 0; pass < 2; pass++) { blur(bloom, bloomTmp, bw, bh, true); blur(bloomTmp, bloom, bw, bh, false); }

        final int frameSeed = frame * 7919;
        IntStream.range(0, H).parallel().forEach(y -> {
            double fy = (y - 1.5) / 4.0;
            int by0 = Math.max(0, Math.min(bh - 1, (int) Math.floor(fy)));
            int by1 = Math.min(bh - 1, by0 + 1);
            double wy = Math.max(0, Math.min(1, fy - by0));
            for (int x = 0; x < W; x++) {
                double fx2 = (x - 1.5) / 4.0;
                int bx0 = Math.max(0, Math.min(bw - 1, (int) Math.floor(fx2)));
                int bx1 = Math.min(bw - 1, bx0 + 1);
                double wx = Math.max(0, Math.min(1, fx2 - bx0));
                int c = pix[y * W + x];
                double v = vig[y * W + x];
                double grain = ((Textures.hash(x, y, frameSeed) & 255) - 128) * 0.05;
                int out = 0;
                for (int ch = 0; ch < 3; ch++) {
                    double b = lerp(lerp(bloom[(by0 * bw + bx0) * 3 + ch], bloom[(by0 * bw + bx1) * 3 + ch], wx),
                                    lerp(bloom[(by1 * bw + bx0) * 3 + ch], bloom[(by1 * bw + bx1) * 3 + ch], wx), wy);
                    int shift = 16 - 8 * ch;
                    double val = (((c >> shift) & 255) + b * 255 * 1.25) * v + grain;
                    out |= (val < 0 ? 0 : (val > 255 ? 255 : (int) val)) << shift;
                }
                pix[y * W + x] = out;
            }
        });
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static void blur(float[] src, float[] dst, int w, int h, boolean horizontal) {
        final int R = 3;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int ch = 0; ch < 3; ch++) {
                    float s = 0;
                    for (int k = -R; k <= R; k++) {
                        int xx = horizontal ? Math.max(0, Math.min(w - 1, x + k)) : x;
                        int yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + k));
                        s += src[(yy * w + xx) * 3 + ch];
                    }
                    dst[(y * w + x) * 3 + ch] = s / (2 * R + 1);
                }
    }
}
