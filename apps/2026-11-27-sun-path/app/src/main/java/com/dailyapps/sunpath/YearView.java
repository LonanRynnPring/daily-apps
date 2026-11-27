package com.dailyapps.sunpath;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Whole-year daylight chart: day of year across, local time of day down. */
class YearView extends View {
    interface Listener { void onPickDay(int year, int month, int day); }

    // levels drawn outermost first
    private static final int[] LEVEL_ANGLE = {SunCalc.ASTRO, SunCalc.NAUTICAL, SunCalc.CIVIL,
            SunCalc.SUNRISE, SunCalc.GOLDEN};
    private static final int[] LEVEL_COLOR = {0xFF1B2550, 0xFF283A75, 0xFF3D56A3, 0xFFD0843A, 0xFFF1CC74};
    private static final String[] LEVEL_NAME = {"Astro", "Nautical", "Civil", "Golden", "Sun > 6°"};

    private Listener listener;
    private int year, days;
    private float[][] riseM = new float[5][366], setM = new float[5][366];
    private final float[] noonM = new float[366];
    private final float[] lenM = new float[366];
    private final int[] monthStart = new int[12];
    private final int[] dayMonth = new int[366], dayDom = new int[366];
    private int selected = -1;
    private String stats = "";

    private final float dp;
    private final Paint fill = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float left, top, pw, ph;

    YearView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1 * dp);
        text.setTextSize(11 * dp);
        text.setColor(Draw.MUTED);
    }

    void setListener(Listener l) { listener = l; }

    void setYear(double lat, double lon, TimeZone tz, int year) {
        this.year = year;
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        days = c.getActualMaximum(Calendar.DAY_OF_YEAR);
        int longest = 0, shortest = 0;
        for (int d = 0; d < days; d++) {
            long start = c.getTimeInMillis();
            dayMonth[d] = c.get(Calendar.MONTH);
            dayDom[d] = c.get(Calendar.DAY_OF_MONTH);
            if (dayDom[d] == 1) monthStart[dayMonth[d]] = d;
            SunCalc.Times t = SunCalc.times(start + 12 * 3600000L, lat, lon);
            noonM[d] = (t.noon - start) / 60000f;
            for (int l = 0; l < 5; l++) {
                int ai = LEVEL_ANGLE[l];
                if (t.rise[ai] == SunCalc.NONE) {
                    if (t.noonAlt > SunCalc.ANGLES[ai]) {
                        riseM[l][d] = 0;
                        setM[l][d] = 1440;
                    } else {
                        riseM[l][d] = Float.NaN;
                        setM[l][d] = Float.NaN;
                    }
                } else {
                    riseM[l][d] = (t.rise[ai] - start) / 60000f;
                    setM[l][d] = (t.set[ai] - start) / 60000f;
                }
            }
            float len = Float.isNaN(riseM[3][d]) ? 0 : setM[3][d] - riseM[3][d];
            lenM[d] = Math.max(0, Math.min(1440, len));
            if (lenM[d] > lenM[longest]) longest = d;
            if (lenM[d] < lenM[shortest]) shortest = d;
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        stats = "Longest " + MainActivity.duration(lenM[longest]) + " (" + dateName(longest) + ")   Shortest "
                + MainActivity.duration(lenM[shortest]) + " (" + dateName(shortest) + ")";
        invalidate();
    }

    private String dateName(int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2001, dayMonth[d], dayDom[d]);
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(c.getTime());
    }

    void setSelected(int y, int month, int dom) {
        selected = -1;
        if (y == year) {
            for (int d = 0; d < days; d++) {
                if (dayMonth[d] == month && dayDom[d] == dom) { selected = d; break; }
            }
        }
        invalidate();
    }

    private float yOf(float min) {
        min = Math.max(0, Math.min(1440, min));
        return top + ph * min / 1440f;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Draw.BG);
        if (days == 0) return;
        left = 34 * dp;
        top = 46 * dp;
        float right = getWidth() - 10 * dp;
        float bottom = getHeight() - 52 * dp;
        pw = right - left;
        ph = bottom - top;

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(Draw.TEXT);
        text.setTextSize(12 * dp);
        c.drawText(stats, left, 18 * dp, text);
        text.setTextSize(11 * dp);
        text.setColor(Draw.MUTED);

        fill.setColor(0xFF080A14);
        c.drawRect(left, top, right, bottom, fill);
        float colW = pw / days;
        for (int d = 0; d < days; d++) {
            float x0 = left + d * colW, x1 = x0 + colW + 1;
            for (int l = 0; l < 5; l++) {
                float r = riseM[l][d], s = setM[l][d];
                if (Float.isNaN(r)) continue;
                fill.setColor(LEVEL_COLOR[l]);
                if (s - r >= 1440) {
                    c.drawRect(x0, top, x1, bottom, fill);
                } else {
                    c.drawRect(x0, yOf(r), x1, yOf(s), fill);
                }
            }
        }

        // hour grid
        line.setColor(0x33FFFFFF);
        text.setTextAlign(Paint.Align.RIGHT);
        for (int hr = 0; hr <= 24; hr += 3) {
            float y = top + ph * hr / 24f;
            c.drawLine(left, y, right, y, line);
            if (hr % 6 == 0) c.drawText(String.format(Locale.US, "%02d", hr), left - 5 * dp, y + 4 * dp, text);
        }
        // months
        text.setTextAlign(Paint.Align.CENTER);
        String[] mn = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
        for (int m = 0; m < 12; m++) {
            float x = left + monthStart[m] * colW;
            c.drawLine(x, top, x, bottom, line);
            int next = m < 11 ? monthStart[m + 1] : days;
            c.drawText(mn[m], left + (monthStart[m] + next) / 2f * colW, bottom + 14 * dp, text);
        }
        // solar noon curve
        path.reset();
        for (int d = 0; d < days; d++) {
            float x = left + (d + 0.5f) * colW, y = yOf(noonM[d]);
            if (d == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        line.setColor(0x99FFFFFF);
        c.drawPath(path, line);

        // legend
        float lx = left;
        float ly = bottom + 34 * dp;
        text.setTextAlign(Paint.Align.LEFT);
        for (int l = 0; l < 5; l++) {
            fill.setColor(LEVEL_COLOR[l]);
            c.drawRect(lx, ly - 9 * dp, lx + 10 * dp, ly + 1 * dp, fill);
            c.drawText(LEVEL_NAME[l], lx + 13 * dp, ly, text);
            lx += 13 * dp + text.measureText(LEVEL_NAME[l]) + 10 * dp;
        }

        if (selected >= 0) {
            float x = left + (selected + 0.5f) * colW;
            line.setColor(0xFFFF7B72);
            line.setStrokeWidth(2 * dp);
            c.drawLine(x, top - 6 * dp, x, bottom, line);
            line.setStrokeWidth(1 * dp);
            text.setColor(0xFFFF7B72);
            String s = dateName(selected) + ": " + MainActivity.duration(lenM[selected]) + " of daylight";
            float tw = text.measureText(s);
            float tx = Math.max(left, Math.min(right - tw, x - tw / 2));
            c.drawText(s, tx, top - 10 * dp, text);
            text.setColor(Draw.MUTED);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (days == 0 || pw <= 0) return false;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            int d = (int) ((e.getX() - left) / (pw / days));
            d = Math.max(0, Math.min(days - 1, d));
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (d != selected) {
                selected = d;
                invalidate();
                if (listener != null) listener.onPickDay(year, dayMonth[d], dayDom[d]);
            }
            return true;
        }
        return true;
    }
}
