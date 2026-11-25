package com.dailyapps.laserbounce;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.Random;

public class GameView extends View {
    private static final int MENU = 0, PLAY = 1;
    private static final int TOTAL = 60, PER_PAGE = 30, COLS = 5;

    private static final int BG = 0xFF0B1020;
    private static final int PANEL = 0xFF121A33;
    private static final int GRID = 0xFF222D52;
    private static final int WALL = 0xFF4A5580;
    private static final int BEAM = 0xFFFF3D6E;
    private static final int GOLD = 0xFFFFD34D;
    private static final int GREEN = 0xFF6BFFB0;
    private static final int CYAN = 0xFF58D6FF;
    private static final int MIRROR = 0xFFD6F2FF;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final SharedPreferences prefs;
    private final Random rnd = new Random();

    private int screen = MENU;
    private int page = 0;
    private int W, H;
    private float u = 1f;

    private Level level;
    private int[][] m;
    private int moves, used, hints, stars;
    private boolean solved;
    private Level.Trace trace;
    private float bl, bt, cs;
    private int hintX = -1, hintY = -1, hintType;
    private long hintUntil, msgUntil;
    private String msg = "";

    private final RectF rBack = new RectF(), rClear = new RectF(), rHint = new RectF();
    private final RectF rNext = new RectF(), rMenu = new RectF(), rReplay = new RectF();
    private final RectF rPrev = new RectF(), rNextPage = new RectF();

    public GameView(Context c) {
        super(c);
        prefs = c.getSharedPreferences("laserbounce", Context.MODE_PRIVATE);
        setKeepScreenOn(true);
    }

    private int unlocked() {
        return Math.max(1, prefs.getInt("unlocked", 1));
    }

