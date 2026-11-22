package com.dailyapps.dactyl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dactyl: a 4x3 grid of bombs. Bombs light up at random, tap each one before
 * its fuse burns out. Miss one and everything blows up.
 */
public class GameView extends View {
    private static final int COLS = 4, ROWS = 3, N = COLS * ROWS;
    private static final int PLAY = 0, BOOM = 1, SCORES = 2;
    private static final float BOOM_TIME = 1.5f;
    private static final int MAX_SCORES = 10;

    private final SharedPreferences prefs;
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path seg = new Path();
    private final Path fusePath = new Path();
    private final PathMeasure pm = new PathMeasure();
    private final RectF rf = new RectF();
    private final float[] pos = new float[2];
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Layout
    private int W, H;
    private float r, cw, ch;
    private float gridLeft, gridRight, gridTop, gridBottom;
    private float titleSize;
    private final float[] cx = new float[N];
    private final float[] cy = new float[N];
    private final RectF btnBack = new RectF();
    private final RectF btnSound = new RectF();
    private final RectF btnRestart = new RectF();
    private final RectF lcd = new RectF();
    private Shader bgShader, bodyDark, bodyLit, silver;
    private float fuseLen;

    // Game state
    private int state = PLAY;
    private int score;
    private boolean sound;
    private final boolean[] lit = new boolean[N];
    private final float[] fuse = new float[N];
    private final float[] fuseTotal = new float[N];
    private final float[] popT = new float[N];
    private float spawnTimer;
    private float boomT, scoresT;
    private int boomIdx;
    private final float[] bx = new float[N];
    private final float[] by = new float[N];
    private final float[] vx = new float[N];
    private final float[] vy = new float[N];
    private final float[] rot = new float[N];
    private final float[] vr = new float[N];
    private List<long[]> scores = new ArrayList<long[]>();
    private long[] newEntry;

