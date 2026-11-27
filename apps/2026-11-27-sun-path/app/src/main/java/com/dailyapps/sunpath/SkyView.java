package com.dailyapps.sunpath;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Polar sky dome: zenith in the centre, horizon at the rim, north up. */
class SkyView extends View {
    interface Listener { void onPickTime(long ms); }

    static final int STEP_MIN = 4;
    static final int SAMPLES = 24 * 60 / STEP_MIN + 1;

    private Listener listener;
    private double lat, lon;
    private long dayStart;
    private final float[] dayAlt = new float[SAMPLES], dayAz = new float[SAMPLES];
    private final float[] junAlt = new float[SAMPLES], junAz = new float[SAMPLES];
    private final float[] decAlt = new float[SAMPLES], decAz = new float[SAMPLES];
    private final float[] eqAlt = new float[SAMPLES], eqAz = new float[SAMPLES];
    private SunCalc.Times times;
    private boolean hasDay;
    private double sunAlt, sunAz, moonAlt, moonAz, moonPhase;
    private SimpleDateFormat fmt, hourFmt;

    private final float dp;
    private float cx, cy, radius;
    private final float[] pt = new float[2];
    private final Path path = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);

    SkyView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1 * dp);
        grid.setColor(0x40FFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2 * dp);
        rim.setColor(0x99FFFFFF);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(4 * dp);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeJoin(Paint.Join.ROUND);
        track.setColor(Draw.ACCENT);
        faint.setStyle(Paint.Style.STROKE);
        faint.setStrokeWidth(1.5f * dp);
        faint.setPathEffect(new DashPathEffect(new float[]{6 * dp, 5 * dp}, 0));
        text.setColor(Draw.TEXT);
        text.setTextSize(15 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        small.setColor(Draw.MUTED);
        small.setTextSize(11 * dp);
        small.setTextAlign(Paint.Align.CENTER);
        glow.setColor(0x55FFD25A);
        lit.setColor(Draw.MOON);
        dark.setColor(0xFF2A2F3A);
    }

    void setListener(Listener l) { listener = l; }

    void setDay(double lat, double lon, TimeZone tz, long dayStart, int year, SunCalc.Times t, boolean use24) {
        this.lat = lat;
        this.lon = lon;
        this.dayStart = dayStart;
        this.times = t;
        fmt = new SimpleDateFormat(use24 ? "HH:mm" : "h:mm a", Locale.getDefault());
        fmt.setTimeZone(tz);
        hourFmt = new SimpleDateFormat(use24 ? "H" : "ha", Locale.getDefault());
        hourFmt.setTimeZone(tz);
        sample(dayStart, dayAlt, dayAz);
        sample(dayOf(tz, year, Calendar.JUNE, 21), junAlt, junAz);
        sample(dayOf(tz, year, Calendar.DECEMBER, 21), decAlt, decAz);
        sample(dayOf(tz, year, Calendar.MARCH, 20), eqAlt, eqAz);
        hasDay = true;
        invalidate();
    }

    float[] dayAltitudes() { return dayAlt; }
    float[] dayAzimuths() { return dayAz; }

    static long dayOf(TimeZone tz, int y, int m, int d) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, m, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    private void sample(long start, float[] alt, float[] az) {
        for (int i = 0; i < SAMPLES; i++) {
            double[] p = SunCalc.sunPosition(start + i * STEP_MIN * 60000L, lat, lon);
            alt[i] = (float) p[0];
            az[i] = (float) p[1];
        }
    }

    void setNow(double sunAlt, double sunAz, double moonAlt, double moonAz, double moonPhase) {
        this.sunAlt = sunAlt;
        this.sunAz = sunAz;
        this.moonAlt = moonAlt;
        this.moonAz = moonAz;
        this.moonPhase = moonPhase;
        invalidate();
    }

    private void project(double alt, double az) {
        double r = radius * (90.0 - Math.max(0, alt)) / 90.0;
        double a = Math.toRadians(az);
        pt[0] = (float) (cx + r * Math.sin(a));
        pt[1] = (float) (cy - r * Math.cos(a));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) / 2f - 34 * dp;
        c.drawColor(Draw.BG);
        if (!hasDay) return;

        fill.setColor(Draw.skyColor(sunAlt));
        c.drawCircle(cx, cy, radius, fill);
        c.drawCircle(cx, cy, radius * 2 / 3f, grid);
        c.drawCircle(cx, cy, radius / 3f, grid);
        c.drawLine(cx - radius, cy, cx + radius, cy, grid);
        c.drawLine(cx, cy - radius, cx, cy + radius, grid);
        c.drawCircle(cx, cy, radius, rim);
        small.setColor(0x80FFFFFF);
        c.drawText("30°", cx + 4 * dp + 10 * dp, cy - radius * 2 / 3f + 12 * dp, small);
        c.drawText("60°", cx + 4 * dp + 10 * dp, cy - radius / 3f + 12 * dp, small);
        small.setColor(Draw.MUTED);

        for (int a = 0; a < 360; a += 15) {
            double rr = Math.toRadians(a);
            float len = (a % 45 == 0) ? 10 * dp : 5 * dp;
            float sx = (float) (cx + radius * Math.sin(rr)), sy = (float) (cy - radius * Math.cos(rr));
            float ex = (float) (cx + (radius + len) * Math.sin(rr)), ey = (float) (cy - (radius + len) * Math.cos(rr));
            c.drawLine(sx, sy, ex, ey, rim);
        }
        String[] lbl = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double rr = Math.toRadians(i * 90);
            float lx = (float) (cx + (radius + 22 * dp) * Math.sin(rr));
            float ly = (float) (cy - (radius + 22 * dp) * Math.cos(rr)) + 5 * dp;
            text.setColor(i == 0 ? 0xFFFF7B72 : Draw.TEXT);
            c.drawText(lbl[i], lx, ly, text);
        }
        text.setColor(Draw.TEXT);

        faint.setColor(0x99FFB86B);
        drawTrack(c, junAlt, junAz, faint);
        labelMax(c, junAlt, junAz, "Jun 21");
        faint.setColor(0x8890A8FF);
        drawTrack(c, decAlt, decAz, faint);
        labelMax(c, decAlt, decAz, "Dec 21");
        faint.setColor(0x55FFFFFF);
        drawTrack(c, eqAlt, eqAz, faint);

        drawTrack(c, dayAlt, dayAz, track);

        // hour marks along today's path
        int perHour = 60 / STEP_MIN;
        dot.setColor(Draw.TEXT);
        small.setColor(Draw.TEXT);
        for (int i = 0; i < SAMPLES; i += perHour) {
            if (dayAlt[i] <= 0) continue;
            project(dayAlt[i], dayAz[i]);
            c.drawCircle(pt[0], pt[1], 3 * dp, dot);
            float ox = pt[0] - cx, oy = pt[1] - cy;
            float len = (float) Math.max(1, Math.hypot(ox, oy));
            float lx = pt[0] + ox / len * 13 * dp, ly = pt[1] + oy / len * 13 * dp + 4 * dp;
            if (len < 20 * dp) { lx = pt[0]; ly = pt[1] - 8 * dp; }
            c.drawText(hourFmt.format(new Date(dayStart + i * STEP_MIN * 60000L)), lx, ly, small);
        }
        small.setColor(Draw.MUTED);

        // sunrise / sunset bearings
        if (times != null) {
            horizonMark(c, times.rise[SunCalc.SUNRISE]);
            horizonMark(c, times.set[SunCalc.SUNRISE]);
        }

        if (moonAlt > 0) {
            project(moonAlt, moonAz);
            Draw.moon(c, pt[0], pt[1], 9 * dp, moonPhase, lit, dark);
        }
        if (sunAlt > 0) {
            project(sunAlt, sunAz);
            c.drawCircle(pt[0], pt[1], 20 * dp, glow);
            dot.setColor(Draw.SUN);
            c.drawCircle(pt[0], pt[1], 10 * dp, dot);
        } else {
            small.setColor(Draw.TEXT);
            c.drawText("Sun below horizon (" + String.format(Locale.getDefault(), "%.1f", sunAlt) + "°)",
                    cx, cy + radius + 30 * dp > h ? h - 4 * dp : cy + radius + 30 * dp, small);
            small.setColor(Draw.MUTED);
        }
    }

    private void horizonMark(Canvas c, long ms) {
        if (ms == SunCalc.NONE) return;
        double az = SunCalc.sunPosition(ms, lat, lon)[1];
        double rr = Math.toRadians(az);
        float sx = (float) (cx + (radius - 10 * dp) * Math.sin(rr)), sy = (float) (cy - (radius - 10 * dp) * Math.cos(rr));
        float ex = (float) (cx + (radius + 6 * dp) * Math.sin(rr)), ey = (float) (cy - (radius + 6 * dp) * Math.cos(rr));
        dot.setColor(Draw.GOLD);
        dot.setStrokeWidth(3 * dp);
        c.drawLine(sx, sy, ex, ey, dot);
        float lx = (float) (cx + (radius - 26 * dp) * Math.sin(rr));
        float ly = (float) (cy - (radius - 26 * dp) * Math.cos(rr)) + 4 * dp;
        small.setColor(Draw.GOLD);
        c.drawText(fmt.format(new Date(ms)), lx, ly, small);
        small.setColor(Draw.MUTED);
    }

    private void labelMax(Canvas c, float[] alt, float[] az, String s) {
        int best = 0;
        for (int i = 1; i < SAMPLES; i++) if (alt[i] > alt[best]) best = i;
        if (alt[best] <= 2) return;
        project(alt[best], az[best]);
        small.setColor(faint.getColor() | 0xFF000000);
        c.drawText(s, pt[0], pt[1] - 6 * dp, small);
        small.setColor(Draw.MUTED);
    }

    private void drawTrack(Canvas c, float[] alt, float[] az, Paint p) {
        path.reset();
        boolean pen = false;
        boolean any = false;
        for (int i = 0; i < SAMPLES; i++) {
            if (alt[i] > 0) {
                if (!pen) {
                    if (i > 0) {
                        project(0, az[i - 1]);
                        path.moveTo(pt[0], pt[1]);
                        project(alt[i], az[i]);
                        path.lineTo(pt[0], pt[1]);
                    } else {
                        project(alt[i], az[i]);
                        path.moveTo(pt[0], pt[1]);
                    }
                    pen = true;
                } else {
                    project(alt[i], az[i]);
                    path.lineTo(pt[0], pt[1]);
                }
                any = true;
            } else if (pen) {
                project(0, az[i]);
                path.lineTo(pt[0], pt[1]);
                pen = false;
            }
        }
        if (any) c.drawPath(path, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!hasDay) return false;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            int best = -1;
            float bestD = 56 * dp;
            for (int i = 0; i < SAMPLES; i++) {
                if (dayAlt[i] <= 0) continue;
                project(dayAlt[i], dayAz[i]);
                float d = (float) Math.hypot(pt[0] - e.getX(), pt[1] - e.getY());
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best >= 0) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onPickTime(dayStart + best * STEP_MIN * 60000L);
            }
            return true;
        }
        return a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
    }
}