    private int starsOf(int lv) {
        return prefs.getInt("stars_" + lv, 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        u = w / 360f;
        layoutPlay();
    }

    private void layoutPlay() {
        rBack.set(10 * u, 14 * u, 70 * u, 58 * u);
        if (level != null) {
            float avail = Math.min(W - 44 * u, H - 100 * u - 130 * u);
            cs = avail / level.n;
            bl = (W - cs * level.n) / 2f + 4 * u;
            bt = 100 * u;
            float by = bt + cs * level.n + 22 * u;
            float bw = (W - 60 * u) / 2f;
            rClear.set(20 * u, by, 20 * u + bw, by + 52 * u);
            rHint.set(40 * u + bw, by, 40 * u + bw * 2, by + 52 * u);
        }
        float pw = 280 * u, ph = 250 * u;
        float px = (W - pw) / 2f, py = (H - ph) / 2f;
        rNext.set(px + 20 * u, py + 120 * u, px + pw - 20 * u, py + 170 * u);
        rReplay.set(px + 20 * u, py + 182 * u, px + pw / 2f - 5 * u, py + 232 * u);
        rMenu.set(px + pw / 2f + 5 * u, py + 182 * u, px + pw - 20 * u, py + 232 * u);
        rPrev.set(20 * u, H - 80 * u, 150 * u, H - 28 * u);
        rNextPage.set(W - 150 * u, H - 80 * u, W - 20 * u, H - 28 * u);
    }

    public boolean onBack() {
        if (screen == PLAY) {
            screen = MENU;
            invalidate();
            return true;
        }
        return false;
    }

    private void startLevel(int n) {
        level = Level.generate(n);
        m = new int[level.n][level.n];
        moves = 0;
        used = 0;
        hints = 0;
        solved = false;
        stars = 0;
        hintX = -1;
        msgUntil = 0;
        screen = PLAY;
        layoutPlay();
        trace = level.trace(m);
        invalidate();
    }

    private void win() {
        solved = true;
        int s;
        if (moves <= level.par + 1) s = 3;
        else if (moves <= level.par * 2 + 2) s = 2;
        else s = 1;
        if (hints > 0 && s > 2) s = 2;
        stars = s;
        if (s > starsOf(level.number)) prefs.edit().putInt("stars_" + level.number, s).apply();
        if (level.number + 1 > unlocked() && level.number < TOTAL)
            prefs.edit().putInt("unlocked", level.number + 1).apply();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void say(String s) {
        msg = s;
        msgUntil = System.currentTimeMillis() + 1800;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) return true;
        if (a != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        if (screen == MENU) touchMenu(x, y);
        else touchPlay(x, y);
        invalidate();
        return true;
    }

    private void menuCell(int i, RectF out) {
        float gw = (W - 40 * u - 4 * 10 * u) / COLS;
        int col = i % COLS, row = i / COLS;
        float left = 20 * u + col * (gw + 10 * u);
        float top = 140 * u + row * (gw + 10 * u);
        out.set(left, top, left + gw, top + gw);
    }

    private void touchMenu(float x, float y) {
        for (int i = 0; i < PER_PAGE; i++) {
            int lv = page * PER_PAGE + i + 1;
            if (lv > TOTAL) break;
            menuCell(i, rect);
            if (rect.contains(x, y) && lv <= unlocked()) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                startLevel(lv);
                return;
            }
        }
        if (page > 0 && rPrev.contains(x, y)) page--;
        else if ((page + 1) * PER_PAGE < TOTAL && rNextPage.contains(x, y)) page++;
    }

    private void touchPlay(float x, float y) {
        if (solved) {
            if (rNext.contains(x, y) && level.number < TOTAL) startLevel(level.number + 1);
            else if (rReplay.contains(x, y)) startLevel(level.number);
            else if (rMenu.contains(x, y)) {
                page = (level.number - 1) / PER_PAGE;
                screen = MENU;
            }
            return;
        }
        if (rBack.contains(x, y)) {
            page = (level.number - 1) / PER_PAGE;
            screen = MENU;
            return;
        }
        if (rClear.contains(x, y)) {
            m = new int[level.n][level.n];
            used = 0;
            hintX = -1;
            trace = level.trace(m);
            return;
        }
        if (rHint.contains(x, y)) {
            giveHint();
            return;
        }
        int gx = (int) Math.floor((x - bl) / cs);
        int gy = (int) Math.floor((y - bt) / cs);
        if (gx < 0 || gy < 0 || gx >= level.n || gy >= level.n) return;
        if (level.wall[gy][gx]) return;
        int cur = m[gy][gx];
        if (cur == Level.EMPTY && used >= level.budget) {
            say("No mirrors left - tap a mirror to remove it");
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return;
        }
        int next = (cur + 1) % 3;
        m[gy][gx] = next;
        if (cur == Level.EMPTY) used++;
        if (next == Level.EMPTY) used--;
        moves++;
        if (hintX == gx && hintY == gy) hintX = -1;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        trace = level.trace(m);
        if (trace.hitCount == level.targetCount) win();
    }

    private void giveHint() {
        int count = 0;
        for (int y = 0; y < level.n; y++)
            for (int x = 0; x < level.n; x++)
                if (m[y][x] != level.solution[y][x]) count++;
        if (count == 0) return;
        int pick = rnd.nextInt(count);
        for (int y = 0; y < level.n; y++)
            for (int x = 0; x < level.n; x++)
                if (m[y][x] != level.solution[y][x]) {
                    if (pick-- == 0) {
                        hintX = x;
                        hintY = y;
                        hintType = level.solution[y][x];
                    }
                }
        hints++;
        hintUntil = System.currentTimeMillis() + 3500;
        say("Hint: the ghost mirror shows one spot");
    }

    // ---------------------------------------------------------------- drawing

    private void text(String s, float x, float y, float size, int color, Paint.Align al, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(al);
        p.setFakeBoldText(bold);
        canvas.drawText(s, x, y, p);
    }

    private Canvas canvas;

    private void button(RectF r, String label, int fill, int tc) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        canvas.drawRoundRect(r, 12 * u, 12 * u, p);
        text(label, r.centerX(), r.centerY() + 6 * u, 17 * u, tc, Paint.Align.CENTER, true);
    }

