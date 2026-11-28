package com.dailyapps.harmonograph;

import android.graphics.Color;

public final class Palettes {
    private Palettes() {}

    public static final String[] NAMES = {"Aurora", "Ember", "Ocean", "Neon", "Ink", "Spectrum", "Mono", "Rose"};
    public static final int COUNT = NAMES.length;

    public static final int[] BG = {
            0xFF0B0F1A, 0xFF140A08, 0xFF06141C, 0xFF000000,
            0xFFF4EFE4, 0xFF0E0E10, 0xFF111111, 0xFFFFF4F2
    };

    public static final int[][] STOPS = {
            {0xFF5EF0C8, 0xFF4FA3FF, 0xFFB06BFF, 0xFFFF6BC1},
            {0xFFFFD36B, 0xFFFF8A3D, 0xFFE8344E, 0xFF7A1F5C},
            {0xFFA8F0FF, 0xFF3CC6E8, 0xFF1E6FD9, 0xFF3B4FCF},
            {0xFF00FFD0, 0xFFFFF200, 0xFFFF2E93, 0xFF7B2BFF},
            {0xFF1B2A49, 0xFF2E4A7D, 0xFF8B2E3C, 0xFF1B2A49},
            {0xFFFF4D4D, 0xFFFFB84D, 0xFFF2FF4D, 0xFF4DFF88, 0xFF4DD2FF, 0xFFA64DFF},
            {0xFFFFFFFF, 0xFFB0B0B0, 0xFFFFFFFF},
            {0xFFB8336A, 0xFFE07A5F, 0xFF3D405B, 0xFF81B29A}
    };

    public static boolean isLight(int palette) {
        int c = BG[palette];
        double lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c);
        return lum > 140;
    }

    /** Colour along the drawing, frac in 0..1. */
    public static int colorAt(int palette, float frac) {
        int[] s = STOPS[palette];
        if (frac <= 0) return s[0];
        if (frac >= 1) return s[s.length - 1];
        float pos = frac * (s.length - 1);
        int i = (int) pos;
        float t = pos - i;
        int c0 = s[i], c1 = s[Math.min(s.length - 1, i + 1)];
        int r = (int) (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t);
        int g = (int) (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t);
        int b = (int) (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t);
        return Color.rgb(r, g, b);
    }
}
