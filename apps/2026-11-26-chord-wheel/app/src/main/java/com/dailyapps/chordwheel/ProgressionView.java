package com.dailyapps.chordwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Grid of chord cells showing the current progression, four per row. */
final class ProgressionView extends View {
    interface Listener {
        void onCellTap(int index);

        void onCellLongPress(int index);
    }

    private static final int COLS = 4;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final GestureDetector detector;

    private List<Theory.Item> items = new ArrayList<>();
    private int tonic;
    private boolean minor;
    private int current = -1;
    private Listener listener;

    ProgressionView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        detector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0 && listener != null) listener.onCellTap(i);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0 && listener != null) listener.onCellLongPress(i);
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setData(List<Theory.Item> items, int tonic, boolean minor) {
        this.items = items;
        this.tonic = tonic;
        this.minor = minor;
        requestLayout();
        invalidate();
    }

    void setCurrent(int i) {
        current = i;
        invalidate();
    }

    private float cellH() {
        return 62 * density;
    }

    private float gap() {
        return 6 * density;
    }

    private int rows() {
        return Math.max(1, (items.size() + COLS - 1) / COLS);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = (int) (rows() * (cellH() + gap()) + gap());
        setMeasuredDimension(w, h);
    }

    private int indexAt(float x, float y) {
        float cw = (getWidth() - gap() * (COLS + 1)) / COLS;
        int col = (int) ((x - gap() / 2f) / (cw + gap()));
        int row = (int) ((y - gap() / 2f) / (cellH() + gap()));
        if (col < 0 || col >= COLS || row < 0) return -1;
        int i = row * COLS + col;
        return i < items.size() ? i : -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float g = gap();
        float cw = (getWidth() - g * (COLS + 1)) / COLS;
        float ch = cellH();
        if (items.isEmpty()) {
            fill.setColor(Color.rgb(29, 31, 43));
            rect.set(g, g, getWidth() - g, g + ch);
            canvas.drawRoundRect(rect, 12 * density, 12 * density, fill);
            text.setColor(Color.rgb(138, 143, 163));
            text.setTextSize(14 * density);
            text.setFakeBoldText(false);
            canvas.drawText("Tap chords below to write a progression", getWidth() / 2f,
                    g + ch / 2f + 5 * density, text);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Theory.Item it = items.get(i);
            int row = i / COLS, col = i % COLS;
            float x = g + col * (cw + g);
            float y = g + row * (ch + g);
            rect.set(x, y, x + cw, y + ch);
            int q = Theory.quality(minor, it.degree);
            int base;
            if (q == Theory.MAJ) base = Color.rgb(46, 74, 120);
            else if (q == Theory.MIN) base = Color.rgb(78, 52, 110);
            else base = Color.rgb(96, 60, 44);
            boolean cur = i == current;
            fill.setColor(cur ? Color.rgb(255, 184, 77) : base);
            canvas.drawRoundRect(rect, 12 * density, 12 * density, fill);
            int fg = cur ? Color.rgb(20, 20, 28) : Color.WHITE;
            text.setColor(fg);
            text.setFakeBoldText(true);
            String rn = Theory.roman(minor, it.degree, it.seventh);
            text.setTextSize((rn.length() > 4 ? 15 : 19) * density);
            canvas.drawText(rn, x + cw / 2f, y + ch * 0.45f, text);
            text.setFakeBoldText(false);
            text.setTextSize(13 * density);
            text.setColor(cur ? Color.rgb(50, 40, 20) : Color.argb(200, 255, 255, 255));
            canvas.drawText(Theory.chordName(tonic, minor, it.degree, it.seventh),
                    x + cw / 2f, y + ch * 0.8f, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return detector.onTouchEvent(e) || super.onTouchEvent(e);
    }
}