    private void starsRow(float cx, float y, int n, float size) {
        String full = "★", empty = "☆";
        float step = size * 1.15f;
        for (int i = 0; i < 3; i++) {
            text(i < n ? full : empty, cx + (i - 1) * step, y, size, i < n ? GOLD : 0xFF55608A, Paint.Align.CENTER, false);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        canvas = c;
        c.drawColor(BG);
        if (screen == MENU) drawMenu();
        else drawPlay();
        postInvalidateOnAnimation();
    }

    private void drawMenu() {
        text("LASER BOUNCE", W / 2f, 62 * u, 34 * u, BEAM, Paint.Align.CENTER, true);
        text("Bend the beam through every target", W / 2f, 90 * u, 14 * u, 0xFFA7B3D9, Paint.Align.CENTER, false);
        text("Tap a cell: / mirror, \\ mirror, clear", W / 2f, 110 * u, 13 * u, 0xFF7C89B5, Paint.Align.CENTER, false);
        int unl = unlocked();
        int total = 0;
        for (int i = 1; i <= TOTAL; i++) total += starsOf(i);
        text("Stars " + total + " / " + (TOTAL * 3), W / 2f, 130 * u, 13 * u, GOLD, Paint.Align.CENTER, false);
        for (int i = 0; i < PER_PAGE; i++) {
            int lv = page * PER_PAGE + i + 1;
            if (lv > TOTAL) break;
            menuCell(i, rect);
            boolean open = lv <= unl;
            p.setStyle(Paint.Style.FILL);
            p.setColor(open ? (starsOf(lv) > 0 ? 0xFF1E3A5C : 0xFF1B2748) : 0xFF11172B);
            c().drawRoundRect(rect, 10 * u, 10 * u, p);
            if (open) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * u);
                p.setColor(lv == unl ? BEAM : 0xFF34508A);
                c().drawRoundRect(rect, 10 * u, 10 * u, p);
                text(String.valueOf(lv), rect.centerX(), rect.centerY() + 2 * u, 20 * u, 0xFFFFFFFF, Paint.Align.CENTER, true);
                starsRow(rect.centerX(), rect.bottom - 7 * u, starsOf(lv), 12 * u);
            } else {
                text("•", rect.centerX(), rect.centerY() + 8 * u, 22 * u, 0xFF2B3558, Paint.Align.CENTER, true);
            }
        }
        if (page > 0) button(rPrev, "< Prev", 0xFF24315C, 0xFFFFFFFF);
        if ((page + 1) * PER_PAGE < TOTAL) button(rNextPage, "Next >", 0xFF24315C, 0xFFFFFFFF);
        text("Page " + (page + 1) + "/" + (TOTAL / PER_PAGE), W / 2f, H - 48 * u, 13 * u, 0xFF7C89B5, Paint.Align.CENTER, false);
    }

    private Canvas c() {
        return canvas;
    }

    private float gx(float g) {
        return bl + (g + 0.5f) * cs;
    }

    private float gy(float g) {
        return bt + (g + 0.5f) * cs;
    }

    private void drawMirror(int type, float cx, float cy, int color, int alpha, boolean glow) {
        float h = cs * 0.36f;
        float x1, y1, x2, y2;
        if (type == Level.SLASH) {
            x1 = cx - h; y1 = cy + h; x2 = cx + h; y2 = cy - h;
        } else {
            x1 = cx - h; y1 = cy - h; x2 = cx + h; y2 = cy + h;
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (glow) {
            p.setColor(color);
            p.setAlpha(60);
            p.setStrokeWidth(cs * 0.22f);
            canvas.drawLine(x1, y1, x2, y2, p);
        }
        p.setColor(color);
        p.setAlpha(alpha);
        p.setStrokeWidth(cs * 0.1f);
        canvas.drawLine(x1, y1, x2, y2, p);
        p.setAlpha(255);
    }

    private void drawPlay() {
        long now = System.currentTimeMillis();
        // header
        button(rBack, "←", 0xFF24315C, 0xFFFFFFFF);
        text("Level " + level.number, W / 2f, 40 * u, 24 * u, 0xFFFFFFFF, Paint.Align.CENTER, true);
        text("Mirrors " + (level.budget - used) + " left", W / 2f, 62 * u, 14 * u, MIRROR, Paint.Align.CENTER, false);
        text("Targets " + trace.hitCount + "/" + level.targetCount, W - 14 * u, 40 * u, 14 * u,
                trace.hitCount == level.targetCount ? GREEN : GOLD, Paint.Align.RIGHT, true);
        text("Moves " + moves, W - 14 * u, 60 * u, 13 * u, 0xFF8E9AC4, Paint.Align.RIGHT, false);
        if (now < msgUntil) text(msg, W / 2f, 86 * u, 13 * u, GOLD, Paint.Align.CENTER, false);

        // board
        float bw = cs * level.n;
        rect.set(bl - 6 * u, bt - 6 * u, bl + bw + 6 * u, bt + bw + 6 * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(PANEL);
        canvas.drawRoundRect(rect, 12 * u, 12 * u, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f * u);
        p.setColor(GRID);
        for (int i = 0; i <= level.n; i++) {
            canvas.drawLine(bl + i * cs, bt, bl + i * cs, bt + bw, p);
            canvas.drawLine(bl, bt + i * cs, bl + bw, bt + i * cs, p);
        }
        // emitter
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF8A3D);
        rect.set(bl - 16 * u, gy(level.emitRow) - cs * 0.22f, bl, gy(level.emitRow) + cs * 0.22f);
        canvas.drawRoundRect(rect, 4 * u, 4 * u, p);

        // walls and targets
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 250.0);
        for (int y = 0; y < level.n; y++) {
            for (int x = 0; x < level.n; x++) {
                float cx = gx(x), cy = gy(y);
                if (level.wall[y][x]) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(WALL);
                    rect.set(cx - cs * 0.44f, cy - cs * 0.44f, cx + cs * 0.44f, cy + cs * 0.44f);
                    canvas.drawRoundRect(rect, 6 * u, 6 * u, p);
                    p.setColor(0xFF343E66);
                    rect.set(cx - cs * 0.3f, cy - cs * 0.3f, cx + cs * 0.3f, cy + cs * 0.3f);
                    canvas.drawRoundRect(rect, 4 * u, 4 * u, p);
                }
                if (level.target[y][x]) {
                    boolean lit = trace.hit[y][x];
                    float r = cs * (lit ? 0.28f + 0.05f * pulse : 0.26f);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3 * u);
                    p.setColor(lit ? GREEN : CYAN);
                    canvas.drawCircle(cx, cy, cs * 0.34f, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(lit ? GREEN : 0xFF1C5A78);
                    canvas.drawCircle(cx, cy, r * 0.7f, p);
                }
            }
        }

        // beam
        List<float[]> pts = trace.pts;
        path.reset();
        for (int i = 0; i < pts.size(); i++) {
            float[] q = pts.get(i);
            if (i == 0) path.moveTo(gx(q[0]), gy(q[1]));
            else path.lineTo(gx(q[0]), gy(q[1]));
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(BEAM);
        p.setAlpha(50);
        p.setStrokeWidth(cs * 0.34f);
        canvas.drawPath(path, p);
        p.setAlpha(120);
        p.setStrokeWidth(cs * 0.16f);
        canvas.drawPath(path, p);
        p.setColor(0xFFFFE3EA);
        p.setAlpha(255);
        p.setStrokeWidth(cs * 0.06f);
        canvas.drawPath(path, p);

        // pulses travelling along the beam
        float total = 0;
        for (int i = 1; i < pts.size(); i++) {
            float[] a = pts.get(i - 1), b = pts.get(i);
            total += Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
        }
        if (total > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            for (int k = 0; k < 4; k++) {
                float dist = (((now % 2400) / 2400f) + k / 4f) % 1f * total;
                for (int i = 1; i < pts.size(); i++) {
                    float[] a = pts.get(i - 1), b = pts.get(i);
                    float len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
                    if (dist <= len) {
                        float t = len == 0 ? 0 : dist / len;
                        canvas.drawCircle(gx(a[0] + (b[0] - a[0]) * t), gy(a[1] + (b[1] - a[1]) * t), cs * 0.07f, p);
                        break;
                    }
                    dist -= len;
                }
            }
        }

        // mirrors
        for (int y = 0; y < level.n; y++)
            for (int x = 0; x < level.n; x++)
                if (m[y][x] != Level.EMPTY) drawMirror(m[y][x], gx(x), gy(y), MIRROR, 255, true);
        if (hintX >= 0 && hintType != Level.EMPTY && now < hintUntil && ((now / 300) % 2 == 0))
            drawMirror(hintType, gx(hintX), gy(hintY), GOLD, 200, true);
        if (hintX >= 0 && now < hintUntil && hintType == Level.EMPTY) {
            p.setStyle(Paint.Style.STROKE);
            p.setColor(GOLD);
            p.setStrokeWidth(2 * u);
            canvas.drawCircle(gx(hintX), gy(hintY), cs * 0.4f, p);
        }

        // buttons
        button(rClear, "Clear", 0xFF24315C, 0xFFFFFFFF);
        button(rHint, "Hint (" + hints + ")", 0xFF4A3A12, GOLD);

        if (solved) drawSolved();
    }

    private void drawSolved() {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC050812);
        canvas.drawRect(0, 0, W, H, p);
        float pw = 280 * u, ph = 250 * u;
        float px = (W - pw) / 2f, py = (H - ph) / 2f;
        rect.set(px, py, px + pw, py + ph);
        p.setColor(0xFF16213F);
        canvas.drawRoundRect(rect, 18 * u, 18 * u, p);
        text("SOLVED!", W / 2f, py + 50 * u, 30 * u, GREEN, Paint.Align.CENTER, true);
        starsRow(W / 2f, py + 95 * u, stars, 34 * u);
        text(moves + " moves (par " + level.par + ")", W / 2f, py + 114 * u, 13 * u, 0xFFA7B3D9, Paint.Align.CENTER, false);
        if (level.number < TOTAL) button(rNext, "Next level", 0xFF1F7A4D, 0xFFFFFFFF);
        else button(rNext, "All done!", 0xFF2A3560, 0xFFFFFFFF);
        button(rReplay, "Replay", 0xFF24315C, 0xFFFFFFFF);
        button(rMenu, "Levels", 0xFF24315C, 0xFFFFFFFF);
    }
}