    private float time;
    private long last;
    private boolean paused;

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("dactyl", Context.MODE_PRIVATE);
        sound = prefs.getBoolean("sound", true);
        Sounds.init();
        scores = loadScores();
        restart();
    }

    // ---------------------------------------------------------------- lifecycle

    void onHostPause() {
        paused = true;
    }

    void onHostResume() {
        paused = false;
        last = 0;
        if (state == PLAY) {
            for (int i = 0; i < N; i++) {
                if (lit[i]) fuse[i] += 1.0f;
            }
            if (spawnTimer < 1f) spawnTimer = 1f;
        }
        invalidate();
    }

    // ------------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w;
        H = h;
        float m = W * 0.06f;
        gridLeft = m;
        gridRight = W - m;
        gridTop = H * 0.13f;
        gridBottom = H * 0.74f;
        cw = (gridRight - gridLeft) / COLS;
        ch = (gridBottom - gridTop) / ROWS;
        r = Math.min(cw, ch) * 0.40f;
        for (int i = 0; i < N; i++) {
            int col = i % COLS;
            int row = i / COLS;
            cx[i] = gridLeft + cw * (col + 0.5f);
            cy[i] = gridTop + ch * (row + 0.58f);
        }

        float hs = W * 0.10f;
        float by0 = H * 0.065f;
        btnBack.set(W * 0.14f - hs, by0 - hs, W * 0.14f + hs, by0 + hs);
        btnSound.set(W * 0.50f - hs, by0 - hs, W * 0.50f + hs, by0 + hs);
        btnRestart.set(W * 0.86f - hs, by0 - hs, W * 0.86f + hs, by0 + hs);

        lcd.set(W - m - W * 0.36f, H * 0.775f, W - m, H * 0.84f);

        bgShader = new LinearGradient(0, 0, 0, H,
                new int[]{0xFF6FC3F0, 0xFF9AD8F7, 0xFFDDF3FF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        bodyDark = new RadialGradient(-r * 0.35f, -r * 0.4f, r * 1.6f,
                new int[]{0xFF8E949D, 0xFF2A2D33, 0xFF050506},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        bodyLit = new RadialGradient(-r * 0.35f, -r * 0.4f, r * 1.6f,
                new int[]{0xFFFFA89A, 0xFFE02418, 0xFF7A0804},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        fusePath.reset();
        fusePath.moveTo(0, -r * 0.95f);
        fusePath.quadTo(r * 0.05f, -r * 1.35f, r * 0.55f, -r * 1.5f);
        pm.setPath(fusePath, false);
        fuseLen = pm.getLength();

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setTextSize(100f);
        t.setLetterSpacing(0.18f);
        float wd = t.measureText("DACTYL");
        titleSize = 100f * (W * 0.78f) / Math.max(1f, wd);
        float base = H * 0.93f;
        silver = new LinearGradient(0, base - titleSize * 0.75f, 0, base,
                new int[]{0xFFFFFFFF, 0xFFC4CED6, 0xFF8FA0AE, 0xFFE6ECF0},
                new float[]{0f, 0.45f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    // ------------------------------------------------------------------ game

    private void restart() {
        state = PLAY;
        score = 0;
        spawnTimer = 0.8f;
        boomT = 0;
        scoresT = 0;
        newEntry = null;
        for (int i = 0; i < N; i++) {
            lit[i] = false;
            fuse[i] = 0;
            popT[i] = 0;
            bx[i] = cx[i];
            by[i] = cy[i];
            rot[i] = 0;
        }
    }

    private float fuseTime() {
        return Math.max(0.75f, 2.2f - 0.02f * score);
    }

    private float spawnInterval() {
        return Math.max(0.32f, 1.0f - 0.01f * score);
    }

    private void spawn() {
        int[] free = new int[N];
        int n = 0;
        for (int i = 0; i < N; i++) {
            if (!lit[i]) free[n++] = i;
        }
        if (n == 0) return;
        int i = free[rnd.nextInt(n)];
        lit[i] = true;
        fuseTotal[i] = fuseTime();
        fuse[i] = fuseTotal[i];
    }

    private void defuse(int i) {
        lit[i] = false;
        score++;
        popT[i] = 0.001f;
        if (sound) Sounds.play(Sounds.defuse);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void explode(int idx) {
        state = BOOM;
        boomT = 0;
        boomIdx = idx;
        float k = W / 1080f;
        for (int i = 0; i < N; i++) {
            bx[i] = cx[i];
            by[i] = cy[i];
            popT[i] = 0;
            float dx = cx[i] - cx[idx];
            float dy = cy[i] - cy[idx];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float speed = (600 + rnd.nextFloat() * 700) * k;
            if (dist < 1f) {
                double a = rnd.nextDouble() * Math.PI * 2;
                dx = (float) Math.cos(a);
                dy = (float) Math.sin(a);
                dist = 1f;
            }
            vx[i] = dx / dist * speed + (rnd.nextFloat() - 0.5f) * 300 * k;
            vy[i] = dy / dist * speed - 500 * k;
            vr[i] = (rnd.nextFloat() - 0.5f) * 900f;
        }
        recordScore();
        if (sound) Sounds.play(Sounds.boom);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void update(float dt) {
        if (state == PLAY) {
            int litCount = 0;
            for (int i = 0; i < N; i++) {
                if (lit[i]) {
                    fuse[i] -= dt;
                    if (fuse[i] <= 0f) {
                        explode(i);
                        return;
                    }
                    litCount++;
                }
                if (popT[i] > 0f) {
                    popT[i] += dt / 0.4f;
                    if (popT[i] >= 1f) popT[i] = 0f;
                }
            }
            spawnTimer -= dt;
            int maxLit = Math.min(4, 1 + score / 12);
            if (litCount == 0 && spawnTimer > 0.25f) spawnTimer = 0.25f;
            if (spawnTimer <= 0f) {
                if (litCount < maxLit) {
                    spawn();
                    spawnTimer = spawnInterval();
                } else {
                    spawnTimer = 0.1f;
                }
            }
        } else if (state == BOOM) {
            boomT += dt;
            float k = W / 1080f;
            for (int i = 0; i < N; i++) {
                bx[i] += vx[i] * dt;
                by[i] += vy[i] * dt;
                vy[i] += 1800f * k * dt;
                rot[i] += vr[i] * dt;
            }
            if (boomT >= BOOM_TIME) {
                state = SCORES;
                scoresT = 0;
            }
        } else {
            scoresT += dt;
        }
    }

    // ------------------------------------------------------------- high scores

    private List<long[]> loadScores() {
        List<long[]> list = new ArrayList<long[]>();
        String s = prefs.getString("scores", "");
        if (s != null && s.length() > 0) {
            String[] parts = s.split("\\|");
            for (String part : parts) {
                String[] kv = part.split(",");
                if (kv.length == 2) {
                    try {
                        list.add(new long[]{Long.parseLong(kv[0]), Long.parseLong(kv[1])});
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        sortScores(list);
        return list;
    }

    private void sortScores(List<long[]> list) {
        Collections.sort(list, new Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                if (a[1] != b[1]) return a[1] > b[1] ? -1 : 1;
                if (a[0] != b[0]) return a[0] > b[0] ? -1 : 1;
                return 0;
            }
        });
    }

    private void recordScore() {
        newEntry = null;
        if (score <= 0) return;
        long[] e = new long[]{System.currentTimeMillis(), score};
        scores.add(e);
        sortScores(scores);
        while (scores.size() > MAX_SCORES) scores.remove(scores.size() - 1);
        if (scores.contains(e)) newEntry = e;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scores.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(scores.get(i)[0]).append(',').append(scores.get(i)[1]);
        }
        prefs.edit().putString("scores", sb.toString()).apply();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) {
            return true;
        }
        int pi = e.getActionIndex();
        float x = e.getX(pi);
        float y = e.getY(pi);

        if (btnBack.contains(x, y)) {
            if (getContext() instanceof Activity) ((Activity) getContext()).finish();
            return true;
        }
        if (btnSound.contains(x, y)) {
            sound = !sound;
            prefs.edit().putBoolean("sound", sound).apply();
            invalidate();
            return true;
        }
        if (btnRestart.contains(x, y)) {
            restart();
            invalidate();
            return true;
        }
        if (state == PLAY) {
            if (x >= gridLeft && x < gridRight && y >= gridTop && y < gridBottom) {
                int col = (int) ((x - gridLeft) / cw);
                int row = (int) ((y - gridTop) / ch);
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    int i = row * COLS + col;
                    if (lit[i]) defuse(i);
                }
            }
        } else if (state == SCORES && scoresT > 0.5f) {
            restart();
        }
        return true;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = 0f;
        if (last != 0) dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        time += dt;
        if (W > 0 && !paused) update(dt);

        c.save();
        if (state == BOOM) {
            float shake = Math.max(0f, 1f - boomT / 0.5f);
            c.translate((rnd.nextFloat() - 0.5f) * W * 0.04f * shake,
                    (rnd.nextFloat() - 0.5f) * W * 0.04f * shake);
        }

        p.setShader(bgShader);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(-W * 0.1f, -H * 0.1f, W * 1.1f, H * 1.1f, p);
        p.setShader(null);

        drawButtons(c);
        if (state != SCORES) drawBombs(c);
        drawLcd(c);
        drawTitle(c);
        if (state == SCORES) drawScores(c);
        c.restore();

        if (state == BOOM && boomT < 0.4f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb((int) (200 * (1f - boomT / 0.4f)), 255, 255, 255));
            c.drawRect(0, 0, W, H, p);
        }

        if (!paused) postInvalidateOnAnimation();
    }

    private void drawBombs(Canvas c) {
        for (int i = 0; i < N; i++) {
            if (state == PLAY) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 14f + i);
                float frac = lit[i] ? Math.max(0f, fuse[i] / fuseTotal[i]) : 1f;
                float scale = lit[i] ? 1f + 0.04f * pulse : 1f;
                drawBomb(c, cx[i], cy[i], 0f, scale, lit[i], frac, pulse, 255);
            } else {
                float a = Math.max(0f, 1f - boomT / BOOM_TIME);
                float scale = 1f + boomT * 0.4f;
                drawBomb(c, bx[i], by[i], rot[i], scale, lit[i], 0.2f, 1f, (int) (255 * a));
            }
        }

        if (state == PLAY) {
            for (int i = 0; i < N; i++) {
                if (popT[i] > 0f) {
                    float t = popT[i];
                    p.setShader(null);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(r * 0.14f * (1f - t) + 1f);
                    p.setColor(Color.argb((int) (230 * (1f - t)), 255, 255, 255));
                    c.drawCircle(cx[i], cy[i], r * (1f + 0.9f * t), p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb((int) (120 * (1f - t)), 255, 255, 255));
                    c.drawCircle(cx[i], cy[i], r * (0.6f + 0.6f * t), p);
                }
            }
        } else if (state == BOOM) {
            float a = Math.max(0f, 1f - boomT / 0.7f);
            if (a > 0f) {
                float big = r * (0.6f + boomT * 9f);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int) (200 * a), 255, 120, 20));
                c.drawCircle(cx[boomIdx], cy[boomIdx], big, p);
                p.setColor(Color.argb((int) (230 * a), 255, 215, 80));
                c.drawCircle(cx[boomIdx], cy[boomIdx], big * 0.68f, p);
                p.setColor(Color.argb((int) (255 * a), 255, 255, 240));
                c.drawCircle(cx[boomIdx], cy[boomIdx], big * 0.36f, p);
            }
        }
    }

    private void drawBomb(Canvas c, float x, float y, float rotation, float scale,
                          boolean isLit, float frac, float pulse, int alpha) {
        if (alpha <= 0) return;
        c.save();
        c.translate(x, y);
        c.rotate(rotation);
        c.scale(scale, scale);

        p.setStyle(Paint.Style.FILL);

        if (isLit) {
            p.setShader(null);
            p.setColor(Color.argb((int) ((50 + 50 * pulse) * alpha / 255f), 255, 60, 40));
            c.drawCircle(0, 0, r * 1.3f, p);
        }

        // Body
        p.setShader(isLit ? bodyLit : bodyDark);
        p.setAlpha(alpha);
        c.drawCircle(0, 0, r, p);
        p.setShader(null);

        // Cap
        p.setColor(0xFF59606A);
        p.setAlpha(alpha);
        rf.set(-r * 0.3f, -r * 1.12f, r * 0.3f, -r * 0.82f);
        c.drawRoundRect(rf, r * 0.08f, r * 0.08f, p);

        // Fuse
        float show = isLit ? frac : 1f;
        seg.rewind();
        pm.setPath(fusePath, false);
        pm.getSegment(0f, fuseLen * show, seg, true);
        tp.setShader(null);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(r * 0.09f);
        tp.setStrokeCap(Paint.Cap.ROUND);
        tp.setColor(0xFFB08850);
        tp.setAlpha(alpha);
        c.drawPath(seg, tp);
        tp.setStrokeCap(Paint.Cap.BUTT);

        pm.getPosTan(fuseLen * show, pos, null);
        if (isLit) {
            float rr = r * (0.2f + 0.08f * (float) Math.sin(time * 40f));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha, 255, 170, 30));
            c.drawCircle(pos[0], pos[1], rr, p);
            p.setColor(Color.argb(alpha, 255, 245, 160));
            c.drawCircle(pos[0], pos[1], rr * 0.55f, p);
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(r * 0.05f);
            tp.setColor(Color.argb(alpha, 255, 220, 80));
            for (int k = 0; k < 6; k++) {
                double a = time * 6 + k * Math.PI / 3;
                float l1 = rr * 1.1f;
                float l2 = rr * (1.7f + 0.4f * (float) Math.sin(time * 33 + k));
                c.drawLine(pos[0] + (float) Math.cos(a) * l1, pos[1] + (float) Math.sin(a) * l1,
                        pos[0] + (float) Math.cos(a) * l2, pos[1] + (float) Math.sin(a) * l2, tp);
            }
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha, 224, 176, 64));
            c.drawCircle(pos[0], pos[1], r * 0.07f, p);
        }

        // Highlight
        c.save();
        c.rotate(-40f, -r * 0.4f, -r * 0.45f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (isLit ? 110 : 90) * alpha / 255, 255, 255, 255));
        rf.set(-r * 0.68f, -r * 0.58f, -r * 0.12f, -r * 0.32f);
        c.drawOval(rf, p);
        c.restore();

        c.restore();
    }

    private void strokeTwice(Canvas c, Path pa, float s, boolean fill) {
        p.setShader(null);
        p.setStyle(fill ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.38f);
        p.setColor(0xFFFFFFFF);
        c.drawPath(pa, p);
        p.setStrokeWidth(s * 0.2f);
        p.setColor(0xFF6F9BBA);
        c.drawPath(pa, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeJoin(Paint.Join.MITER);
    }

    private void drawButtons(Canvas c) {
        float s = W * 0.06f;

        // Back arrow
        float x = btnBack.centerX();
        float y = btnBack.centerY();
        path.rewind();
        path.moveTo(x + s * 0.9f, y);
        path.lineTo(x - s * 0.9f, y);
        path.moveTo(x - s * 0.2f, y - s * 0.7f);
        path.lineTo(x - s * 0.9f, y);
        path.lineTo(x - s * 0.2f, y + s * 0.7f);
        strokeTwice(c, path, s, false);

        // Speaker
        x = btnSound.centerX();
        y = btnSound.centerY();
        path.rewind();
        path.moveTo(x - s * 0.9f, y - s * 0.35f);
        path.lineTo(x - s * 0.45f, y - s * 0.35f);
        path.lineTo(x + s * 0.05f, y - s * 0.8f);
        path.lineTo(x + s * 0.05f, y + s * 0.8f);
        path.lineTo(x - s * 0.45f, y + s * 0.35f);
        path.lineTo(x - s * 0.9f, y + s * 0.35f);
        path.close();
        strokeTwice(c, path, s, true);
        if (sound) {
            path.rewind();
            rf.set(x - s * 0.45f, y - s * 0.6f, x + s * 0.65f, y + s * 0.6f);
            path.addArc(rf, -50f, 100f);
            strokeTwice(c, path, s, false);
            path.rewind();
            rf.set(x - s * 0.75f, y - s * 1.0f, x + s * 1.05f, y + s * 1.0f);
            path.addArc(rf, -50f, 100f);
            strokeTwice(c, path, s, false);
        } else {
            path.rewind();
            path.moveTo(x + s * 0.4f, y - s * 0.6f);
            path.lineTo(x + s * 1.1f, y + s * 0.6f);
            path.moveTo(x + s * 1.1f, y - s * 0.6f);
            path.lineTo(x + s * 0.4f, y + s * 0.6f);
            strokeTwice(c, path, s, false);
        }

        // Restart
        x = btnRestart.centerX();
        y = btnRestart.centerY();
        float rad = s * 0.8f;
        path.rewind();
        rf.set(x - rad, y - rad, x + rad, y + rad);
        path.addArc(rf, -60f, 290f);
        strokeTwice(c, path, s, false);
        double th = Math.toRadians(230.0);
        float px = x + rad * (float) Math.cos(th);
        float py = y + rad * (float) Math.sin(th);
        float tx = -(float) Math.sin(th);
        float ty = (float) Math.cos(th);
        float nx = (float) Math.cos(th);
        float ny = (float) Math.sin(th);
        path.rewind();
        path.moveTo(px + tx * s * 0.55f, py + ty * s * 0.55f);
        path.lineTo(px + nx * s * 0.45f, py + ny * s * 0.45f);
        path.lineTo(px - nx * s * 0.45f, py - ny * s * 0.45f);
        path.close();
        strokeTwice(c, path, s * 0.7f, true);
    }

    private void drawLcd(Canvas c) {
        float m = W * 0.06f;

        // Label
        String label = "BOMBS DEFUSED";
        tp.setShader(null);
        tp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(100f);
        tp.setLetterSpacing(0.02f);
        float avail = lcd.left - m - W * 0.03f;
        float size = 100f * avail / Math.max(1f, tp.measureText(label));
        size = Math.min(size, lcd.height() * 0.8f);
        tp.setTextSize(size);
        float ly = lcd.centerY() + size * 0.35f;
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.16f);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setColor(0xFF4A0A08);
        c.drawText(label, m, ly, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(0xFFD8281E);
        c.drawText(label, m, ly, tp);
        tp.setLetterSpacing(0f);
        tp.setStrokeJoin(Paint.Join.MITER);

        // LCD box
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3A4A52);
        rf.set(lcd.left - 6, lcd.top - 6, lcd.right + 6, lcd.bottom + 6);
        c.drawRoundRect(rf, 14, 14, p);
        p.setColor(0xFF16221C);
        c.drawRoundRect(lcd, 10, 10, p);

        float dsize = Math.min(lcd.height() * 0.8f, lcd.width() * 0.8f / 3f);
        tp.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        tp.setTextAlign(Paint.Align.RIGHT);
        tp.setTextSize(dsize);
        float dy = lcd.centerY() + dsize * 0.34f;
        float dx = lcd.right - lcd.width() * 0.06f;
        tp.setColor(0xFF2B4630);
        c.drawText("88888", dx, dy, tp);
        tp.setColor(0xFFC8F000);
        c.drawText(String.valueOf(score), dx, dy, tp);
    }

    private void drawTitle(Canvas c) {
        float base = H * 0.93f;
        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(titleSize);
        tp.setLetterSpacing(0.18f);
        float x = W / 2f + titleSize * 0.09f;
        tp.setShader(null);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(titleSize * 0.06f);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setColor(0xFF56708A);
        c.drawText("DACTYL", x + 2, base + 2, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setShader(silver);
        c.drawText("DACTYL", x, base, tp);
        tp.setShader(null);
        tp.setLetterSpacing(0f);
        tp.setStrokeJoin(Paint.Join.MITER);
    }

    private void drawScores(Canvas c) {
        float a = Math.min(1f, scoresT / 0.3f);
        float m = W * 0.06f;
        float panelH = gridBottom - gridTop;

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (110 * a), 10, 50, 100));
        rf.set(m * 0.6f, gridTop - panelH * 0.02f, W - m * 0.6f, gridBottom + panelH * 0.02f);
        c.drawRoundRect(rf, 24, 24, p);

        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setStyle(Paint.Style.FILL);
        tp.setShader(null);
        int white = Color.argb((int) (255 * a), 255, 255, 255);

        float hs = panelH * 0.07f;
        tp.setTextSize(hs);
        tp.setColor(white);
        c.drawText("High Scores", m * 1.4f, gridTop + panelH * 0.09f, tp);
        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(hs * 0.55f);
        c.drawText("Classic Mode", m * 1.4f, gridTop + panelH * 0.145f, tp);

        float rowH = panelH * 0.066f;
        float y0 = gridTop + panelH * 0.2f;
        float rs = Math.min(rowH * 0.6f, W * 0.04f);
        tp.setTextSize(rs);
        for (int i = 0; i < scores.size() && i < MAX_SCORES; i++) {
            long[] e = scores.get(i);
            float ry = y0 + rowH * (i + 1);
            if (e == newEntry) {
                p.setColor(Color.argb((int) (90 * a), 255, 224, 102));
                rf.set(m * 0.9f, ry - rowH * 0.78f, W - m * 0.9f, ry + rowH * 0.22f);
                c.drawRoundRect(rf, 10, 10, p);
            }
            tp.setTextAlign(Paint.Align.LEFT);
            c.drawText(dateFmt.format(new Date(e[0])) + "  Me", m * 1.4f, ry, tp);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Bombs:" + e[1], W - m * 1.4f, ry, tp);
        }

        if (scores.isEmpty()) {
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText("No scores yet", W / 2f, y0 + rowH * 2, tp);
        }

        float pulse = 0.65f + 0.35f * (float) Math.sin(time * 4f);
        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(panelH * 0.045f);
        tp.setColor(Color.argb((int) (255 * a * pulse), 255, 255, 255));
        c.drawText("Tap to play again", W / 2f, gridBottom - panelH * 0.03f, tp);
    }
}
