package com.dailyapps.sunpath;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Locale;

/**
 * Point the back of the phone at the sky: shows a window onto the sky
 * with the horizon, compass bearings, today's sun path and where the
 * sun and moon are at the selected time.
 */
class AimView extends View {
    private static final float FOV = 60f;

    private boolean hasSensor = true, haveCam;
    private float camAz, camEl;
    private double sunAlt, sunAz, moonAlt, moonAz, moonPhase;
    private float[] pathAlt, pathAz;
    private String timeText = "";
    private boolean onTarget;

    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] pt = new float[2];
    private float cx, cy, ppd;

    AimView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * dp);
        text.setTextSize(12 * dp);
        text.setColor(Draw.TEXT);
        text.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(17 * dp);
        big.setColor(Draw.TEXT);
        big.setFakeBoldText(true);
        big.setTextAlign(Paint.Align.CENTER);
        lit.setColor(Draw.MOON);
        dark.setColor(0xFF2A2F3A);
    }

    void setHasSensor(boolean b) { hasSensor = b; invalidate(); }

    void setCamera(float az, float el) {
        camAz = (float) SunCalc.norm360(az);
        camEl = el;
        haveCam = true;
        checkTarget();
        invalidate();
    }

    void setTargets(double sunAlt, double sunAz, double moonAlt, double moonAz, double phase,
                    float[] pathAlt, float[] pathAz, String timeText) {
        this.sunAlt = sunAlt;
        this.sunAz = sunAz;
        this.moonAlt = moonAlt;
        this.moonAz = moonAz;
        this.moonPhase = phase;
        this.pathAlt = pathAlt;
        this.pathAz = pathAz;
        this.timeText = timeText;
        invalidate();
    }

    private static double angDist(double alt1, double az1, double alt2, double az2) {
        double a1 = Math.toRadians(alt1), a2 = Math.toRadians(alt2), dz = Math.toRadians(az1 - az2);
        double cos = Math.sin(a1) * Math.sin(a2) + Math.cos(a1) * Math.cos(a2) * Math.cos(dz);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    private void checkTarget() {
        double d = angDist(camEl, camAz, sunAlt, sunAz);
        if (!onTarget && d < 3) {
            onTarget = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else if (onTarget && d > 6) {
            onTarget = false;
        }
    }

    private static float wrap(double a) {
        a = a % 360.0;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return (float) a;
    }

    private void project(double alt, double az) {
        pt[0] = cx + wrap(az - camAz) * ppd;
        pt[1] = cy - (float) (alt - camEl) * ppd;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        cx = w / 2f;
        cy = h / 2f;
        ppd = w / FOV;
        c.drawColor(Draw.BG);
        if (!hasSensor) {
            big.setColor(Draw.MUTED);
            c.drawText("No orientation sensor on this device", cx, cy, big);
            big.setColor(Draw.TEXT);
            return;
        }
        if (!haveCam) {
            text.setColor(Draw.MUTED);
            c.drawText("Waiting for sensor…", cx, cy, text);
            text.setColor(Draw.TEXT);
            return;
        }

        float horizonY = cy + camEl * ppd;
        fill.setColor(Draw.skyColor(sunAlt));
        c.drawRect(0, 0, w, Math.max(0, Math.min(h, horizonY)), fill);
        fill.setColor(0xFF12201A);
        if (horizonY < h) c.drawRect(0, Math.max(0, horizonY), w, h, fill);
        line.setColor(0xCCFFFFFF);
        c.drawLine(0, horizonY, w, horizonY, line);

        // compass ticks along the horizon
        int start = (int) Math.floor((camAz - FOV) / 5f) * 5;
        for (int a = start; a <= camAz + FOV; a += 5) {
            float x = cx + wrap(a - camAz) * ppd;
            if (x < -20 || x > w + 20) continue;
            int an = (int) SunCalc.norm360(a);
            float len = an % 45 == 0 ? 14 * dp : (an % 15 == 0 ? 9 * dp : 5 * dp);
            c.drawLine(x, horizonY, x, horizonY + len, line);
            if (an % 45 == 0) {
                text.setColor(an == 0 ? 0xFFFF7B72 : Draw.TEXT);
                c.drawText(SunCalc.compassPoint(an), x, horizonY + 28 * dp, text);
            } else if (an % 15 == 0) {
                text.setColor(Draw.MUTED);
                c.drawText(String.valueOf(an), x, horizonY + 26 * dp, text);
            }
        }
        text.setColor(Draw.TEXT);

        // altitude ticks up the centre
        line.setColor(0x55FFFFFF);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(Draw.MUTED);
        for (int el = -80; el <= 90; el += 10) {
            if (el == 0) continue;
            float y = cy - (el - camEl) * ppd;
            if (y < 0 || y > h) continue;
            c.drawLine(cx - 6 * dp, y, cx + 6 * dp, y, line);
            c.drawText(el + "°", cx + 9 * dp, y + 4 * dp, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Draw.TEXT);

        // today's sun path
        if (pathAlt != null) {
            path.reset();
            boolean pen = false;
            float px = 0;
            for (int i = 0; i < pathAlt.length; i++) {
                project(pathAlt[i], pathAz[i]);
                if (pen && Math.abs(pt[0] - px) < w) path.lineTo(pt[0], pt[1]);
                else path.moveTo(pt[0], pt[1]);
                pen = true;
                px = pt[0];
            }
            line.setColor(0xAAF2B33D);
            line.setStrokeWidth(3 * dp);
            c.drawPath(path, line);
            line.setStrokeWidth(1.5f * dp);
            int perHour = 60 / SkyView.STEP_MIN;
            fill.setColor(0xCCFFFFFF);
            for (int i = 0; i < pathAlt.length; i += perHour) {
                project(pathAlt[i], pathAz[i]);
                if (pt[0] < 0 || pt[0] > w || pt[1] < 0 || pt[1] > h) continue;
                c.drawCircle(pt[0], pt[1], 3 * dp, fill);
                c.drawText(String.valueOf(i / perHour), pt[0], pt[1] - 7 * dp, text);
            }
        }

        // moon
        project(moonAlt, moonAz);
        if (onScreen(w, h)) {
            Draw.moon(c, pt[0], pt[1], 14 * dp, moonPhase, lit, dark);
        } else {
            edgeArrow(c, w, h, Draw.MOON, "Moon");
        }

        // sun
        project(sunAlt, sunAz);
        if (onScreen(w, h)) {
            fill.setColor(sunAlt > 0 ? 0x55FFD25A : 0x33FFD25A);
            c.drawCircle(pt[0], pt[1], 30 * dp, fill);
            fill.setColor(sunAlt > 0 ? Draw.SUN : 0x88FFD25A);
            c.drawCircle(pt[0], pt[1], 15 * dp, fill);
        } else {
            edgeArrow(c, w, h, Draw.SUN, "Sun");
        }

        // crosshair
        line.setColor(onTarget ? 0xFF7EE787 : 0xCCFFFFFF);
        line.setStrokeWidth(2 * dp);
        c.drawCircle(cx, cy, 22 * dp, line);
        c.drawLine(cx - 34 * dp, cy, cx - 12 * dp, cy, line);
        c.drawLine(cx + 12 * dp, cy, cx + 34 * dp, cy, line);
        c.drawLine(cx, cy - 34 * dp, cx, cy - 12 * dp, line);
        c.drawLine(cx, cy + 12 * dp, cx, cy + 34 * dp, line);
        line.setStrokeWidth(1.5f * dp);

        // header and guidance
        fill.setColor(0xCC0E1116);
        c.drawRect(0, 0, w, 54 * dp, fill);
        c.drawRect(0, h - 60 * dp, w, h, fill);
        text.setColor(Draw.MUTED);
        c.drawText(String.format(Locale.getDefault(), "Pointing %.0f° %s, %+.0f°   ·   %s",
                camAz, SunCalc.compassPoint(camAz), camEl, timeText), cx, 20 * dp, text);
        c.drawText(String.format(Locale.getDefault(), "Sun %.0f° %s, %+.1f°   Moon %.0f° %s, %+.1f°",
                sunAz, SunCalc.compassPoint(sunAz), sunAlt, moonAz, SunCalc.compassPoint(moonAz), moonAlt), cx, 40 * dp, text);
        text.setColor(Draw.TEXT);

        String guide;
        if (onTarget) {
            guide = "✓ On the sun's position";
            big.setColor(0xFF7EE787);
        } else {
            float dz = wrap(sunAz - camAz);
            float de = (float) (sunAlt - camEl);
            guide = (dz >= 0 ? "Turn right " : "Turn left ") + Math.round(Math.abs(dz)) + "°   ·   "
                    + (de >= 0 ? "Tilt up " : "Tilt down ") + Math.round(Math.abs(de)) + "°";
            big.setColor(Draw.TEXT);
        }
        c.drawText(guide, cx, h - 34 * dp, big);
        big.setColor(Draw.TEXT);
        text.setColor(Draw.MUTED);
        c.drawText(camEl < -60 ? "Raise the phone upright and point its back at the sky"
                : "Bearings corrected to true north", cx, h - 14 * dp, text);
        text.setColor(Draw.TEXT);
    }

    private boolean onScreen(int w, int h) {
        return pt[0] >= 0 && pt[0] <= w && pt[1] >= 54 * dp && pt[1] <= h - 60 * dp;
    }

    private void edgeArrow(Canvas c, int w, int h, int color, String label) {
        float dx = pt[0] - cx, dy = pt[1] - cy;
        double ang = Math.atan2(dy, dx);
        float hw = w / 2f - 28 * dp, hh = (h - 114 * dp) / 2f - 20 * dp;
        float cyMid = 54 * dp + (h - 114 * dp) / 2f;
        double sx = Math.abs(Math.cos(ang)) < 1e-6 ? 1e9 : hw / Math.abs(Math.cos(ang));
        double sy = Math.abs(Math.sin(ang)) < 1e-6 ? 1e9 : hh / Math.abs(Math.sin(ang));
        double s = Math.min(sx, sy);
        float ax = (float) (cx + Math.cos(ang) * s), ay = (float) (cyMid + Math.sin(ang) * s);
        path.reset();
        float tip = 16 * dp, back = 9 * dp;
        path.moveTo((float) (ax + Math.cos(ang) * tip), (float) (ay + Math.sin(ang) * tip));
        path.lineTo((float) (ax + Math.cos(ang + 2.4) * back), (float) (ay + Math.sin(ang + 2.4) * back));
        path.lineTo((float) (ax + Math.cos(ang - 2.4) * back), (float) (ay + Math.sin(ang - 2.4) * back));
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
        text.setColor(color);
        c.drawText(label, (float) (ax - Math.cos(ang) * 16 * dp), (float) (ay - Math.sin(ang) * 16 * dp) + 4 * dp, text);
        text.setColor(Draw.TEXT);
    }
}
