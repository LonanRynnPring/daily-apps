package com.dailyapps.echopads;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/** The four glowing pads plus all game logic. */
public class PadView extends View {
    public interface Listener {
        void onRound(int round);
        void onGameOver(int score);
        void onStatus(String text);
    }

    public static final int IDLE = 0, SHOWING = 1, INPUT = 2, OVER = 3;
    private static final int[] BASE = {0xFF1E88E5, 0xFFD81B60, 0xFF43A047, 0xFFFFB300};
    private static final int[] GLOW = {0xFF90CAF9, 0xFFF48FB1, 0xFFA5D6A7, 0xFFFFE082};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] rects = new RectF[4];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final ArrayList<Integer> seq = new ArrayList<Integer>();
    private final Tones tones;
    private final Vibrator vib;
    private Listener listener;

    public int state = IDLE;
    public boolean reverse = false;
    public boolean fast = false;
    private int lit = -1;
    private int inputPos = 0;
    private boolean flashWrong = false;
    private int gen = 0;

    public PadView(Context c, Tones tones) {
        super(c);
        this.tones = tones;
        this.vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        for (int i = 0; i < 4; i++) rects[i] = new RectF();
    }

    public void setListener(Listener l) { listener = l; }

    public int roundCount() { return seq.size(); }

    public void start() {
        gen++;
        seq.clear();
        state = SHOWING;
        flashWrong = false;
        nextRound();
    }

    public void abort() {
        gen++;
        state = IDLE;
        lit = -1;
        invalidate();
    }

    private void nextRound() {
        seq.add(rnd.nextInt(4));
        inputPos = 0;
        state = SHOWING;
        if (listener != null) {
            listener.onRound(seq.size());
            listener.onStatus("Watch…");
        }
        final int g = ++gen;
        int step = fast ? 380 : 620;
        long t = 700;
        for (int i = 0; i < seq.size(); i++) {
            final int pad = seq.get(i);
            handler.postDelayed(new Runnable() {
                public void run() { if (g == gen) light(pad, fast ? 250 : 420); }
            }, t);
            t += step;
        }
        handler.postDelayed(new Runnable() {
            public void run() {
                if (g != gen) return;
                state = INPUT;
                if (listener != null) listener.onStatus(reverse ? "Repeat it BACKWARDS" : "Your turn");
                invalidate();
            }
        }, t);
        invalidate();
    }

    private void light(int pad, int ms) {
        lit = pad;
        tones.playPad(pad);
        invalidate();
        final int g = gen;
        handler.postDelayed(new Runnable() {
            public void run() {
                if (g == gen) { lit = -1; invalidate(); }
            }
        }, ms);
    }

    private int expected() {
        int idx = reverse ? seq.size() - 1 - inputPos : inputPos;
        return seq.get(idx);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        if (state != INPUT) return true;
        int pad = -1;
        for (int i = 0; i < 4; i++) if (rects[i].contains(e.getX(), e.getY())) pad = i;
        if (pad < 0) return true;
        light(pad, 180);
        if (pad == expected()) {
            inputPos++;
            if (inputPos >= seq.size()) {
                state = SHOWING;
                final int g = gen;
                if (listener != null) listener.onStatus("Nice!");
                handler.postDelayed(new Runnable() {
                    public void run() { if (g == gen) nextRound(); }
                }, 700);
            }
        } else {
            state = OVER;
            gen++;
            flashWrong = true;
            lit = -1;
            tones.playBuzz();
            try { if (vib != null) vib.vibrate(300); } catch (Exception ignored) { }
            invalidate();
            if (listener != null) listener.onGameOver(seq.size() - 1);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF121212);
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float gap = size * 0.04f;
        float cell = (size - gap * 3) / 2f;
        float left = (w - size) / 2f + gap;
        float top = (h - size) / 2f + gap;
        for (int i = 0; i < 4; i++) {
            int col = i % 2, row = i / 2;
            float x = left + col * (cell + gap);
            float y = top + row * (cell + gap);
            rects[i].set(x, y, x + cell, y + cell);
            boolean on = lit == i;
            if (on) {
                paint.setColor(GLOW[i]);
                paint.setAlpha(70);
                c.drawRoundRect(x - gap / 2, y - gap / 2, x + cell + gap / 2, y + cell + gap / 2, 40, 40, paint);
                paint.setAlpha(255);
            }
            paint.setColor(on ? GLOW[i] : BASE[i]);
            if (!on && state == SHOWING) paint.setAlpha(120);
            c.drawRoundRect(rects[i], 36, 36, paint);
            paint.setAlpha(255);
        }
        if (flashWrong) {
            paint.setColor(0x55FF0000);
            c.drawRect(0, 0, w, h, paint);
        }
    }
}
