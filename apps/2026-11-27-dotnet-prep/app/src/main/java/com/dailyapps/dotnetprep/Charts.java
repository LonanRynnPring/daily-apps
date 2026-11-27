package com.dailyapps.dotnetprep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Small custom-drawn views: mastery bars, activity heatmap, countdown ring. */
final class Charts {
    private Charts() {}

    /** Horizontal stacked bars per topic: mastered / learning / new. */
    static class MasteryView extends View {
        static final class Row {
            String name;
            int mastered, learning, total;
        }

        private final List<Row> rows = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float dp;

        MasteryView(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            t.setTextSize(13 * dp);
        }

        void setRows(List<Row> list) {
            rows.clear();
            rows.addAll(list);
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, (int) (rows.size() * 44 * dp + 30 * dp));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float y = 4 * dp;
            for (Row row : rows) {
                t.setColor(Theme.TEXT);
                t.setTextAlign(Paint.Align.LEFT);
                c.drawText(row.name, 0, y + 13 * dp, t);
                t.setColor(Theme.MUTED);
                t.setTextAlign(Paint.Align.RIGHT);
                int pct = row.total == 0 ? 0 : Math.round(100f * row.mastered / row.total);
                c.drawText(pct + "%  (" + row.mastered + "/" + row.total + ")", w, y + 13 * dp, t);
                float by = y + 20 * dp, bh = 12 * dp;
                r.set(0, by, w, by + bh);
                p.setColor(Theme.PANEL2);
                c.drawRoundRect(r, bh / 2, bh / 2, p);
                if (row.total > 0) {
                    float lw = w * (row.mastered + row.learning) / row.total;
                    if (lw > 0) {
                        r.set(0, by, Math.max(bh, lw), by + bh);
                        p.setColor(0xFF3E5C8A);
                        c.drawRoundRect(r, bh / 2, bh / 2, p);
                    }
                    float mw = w * row.mastered / row.total;
                    if (mw > 0) {
                        r.set(0, by, Math.max(bh, mw), by + bh);
                        p.setColor(Theme.ACCENT);
                        c.drawRoundRect(r, bh / 2, bh / 2, p);
                    }
                }
                y += 44 * dp;
            }
            // legend
            float lx = 0, ly = y + 12 * dp;
            int[] cols = {Theme.ACCENT, 0xFF3E5C8A, Theme.PANEL2};
            String[] names = {"Mastered", "Learning", "Not seen"};
            t.setTextAlign(Paint.Align.LEFT);
            t.setColor(Theme.MUTED);
            for (int i = 0; i < 3; i++) {
                p.setColor(cols[i]);
                c.drawCircle(lx + 5 * dp, ly - 4 * dp, 5 * dp, p);
                c.drawText(names[i], lx + 14 * dp, ly, t);
                lx += 14 * dp + t.measureText(names[i]) + 16 * dp;
            }
        }
    }

    /** GitHub-style grid of the last 16 weeks of study activity. */
    static class HeatmapView extends View {
        private Progress progress;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float dp;
        static final int WEEKS = 16;

        HeatmapView(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
        }

        void setProgress(Progress pr) {
            progress = pr;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            float cell = width / (float) WEEKS;
            setMeasuredDimension(width, (int) (cell * 7));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (progress == null) return;
            float cell = getWidth() / (float) WEEKS;
            float gap = Math.max(2, cell * 0.14f);
            int today = Progress.today();
            // day 0 (1970-01-01) was a Thursday; make Monday row 0
            int dow = ((today + 3) % 7 + 7) % 7;
            int start = today - dow - (WEEKS - 1) * 7;
            for (int wk = 0; wk < WEEKS; wk++) {
                for (int d = 0; d < 7; d++) {
                    int day = start + wk * 7 + d;
                    if (day > today) continue;
                    int n = progress.activityOn(day);
                    int col;
                    if (n == 0) col = Theme.PANEL2;
                    else if (n < 10) col = 0xFF1E4D3B;
                    else if (n < 25) col = 0xFF2B7A57;
                    else if (n < 50) col = 0xFF3DAA78;
                    else col = 0xFF5FE0A0;
                    p.setColor(col);
                    r.set(wk * cell + gap / 2, d * cell + gap / 2, (wk + 1) * cell - gap / 2, (d + 1) * cell - gap / 2);
                    c.drawRoundRect(r, 3 * dp, 3 * dp, p);
                    if (day == today) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(1.5f * dp);
                        p.setColor(Theme.TEXT);
                        c.drawRoundRect(r, 3 * dp, 3 * dp, p);
                        p.setStyle(Paint.Style.FILL);
                    }
                }
            }
        }
    }

    /** Countdown ring for the mock interview. */
    static class RingView extends View {
        private float fraction = 1f;
        private String label = "";
        private int color = Theme.ACCENT;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float dp;

        RingView(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            t.setTextAlign(Paint.Align.CENTER);
            t.setFakeBoldText(true);
            t.setColor(Theme.TEXT);
        }

        void set(float fraction, String label, int color) {
            this.fraction = fraction;
            this.label = label;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float sw = s * 0.09f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = s / 2f - sw;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setStrokeWidth(sw);
            p.setColor(Theme.PANEL2);
            c.drawArc(r, 0, 360, false, p);
            p.setColor(color);
            c.drawArc(r, -90, 360 * Math.max(0, Math.min(1, fraction)), false, p);
            t.setTextSize(s * 0.22f);
            c.drawText(label, cx, cy + s * 0.08f, t);
        }
    }
}
