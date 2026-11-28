package com.dailyapps.harmonograph;

import java.util.Locale;
import java.util.Random;

/** Settings for one harmonograph drawing: four linear pendulums plus one rotary pendulum. */
public class Params {
    public static final int N = 5;
    public static final int X1 = 0, X2 = 1, Y1 = 2, Y2 = 3, ROT = 4;
    public static final String[] NAMES = {"X1", "X2", "Y1", "Y2", "Rotary"};

    public static final float F_MIN = 0.25f, F_MAX = 8f;
    public static final float D_MAX = 0.03f;

    public float[] f = new float[N];   // frequency (rad per time unit)
    public float[] p = new float[N];   // phase in degrees
    public float[] a = new float[N];   // amplitude 0..1
    public float[] d = new float[N];   // damping
    public int palette = 0;
    public float width = 2f;
    public float length = 120f;

    public Params() {
        f[X1] = 2f; f[X2] = 3.01f; f[Y1] = 3f; f[Y2] = 2f; f[ROT] = 1f;
        p[X1] = 0; p[X2] = 90; p[Y1] = 45; p[Y2] = 0; p[ROT] = 0;
        a[X1] = 1f; a[X2] = 0.5f; a[Y1] = 1f; a[Y2] = 0.5f; a[ROT] = 0f;
        d[X1] = 0.004f; d[X2] = 0.006f; d[Y1] = 0.005f; d[Y2] = 0.003f; d[ROT] = 0.004f;
    }

    public Params copy() {
        Params c = new Params();
        System.arraycopy(f, 0, c.f, 0, N);
        System.arraycopy(p, 0, c.p, 0, N);
        System.arraycopy(a, 0, c.a, 0, N);
        System.arraycopy(d, 0, c.d, 0, N);
        c.palette = palette;
        c.width = width;
        c.length = length;
        return c;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder("H1");
        for (int i = 0; i < N; i++) {
            sb.append(String.format(Locale.US, ";%.3f,%.1f,%.3f,%.5f", f[i], p[i], a[i], d[i]));
        }
        sb.append(String.format(Locale.US, ";%d,%.1f,%.0f", palette, width, length));
        return sb.toString();
    }

    public static Params decode(String s) {
        if (s == null) return null;
        try {
            String t = s.trim();
            int start = t.indexOf("H1;");
            if (start < 0) return null;
            t = t.substring(start);
            int sp = t.indexOf(' ');
            if (sp > 0) t = t.substring(0, sp);
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(0, nl);
            String[] parts = t.split(";");
            if (parts.length < N + 2) return null;
            Params r = new Params();
            for (int i = 0; i < N; i++) {
                String[] v = parts[i + 1].split(",");
                r.f[i] = clamp(Float.parseFloat(v[0]), F_MIN, F_MAX);
                r.p[i] = clamp(Float.parseFloat(v[1]), 0, 360);
                r.a[i] = clamp(Float.parseFloat(v[2]), 0, 1);
                r.d[i] = clamp(Float.parseFloat(v[3]), 0, D_MAX);
            }
            String[] g = parts[N + 1].split(",");
            r.palette = Math.max(0, Math.min(Palettes.COUNT - 1, Integer.parseInt(g[0].trim())));
            r.width = clamp(Float.parseFloat(g[1]), 0.5f, 10f);
            r.length = clamp(Float.parseFloat(g[2]), 20f, 400f);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static final float[] RATIOS = {1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f};

    public void randomize(Random rnd) {
        int base = rnd.nextInt(3);
        for (int i = 0; i < 4; i++) {
            float r = RATIOS[Math.min(RATIOS.length - 1, base + rnd.nextInt(5))];
            float detune = rnd.nextFloat() < 0.75f ? (rnd.nextFloat() - 0.5f) * 0.04f : 0f;
            f[i] = clamp(r + detune, F_MIN, F_MAX);
            p[i] = rnd.nextInt(24) * 15f;
            d[i] = 0.001f + rnd.nextFloat() * 0.008f;
        }
        a[X1] = 0.6f + rnd.nextFloat() * 0.4f;
        a[Y1] = 0.6f + rnd.nextFloat() * 0.4f;
        a[X2] = rnd.nextFloat() < 0.2f ? 0f : 0.15f + rnd.nextFloat() * 0.7f;
        a[Y2] = rnd.nextFloat() < 0.2f ? 0f : 0.15f + rnd.nextFloat() * 0.7f;
        if (rnd.nextFloat() < 0.45f) {
            a[ROT] = 0.15f + rnd.nextFloat() * 0.45f;
            f[ROT] = clamp(RATIOS[rnd.nextInt(5)] + (rnd.nextFloat() - 0.5f) * 0.03f, F_MIN, F_MAX);
            p[ROT] = rnd.nextInt(24) * 15f;
            d[ROT] = 0.001f + rnd.nextFloat() * 0.008f;
        } else {
            a[ROT] = 0f;
        }
    }

    public void mutate(Random rnd) {
        for (int i = 0; i < N; i++) {
            f[i] = clamp(f[i] + (rnd.nextFloat() - 0.5f) * 0.03f, F_MIN, F_MAX);
            p[i] = (p[i] + (rnd.nextFloat() - 0.5f) * 40f + 360f) % 360f;
            if (a[i] > 0) a[i] = clamp(a[i] + (rnd.nextFloat() - 0.5f) * 0.2f, 0.05f, 1f);
            d[i] = clamp(d[i] * (0.75f + rnd.nextFloat() * 0.5f), 0f, D_MAX);
        }
    }

    /** Short human readable ratio summary, e.g. "X 2.00:3.01  Y 3.00:2.00". */
    public String ratioText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "X %.2f", f[X1]));
        if (a[X2] > 0) sb.append(String.format(Locale.US, ":%.2f", f[X2]));
        sb.append(String.format(Locale.US, "   Y %.2f", f[Y1]));
        if (a[Y2] > 0) sb.append(String.format(Locale.US, ":%.2f", f[Y2]));
        if (a[ROT] > 0) sb.append(String.format(Locale.US, "   R %.2f", f[ROT]));
        return sb.toString();
    }
}
