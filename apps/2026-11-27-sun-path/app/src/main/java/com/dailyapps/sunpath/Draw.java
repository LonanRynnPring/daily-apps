package com.dailyapps.sunpath;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Shared drawing helpers and colours. */
final class Draw {
    static final int BG = 0xFF0E1116;
    static final int PANEL = 0xFF161B22;
    static final int TEXT = 0xFFE6EDF3;
    static final int MUTED = 0xFF8B949E;
    static final int ACCENT = 0xFFF2B33D;
    static final int SUN = 0xFFFFD25A;
    static final int GOLD = 0xFFE8913A;
    static final int BLUE = 0xFF5B7BD5;
    static final int MOON = 0xFFD9DEE8;

    private Draw() {}

    /** Sky colour for a given sun altitude. */
    static int skyColor(double alt) {
        if (alt > 6) return 0xFF1F4F8F;
        if (alt > 0) return 0xFF7A4E2C;
        if (alt > -4) return 0xFF4A3B5E;
        if (alt > -6) return 0xFF2B3A73;
        if (alt > -12) return 0xFF1A2349;
        if (alt > -18) return 0xFF111632;
        return 0xFF080A14;
    }

    static String lightName(double alt) {
        if (alt > 6) return "Daylight";
        if (alt > -0.833) return "Golden hour";
        if (alt > -4) return "Golden hour (twilight)";
        if (alt > -6) return "Blue hour";
        if (alt > -12) return "Nautical twilight";
        if (alt > -18) return "Astronomical twilight";
        return "Night";
    }

    private static final RectF OVAL = new RectF();

    /** Draws a moon disc showing the given phase (0 new, 0.5 full). */
    static void moon(Canvas c, float cx, float cy, float r, double phase, Paint lit, Paint dark) {
        c.drawCircle(cx, cy, r, dark);
        boolean waxing = phase < 0.5;
        OVAL.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(OVAL, waxing ? -90f : 90f, 180f, true, lit);
        double k = Math.cos(2 * Math.PI * phase);
        float w = (float) (r * Math.abs(k));
        OVAL.set(cx - w, cy - r, cx + w, cy + r);
        c.drawOval(OVAL, k > 0 ? dark : lit);
    }
}
