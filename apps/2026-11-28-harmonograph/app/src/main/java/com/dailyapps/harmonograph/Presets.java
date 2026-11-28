package com.dailyapps.harmonograph;

public final class Presets {
    private Presets() {}

    public static final String[] NAMES = {
            "Drifting Knot", "Spiral Galaxy", "Butterfly", "Rotary Rose", "Woven Ink", "Spectrum Bloom", "Lantern"
    };

    public static Params get(int i) {
        switch (i) {
            case 1:
                return make(new float[]{1f, 1f, 1.003f, 1f, 1f}, new float[]{90, 0, 0, 0, 0},
                        new float[]{1f, 0f, 1f, 0f, 0f}, new float[]{0.012f, 0f, 0.012f, 0f, 0f}, 2, 1.5f, 220);
            case 2:
                return make(new float[]{2f, 6.01f, 3f, 4f, 1f}, new float[]{0, 30, 90, 0, 0},
                        new float[]{1f, 0.35f, 1f, 0.3f, 0f}, new float[]{0.003f, 0.008f, 0.004f, 0.007f, 0f}, 3, 1.5f, 160);
            case 3:
                return make(new float[]{3f, 1f, 2.98f, 1f, 1.01f}, new float[]{0, 0, 90, 0, 0},
                        new float[]{0.8f, 0f, 0.8f, 0f, 0.6f}, new float[]{0.005f, 0f, 0.005f, 0f, 0.003f}, 1, 2f, 140);
            case 4:
                return make(new float[]{4f, 4.02f, 4.01f, 4f, 1f}, new float[]{0, 90, 90, 0, 0},
                        new float[]{1f, 0.6f, 1f, 0.6f, 0f}, new float[]{0.006f, 0.004f, 0.005f, 0.004f, 0f}, 4, 1.2f, 160);
            case 5:
                return make(new float[]{5f, 2f, 5.02f, 2.01f, 1f}, new float[]{0, 45, 90, 0, 30},
                        new float[]{0.7f, 0.5f, 0.7f, 0.5f, 0.3f}, new float[]{0.004f, 0.006f, 0.004f, 0.005f, 0.004f}, 5, 1.5f, 150);
            case 6:
                return make(new float[]{1f, 3.005f, 2f, 3f, 1f}, new float[]{90, 0, 0, 90, 0},
                        new float[]{1f, 0.4f, 1f, 0.25f, 0f}, new float[]{0.005f, 0.003f, 0.006f, 0.003f, 0f}, 7, 1.8f, 140);
            default:
                return new Params();
        }
    }

    private static Params make(float[] f, float[] p, float[] a, float[] d, int pal, float width, float len) {
        Params r = new Params();
        System.arraycopy(f, 0, r.f, 0, Params.N);
        System.arraycopy(p, 0, r.p, 0, Params.N);
        System.arraycopy(a, 0, r.a, 0, Params.N);
        System.arraycopy(d, 0, r.d, 0, Params.N);
        r.palette = pal;
        r.width = width;
        r.length = len;
        return r;
    }
}
