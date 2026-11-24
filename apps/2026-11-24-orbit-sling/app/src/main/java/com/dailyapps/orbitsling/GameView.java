package com.dailyapps.orbitsling;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Random;

public class GameView extends View {
    private static final int MENU = 0;
    private static final int PLAY = 1;
    private static final int AIM = 0;
    private static final int FLY = 1;
    private static final int WON = 2;
    private static final int FAIL = 3;
    private static final int MAXL = 30;
    private static final int COLS = 5;
    private static final int ROWS = 6;
    private static final int BG = 0xFF0B0E1F;
    private static final int[] PLANET_COLORS = {
            0xFF4FC3F7, 0xFFFFB74D, 0xFF9575CD, 0xFF81C784, 0xFFF06292
    };

    private final SharedPreferences prefs;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final HashMap<Integer, Level> cache = new HashMap<Integer, Level>();
    private ToneGenerator tone;

    private final float[] starX = new float[80];
    private final float[] starY = new float[80];
    private final float[] starP = new float[80];

    private int mode = MENU;
    private int state = AIM;
    private int level = 1;
    private Level lv;
    private final Level.Probe probe = new Level.Probe();
    private final Level.Probe preview = new Level.Probe();
    private int shots;
    private float failTimer;
    private boolean crashed;
    private float crashX, crashY;
    private final float[] trailX = new float[1000];
    private final float[] trailY = new float[1000];
    private int trailN;
    private float acc;
    private int lastStars;

    private boolean dragging;
    private float tx0, ty0, tx1, ty1;
    private int downBtn;
    private int downCell = -1;

    private float s, ox, oy;
    private long lastNanos;
    private float time;

