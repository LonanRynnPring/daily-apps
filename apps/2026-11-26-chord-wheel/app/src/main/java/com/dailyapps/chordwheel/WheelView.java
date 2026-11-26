package com.dailyapps.chordwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Interactive circle of fifths: outer ring major keys, inner ring relative minors. */
final class WheelView extends View {
    interface Listener {
        void onKeyPicked(int tonic, boolean minor);

        void onCenterTapped();
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF outerRect = new RectF();
    private final RectF innerRect = new RectF();
    private final float density;

    private Listener listener;
    private int tonic = 0;
    private boolean minor = false;
    private int hlRoot = -1;
    private int hlQuality = -1;
    private String centerTop = "";
    private String centerBottom = "";
    private float pulse = 0f;
    private long pulseStart = 0;

    WheelView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setKey(int tonic, boolean minor) {
        this.tonic = tonic;
        this.minor = minor;
        invalidate();
    }

    void setHighlight(int root, int quality, String chordName) {
        hlRoot = root;
        hlQuality = quality;
        centerBottom = chordName == null ? "" : chordName;
        pulseStart = System.currentTimeMillis();
        invalidate();
    }

    void clearHighlight() {
        hlRoot = -1;
        hlQuality = -1;
        centerBottom = "";
        invalidate();
    }

    void setCenterTop(String s) {
        centerTop = s;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int max = (int) (420 * density);
        if (w > max) w = max;
        setMeasuredDimension(w, w);
    }

    private int relMajorIndex() {
        int major = minor ? (tonic + 3) % 12 : tonic;
        for (int i = 0; i < 12; i++) if (Theory.FIFTHS[i] == major) return i;
        return 0;
    }

    private static int hueColor(int i, float sat, float val) {
        float[] hsv = {(i * 30f + 200f) % 360f, sat, val};
        return Color.HSVToColor(hsv);
    }

    private void sector(float cx, float cy, float rIn, float rOut, float startDeg, float sweep) {
        outerRect.set(cx - rOut, cy - rOut, cx + rOut, cy + rOut);
        innerRect.set(cx - rIn, cy - rIn, cx + rIn, cy + rIn);
        path.reset();
        path.arcTo(outerRect, startDeg, sweep, true);
        path.arcTo(innerRect, startDeg + sweep, -sweep, false);
        path.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float cx = w / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 4 * density;
        float rOut = r, rMid = r * 0.64f, rIn = r * 0.36f;
        int k = relMajorIndex();
        long since = System.currentTimeMillis() - pulseStart;
        pulse = since < 400 ? 1f - since / 400f : 0f;

        for (int ring = 0; ring < 2; ring++) {
            float a = ring == 0 ? rMid : rIn;
            float b = ring == 0 ? rOut : rMid;
            for (int i = 0; i < 12; i++) {
                int d = Math.abs(i - k);
                d = Math.min(d, 12 - d);
                boolean diatonic = d <= 1;
                boolean isKey = (i == k) && (ring == 0) != minor;
                float start = -90f + i * 30f - 15f + 0.8f;
                sector(cx, cy, a + 1.2f * density, b, start, 30f - 1.6f);
                int col;
                if (isKey) col = hueColor(i, 0.65f, 0.95f);
                else if (diatonic) col = hueColor(i, 0.45f, ring == 0 ? 0.62f : 0.52f);
                else col = hueColor(i, 0.25f, ring == 0 ? 0.24f : 0.19f);
                fill.setColor(col);
                canvas.drawPath(path, fill);

                int pc = ring == 0 ? Theory.FIFTHS[i] : (Theory.FIFTHS[i] + 9) % 12;
                boolean hl = hlRoot == pc
                        && ((ring == 0 && hlQuality == Theory.MAJ) || (ring == 1 && hlQuality == Theory.MIN));
                if (hl) {
                    stroke.setColor(Color.WHITE);
                    stroke.setStrokeWidth((3f + 4f * pulse) * density);
                    canvas.drawPath(path, stroke);
                }

                double ang = Math.toRadians(-90 + i * 30);
                float tr = (a + b) / 2f;
                float tx = cx + (float) Math.cos(ang) * tr;
                float ty = cy + (float) Math.sin(ang) * tr;
                boolean flats = Theory.useFlats(ring == 0 ? pc : pc, ring == 1);
                String label = Theory.noteName(pc, flats) + (ring == 1 ? "m" : "");
                if (ring == 0 && i == 6) label = "F#/Gb";
                text.setColor(diatonic || isKey ? Color.WHITE : Color.argb(150, 255, 255, 255));
                text.setFakeBoldText(isKey || hl);
                float ts = (ring == 0 ? 17f : 13f) * density;
                if (label.length() > 3) ts *= 0.72f;
                text.setTextSize(ts);
                canvas.drawText(label, tx, ty + ts * 0.35f, text);
            }
        }

        // center disc
        fill.setColor(Color.rgb(22, 24, 34));
        canvas.drawCircle(cx, cy, rIn, fill);
        stroke.setColor(hueColor(k, 0.5f, 0.8f));
        stroke.setStrokeWidth(2f * density);
        canvas.drawCircle(cx, cy, rIn - 2 * density, stroke);

        text.setFakeBoldText(true);
        text.setColor(Color.WHITE);
        boolean dimHl = hlQuality == Theory.DIM;
        if (centerBottom.length() > 0) {
            float s = rIn * (centerBottom.length() > 5 ? 0.32f : 0.42f);
            text.setTextSize(s);
            text.setColor(dimHl ? Color.rgb(255, 184, 77) : Color.WHITE);
            canvas.drawText(centerBottom, cx, cy + s * 0.3f, text);
            text.setFakeBoldText(false);
            text.setTextSize(rIn * 0.17f);
            text.setColor(Color.argb(170, 255, 255, 255));
            canvas.drawText(centerTop, cx, cy - rIn * 0.45f, text);
        } else {
            text.setTextSize(rIn * 0.26f);
            canvas.drawText(Theory.keyShort(tonic, minor), cx, cy + rIn * 0.02f, text);
            text.setFakeBoldText(false);
            text.setTextSize(rIn * 0.15f);
            text.setColor(Color.argb(170, 255, 255, 255));
            canvas.drawText(minor ? "minor key" : "major key", cx, cy + rIn * 0.32f, text);
        }
        if (pulse > 0f) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 4 * density;
        float dx = e.getX() - cx, dy = e.getY() - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (listener == null || dist > r + 8 * density) return true;
        if (dist < r * 0.36f) {
            listener.onCenterTapped();
            return true;
        }
        double deg = Math.toDegrees(Math.atan2(dx, -dy));
        if (deg < 0) deg += 360;
        int idx = ((int) Math.round(deg / 30.0)) % 12;
        if (dist >= r * 0.64f) {
            listener.onKeyPicked(Theory.FIFTHS[idx], false);
        } else {
            listener.onKeyPicked((Theory.FIFTHS[idx] + 9) % 12, true);
        }
        return true;
    }
}