    private final RectF btnBack = new RectF();
    private final RectF btnRetry = new RectF();
    private final RectF btnNext = new RectF();
    private final RectF btnAgain = new RectF();
    private final RectF btnLevels = new RectF();
    private final RectF[] cells = new RectF[MAXL];

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("orbitsling", Context.MODE_PRIVATE);
        density = context.getResources().getDisplayMetrics().density;
        Random r = new Random(42);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat();
            starP[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < MAXL; i++) cells[i] = new RectF();
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
        } catch (Throwable t) {
            tone = null;
        }
        paint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private float dp(float v) {
        return v * density;
    }

    private int starsFor(int n) {
        return prefs.getInt("stars" + n, 0);
    }

    private int totalStars() {
        int t = 0;
        for (int i = 1; i <= MAXL; i++) t += starsFor(i);
        return t;
    }

    private boolean unlocked(int n) {
        return n == 1 || starsFor(n - 1) > 0;
    }

    private Level getLevel(int n) {
        Level l = cache.get(n);
        if (l == null) {
            l = Level.make(n);
            cache.put(n, l);
        }
        return l;
    }

    private void startLevel(int n) {
        level = n;
        lv = getLevel(n);
        mode = PLAY;
        state = AIM;
        shots = 0;
        trailN = 0;
        dragging = false;
        probe.reset(lv, 0f, 0f);
    }

    boolean onBack() {
        if (mode == PLAY) {
            mode = MENU;
            return true;
        }
        return false;
    }

    private void beep(int type, int ms) {
        try {
            if (tone != null) tone.startTone(type, ms);
        } catch (Throwable t) {
            // ignore
        }
    }

    private void buzz(int kind) {
        try {
            performHapticFeedback(kind);
        } catch (Throwable t) {
            // ignore
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        s = Math.min(w / Level.W, h / Level.H);
        ox = (w - s * Level.W) / 2f;
        oy = (h - s * Level.H) / 2f;
        btnBack.set(dp(8), dp(8), dp(60), dp(60));
        btnRetry.set(w - dp(60), dp(8), w - dp(8), dp(60));
        float bw = w * 0.62f;
        float bh = dp(54);
        float cx = w / 2f;
        float top = h * 0.5f;
        btnNext.set(cx - bw / 2, top, cx + bw / 2, top + bh);
        btnAgain.set(cx - bw / 2, top + bh + dp(12), cx + bw / 2, top + 2 * bh + dp(12));
        btnLevels.set(cx - bw / 2, top + 2 * bh + dp(24), cx + bw / 2, top + 3 * bh + dp(24));

        float margin = w * 0.07f;
        float gridTop = h * 0.2f;
        float cell = Math.min((w - 2 * margin) / COLS, (h * 0.96f - gridTop) / ROWS);
        float gridW = cell * COLS;
        float left = (w - gridW) / 2f;
        float gap = cell * 0.12f;
        for (int i = 0; i < MAXL; i++) {
            int c = i % COLS;
            int r = i / COLS;
            cells[i].set(left + c * cell + gap / 2, gridTop + r * cell + gap / 2,
                    left + (c + 1) * cell - gap / 2, gridTop + (r + 1) * cell - gap / 2);
        }
    }

    private float wx(float x) {
        return ox + x * s;
    }

    private float wy(float y) {
        return oy + y * s;
    }

    // ------------------------------------------------------------------ logic

    private void addTrail() {
        if (trailN < trailX.length) {
            trailX[trailN] = probe.x;
            trailY[trailN] = probe.y;
            trailN++;
        }
    }

    private void launch(float vx, float vy) {
        probe.reset(lv, vx, vy);
        trailN = 0;
        addTrail();
        state = FLY;
        acc = 0f;
        shots++;
        buzz(HapticFeedbackConstants.KEYBOARD_TAP);
        beep(ToneGenerator.TONE_DTMF_5, 70);
    }

    private void win() {
        state = WON;
        int st = shots <= 2 ? 3 : (shots <= 4 ? 2 : 1);
        lastStars = st;
        if (st > starsFor(level)) prefs.edit().putInt("stars" + level, st).apply();
        buzz(HapticFeedbackConstants.LONG_PRESS);
        beep(ToneGenerator.TONE_PROP_ACK, 250);
    }

    private void fail(int code) {
        state = FAIL;
        failTimer = 0.9f;
        crashed = code == 2;
        crashX = probe.x;
        crashY = probe.y;
        if (crashed) {
            buzz(HapticFeedbackConstants.LONG_PRESS);
            beep(ToneGenerator.TONE_PROP_NACK, 200);
        }
    }

    private void update(float dt) {
        time += dt;
        if (mode != PLAY) return;
        if (state == FLY) {
            acc += Math.min(dt, 0.05f);
            while (acc >= Level.DT && state == FLY) {
                acc -= Level.DT;
                int st = probe.step(lv);
                if (probe.steps % 2 == 0) addTrail();
                if (st == 1) win();
                else if (st == 2 || st == 3) fail(st);
            }
        } else if (state == FAIL) {
            failTimer -= dt;
            if (failTimer <= 0f) {
                state = AIM;
                trailN = 0;
            }
        }
    }

    // ------------------------------------------------------------------ input

    private int hitBtn(float x, float y) {
        if (state == WON) {
            if (btnNext.contains(x, y)) return 3;
            if (btnAgain.contains(x, y)) return 4;
            if (btnLevels.contains(x, y)) return 5;
            return 0;
        }
        if (btnBack.contains(x, y)) return 1;
        if (btnRetry.contains(x, y)) return 2;
        return 0;
    }

    private int hitCell(float x, float y) {
        for (int i = 0; i < MAXL; i++) {
            if (cells[i].contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        int a = e.getActionMasked();
        if (mode == MENU) {
            if (a == MotionEvent.ACTION_DOWN) {
                downCell = hitCell(x, y);
            } else if (a == MotionEvent.ACTION_UP) {
                int c = hitCell(x, y);
                if (c >= 0 && c == downCell && unlocked(c + 1)) startLevel(c + 1);
                downCell = -1;
            }
            return true;
        }
        if (a == MotionEvent.ACTION_DOWN) {
            downBtn = hitBtn(x, y);
            dragging = false;
            if (downBtn == 0 && state == AIM) {
                dragging = true;
                tx0 = x;
                ty0 = y;
                tx1 = x;
                ty1 = y;
            }
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (dragging) {
                tx1 = x;
                ty1 = y;
            }
        } else if (a == MotionEvent.ACTION_UP) {
            if (downBtn != 0 && hitBtn(x, y) == downBtn) {
                handleButton(downBtn);
            } else if (dragging && state == AIM) {
                tx1 = x;
                ty1 = y;
                float[] v = pullVelocity();
                if (v != null) launch(v[0], v[1]);
            }
            dragging = false;
            downBtn = 0;
        } else if (a == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            downBtn = 0;
        }
        return true;
    }

    private void handleButton(int id) {
        if (id == 1 || id == 5) {
            mode = MENU;
        } else if (id == 2 || id == 4) {
            startLevel(level);
        } else if (id == 3) {
            if (level < MAXL) startLevel(level + 1);
            else mode = MENU;
        }
    }

    /** Converts the current drag into a launch velocity, or null if the pull is too small. */
    private float[] pullVelocity() {
        float px = (tx0 - tx1) / s;
        float py = (ty0 - ty1) / s;
        float len = (float) Math.sqrt(px * px + py * py);
        if (len < 0.04f) return null;
        if (len > Level.MAXPULL) {
            px *= Level.MAXPULL / len;
            py *= Level.MAXPULL / len;
        }
        return new float[]{px * Level.SPEED, py * Level.SPEED};
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (dt > 0.1f) dt = 0.1f;
        update(dt);

        c.drawColor(BG);
        drawStars(c);
        if (mode == MENU) drawMenu(c);
        else drawPlay(c);
        postInvalidateOnAnimation();
    }

    private void text(Canvas c, String t, float x, float y, float size, int color, Paint.Align al) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(al);
        c.drawText(t, x, y, paint);
    }

    private void drawStars(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < starX.length; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 1.5f + starP[i]);
            paint.setColor(((int) (60 + 120 * tw) << 24) | 0xFFFFFF);
            c.drawCircle(starX[i] * w, starY[i] * h, dp(0.8f + (i % 3) * 0.5f), paint);
        }
    }

    private void drawMenu(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        text(c, "ORBIT SLING", w / 2f, h * 0.09f, dp(34), 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "★ " + totalStars() + " / " + (MAXL * 3) + "   ·   fling the probe to the green target",
                w / 2f, h * 0.09f + dp(28), dp(12.5f), 0xFFA0A8D0, Paint.Align.CENTER);
        for (int i = 0; i < MAXL; i++) {
            int n = i + 1;
            boolean open = unlocked(n);
            int st = starsFor(n);
            RectF r = cells[i];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(open ? (st > 0 ? 0xFF1F3A5F : 0xFF22294A) : 0xFF14172B);
            c.drawRoundRect(r, dp(10), dp(10), paint);
            if (open) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                paint.setColor(st > 0 ? 0xFF4FC3F7 : 0xFF5A66A8);
                c.drawRoundRect(r, dp(10), dp(10), paint);
            }
            float cx = r.centerX();
            if (open) {
                text(c, String.valueOf(n), cx, r.centerY() + dp(2), r.width() * 0.34f, 0xFFFFFFFF, Paint.Align.CENTER);
                String sv = st >= 3 ? "★★★" : (st == 2 ? "★★" : (st == 1 ? "★" : ""));
                text(c, sv, cx, r.bottom - r.height() * 0.14f, r.width() * 0.2f, 0xFFFFD54F, Paint.Align.CENTER);
            } else {
                text(c, "·", cx, r.centerY() + dp(6), r.width() * 0.4f, 0xFF3A4066, Paint.Align.CENTER);
            }
        }
    }

    private void drawPlay(Canvas c) {
        if (lv == null) return;
        paint.setStyle(Paint.Style.FILL);

        // target
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3f);
        float tcx = wx(lv.tx);
        float tcy = wy(lv.ty);
        float trp = lv.tr * s;
        paint.setColor(0x2200E676);
        c.drawCircle(tcx, tcy, trp * (2.2f + pulse * 0.5f), paint);
        paint.setColor(0x4400E676);
        c.drawCircle(tcx, tcy, trp * 1.5f, paint);
        paint.setColor(0xFF00E676);
        c.drawCircle(tcx, tcy, trp * 0.45f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        c.drawCircle(tcx, tcy, trp * (1.0f + pulse * 0.2f), paint);

        // planets
        for (int i = 0; i < lv.n; i++) {
            float cx = wx(lv.px[i]);
            float cy = wy(lv.py[i]);
            float rr = lv.pr[i] * s;
            boolean rep = lv.pg[i] < 0;
            int col = rep ? 0xFFFF5252 : PLANET_COLORS[i % PLANET_COLORS.length];
            int rgb = col & 0xFFFFFF;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x14000000 | rgb);
            c.drawCircle(cx, cy, rr * 3.4f, paint);
            paint.setColor(0x1C000000 | rgb);
            c.drawCircle(cx, cy, rr * 2.4f, paint);
            paint.setColor(0x28000000 | rgb);
            c.drawCircle(cx, cy, rr * 1.6f, paint);
            paint.setColor(col);
            c.drawCircle(cx, cy, rr, paint);
            paint.setColor(0x55FFFFFF);
            c.drawCircle(cx - rr * 0.3f, cy - rr * 0.3f, rr * 0.4f, paint);
            paint.setColor(0x44000000);
            c.drawCircle(cx + rr * 0.35f, cy + rr * 0.35f, rr * 0.55f, paint);
            if (rep) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                paint.setColor(0x88FF5252);
                c.drawCircle(cx, cy, rr * (1.6f + 0.8f * pulse), paint);
            }
        }

        // trail
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < trailN; i++) {
            float f = (float) i / Math.max(1, trailN);
            paint.setColor(((int) (30 + 170 * f) << 24) | 0xFFFFFF);
            c.drawCircle(wx(trailX[i]), wy(trailY[i]), dp(1.6f), paint);
        }

        // aim preview
        float sxp = wx(lv.sx);
        float syp = wy(lv.sy);
        if (state == AIM && dragging) {
            float[] v = pullVelocity();
            if (v != null) {
                int steps = Math.max(50, 170 - level * 4);
                preview.reset(lv, v[0], v[1]);
                for (int i = 0; i < steps; i++) {
                    int st = preview.step(lv);
                    if (i % 3 == 0) {
                        float f = 1f - (float) i / steps;
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(((int) (40 + 190 * f) << 24) | 0xFFE082);
                        c.drawCircle(wx(preview.x), wy(preview.y), dp(2.2f), paint);
                    }
                    if (st != 0) break;
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(0xAAFFFFFF);
                c.drawLine(sxp, syp, sxp - v[0] / Level.SPEED * s * 0.5f, syp - v[1] / Level.SPEED * s * 0.5f, paint);
            }
        }

        // probe
        paint.setStyle(Paint.Style.FILL);
        if (state == AIM) {
            paint.setColor(0x33FFFFFF);
            c.drawCircle(sxp, syp, dp(12) + pulse * dp(4), paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(sxp, syp, dp(5), paint);
        } else if (state == FLY) {
            paint.setColor(0x44FFE082);
            c.drawCircle(wx(probe.x), wy(probe.y), dp(10), paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(wx(probe.x), wy(probe.y), dp(5), paint);
        } else if (state == FAIL && crashed) {
            float f = 1f - failTimer / 0.9f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(((int) (255 * (1f - f)) << 24) | 0xFF7043);
            c.drawCircle(wx(crashX), wy(crashY), dp(8) + f * dp(46), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(((int) (200 * (1f - f)) << 24) | 0xFFD54F);
            c.drawCircle(wx(crashX), wy(crashY), dp(6) + f * dp(18), paint);
        }

        drawHud(c);
        if (state == WON) drawWin(c);
    }

    private void drawHud(Canvas c) {
        int w = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(btnBack, dp(12), dp(12), paint);
        c.drawRoundRect(btnRetry, dp(12), dp(12), paint);
        text(c, "‹", btnBack.centerX(), btnBack.centerY() + dp(10), dp(32), 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "↻", btnRetry.centerX(), btnRetry.centerY() + dp(9), dp(26), 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "Level " + level, w / 2f, dp(30), dp(18), 0xFFFFFFFF, Paint.Align.CENTER);
        text(c, "Shots " + shots + "   ·   3★ in 2", w / 2f, dp(50), dp(12.5f), 0xFFA0A8D0, Paint.Align.CENTER);
        if (level == 1 && shots == 0 && state == AIM && !dragging) {
            text(c, "Drag back anywhere, then release to sling", w / 2f, getHeight() - dp(40), dp(14), 0xFFFFE082, Paint.Align.CENTER);
        }
        if (level >= 8 && shots == 0 && state == AIM && !dragging) {
            text(c, "Red worlds push the probe away", w / 2f, getHeight() - dp(40), dp(13), 0xFFFF8A80, Paint.Align.CENTER);
        }
    }

    private void drawWin(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3050714);
        c.drawRect(0, 0, w, h, paint);
        text(c, "ORBIT COMPLETE", w / 2f, h * 0.27f, dp(28), 0xFFFFFFFF, Paint.Align.CENTER);
        String sv = (lastStars >= 1 ? "★" : "☆") + (lastStars >= 2 ? "★" : "☆")
                + (lastStars >= 3 ? "★" : "☆");
        text(c, sv, w / 2f, h * 0.27f + dp(64), dp(52), 0xFFFFD54F, Paint.Align.CENTER);
        text(c, shots + (shots == 1 ? " shot" : " shots"), w / 2f, h * 0.27f + dp(94), dp(15), 0xFFA0A8D0, Paint.Align.CENTER);
        button(c, btnNext, level < MAXL ? "Next level ›" : "All done – Levels", 0xFF00C853);
        button(c, btnAgain, "Retry", 0xFF3949AB);
        button(c, btnLevels, "Levels", 0xFF3949AB);
    }

    private void button(Canvas c, RectF r, String label, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(r, dp(14), dp(14), paint);
        text(c, label, r.centerX(), r.centerY() + dp(6), dp(18), 0xFFFFFFFF, Paint.Align.CENTER);
    }
}
