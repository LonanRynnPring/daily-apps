package com.dailyapps.tiltmaze;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/** Tilt-controlled ball maze. Mazes are generated fresh each level and get bigger and trickier. */
public class MazeView extends View {
    private static final int N = 1, E = 2, S = 4, W = 8;
    private static final int[] WALL_COLORS = {
            0xFF4FC3F7, 0xFFBA68C8, 0xFF81C784, 0xFFFFB74D, 0xFFF06292
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private final SharedPreferences prefs;
    private final Vibrator vibrator;

    private final ArrayList<RectF> walls = new ArrayList<>();
    private final ArrayList<int[]> holes = new ArrayList<>(); // {row, col}

    private int cols, rows;
    private int level = 1;
    private int bestLevel;
    private int[][] cells;

    private float cell, offX, offY, hudH;
    private float bx, by, vx, vy, radius;
    private float tiltX, tiltY;
    private float elapsed;
    private long lastNanos;
    private long lastBuzz;
    private boolean ready;
    private String message = "";
    private float messageTime;

    public MazeView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("tiltmaze", Context.MODE_PRIVATE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        bestLevel = prefs.getInt("best", 1);
        setKeepScreenOn(true);
    }

    public void setTilt(float x, float y) {
        tiltX = tiltX * 0.7f + x * 0.3f;
        tiltY = tiltY * 0.7f + y * 0.3f;
    }

    public void resume() {
        lastNanos = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        hudH = Math.max(140f, h * 0.09f);
        buildLevel();
        ready = true;
    }

    private void buildLevel() {
        float margin = getWidth() * 0.04f;
        float availW = getWidth() - 2 * margin;
        float availH = getHeight() - hudH - margin;

        cols = Math.min(4 + level, 10);
        cell = availW / cols;
        rows = Math.max(4, (int) (availH / cell));
        offX = margin;
        offY = hudH + (availH - rows * cell) / 2f;

        cells = new int[rows][cols];
        boolean[][] seen = new boolean[rows][cols];
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};
        int[] bit = {N, E, S, W};
        int[] opp = {S, W, N, E};

        // Depth-first maze generation.
        int[] stack = new int[rows * cols];
        int sp = 0;
        stack[sp++] = 0;
        seen[0][0] = true;
        while (sp > 0) {
            int cur = stack[sp - 1];
            int r = cur / cols;
            int c = cur % cols;
            int[] options = new int[4];
            int n = 0;
            for (int d = 0; d < 4; d++) {
                int nr = r + dr[d];
                int nc = c + dc[d];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !seen[nr][nc]) {
                    options[n++] = d;
                }
            }
            if (n == 0) {
                sp--;
                continue;
            }
            int d = options[rng.nextInt(n)];
            int nr = r + dr[d];
            int nc = c + dc[d];
            cells[r][c] |= bit[d];
            cells[nr][nc] |= opp[d];
            seen[nr][nc] = true;
            stack[sp++] = nr * cols + nc;
        }

        // Knock out a few extra walls so there is more than one route.
        int extra = (rows * cols) / 12;
        for (int i = 0; i < extra; i++) {
            int r = rng.nextInt(rows);
            int c = rng.nextInt(cols);
            int d = rng.nextInt(4);
            int nr = r + dr[d];
            int nc = c + dc[d];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                cells[r][c] |= bit[d];
                cells[nr][nc] |= opp[d];
            }
        }

        // Wall rectangles for collisions and drawing.
        walls.clear();
        float t = Math.max(4f, cell * 0.12f);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x0 = offX + c * cell;
                float y0 = offY + r * cell;
                if ((cells[r][c] & N) == 0) {
                    walls.add(new RectF(x0 - t / 2, y0 - t / 2, x0 + cell + t / 2, y0 + t / 2));
                }
                if ((cells[r][c] & W) == 0) {
                    walls.add(new RectF(x0 - t / 2, y0 - t / 2, x0 + t / 2, y0 + cell + t / 2));
                }
                if (c == cols - 1) {
                    walls.add(new RectF(x0 + cell - t / 2, y0 - t / 2, x0 + cell + t / 2, y0 + cell + t / 2));
                }
                if (r == rows - 1) {
                    walls.add(new RectF(x0 - t / 2, y0 + cell - t / 2, x0 + cell + t / 2, y0 + cell + t / 2));
                }
            }
        }

        // Holes sit only in dead ends, so they never block the route to the goal.
        holes.clear();
        int wanted = level >= 3 ? Math.min((level - 1) / 2, 6) : 0;
        ArrayList<int[]> deadEnds = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean isStart = r == 0 && c == 0;
                boolean isGoal = r == rows - 1 && c == cols - 1;
                if (!isStart && !isGoal && Integer.bitCount(cells[r][c]) == 1) {
                    deadEnds.add(new int[]{r, c});
                }
            }
        }
        while (holes.size() < wanted && !deadEnds.isEmpty()) {
            holes.add(deadEnds.remove(rng.nextInt(deadEnds.size())));
        }

        radius = cell * 0.28f;
        resetBall();
        elapsed = 0f;
    }

    private void resetBall() {
        bx = offX + cell / 2f;
        by = offY + cell / 2f;
        vx = 0f;
        vy = 0f;
    }

    private void buzz(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBuzz < 80) {
            return;
        }
        lastBuzz = now;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void step(float dt) {
        elapsed += dt;
        if (messageTime > 0f) {
            messageTime -= dt;
        }

        float acc = cell * 4f;
        vx += tiltX * acc * dt;
        vy += tiltY * acc * dt;
        float damp = 1f - 1.8f * dt;
        if (damp < 0f) {
            damp = 0f;
        }
        vx *= damp;
        vy *= damp;

        float maxV = cell * 9f;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed > maxV) {
            vx *= maxV / speed;
            vy *= maxV / speed;
        }

        int steps = 4;
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            bx += vx * h;
            by += vy * h;
            collide();
        }

        checkHoles();
        checkGoal();
    }

    private void collide() {
        for (int i = 0; i < walls.size(); i++) {
            RectF w = walls.get(i);
            float cx = Math.max(w.left, Math.min(bx, w.right));
            float cy = Math.max(w.top, Math.min(by, w.bottom));
            float dx = bx - cx;
            float dy = by - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 >= radius * radius) {
                continue;
            }
            float d = (float) Math.sqrt(d2);
            float nx;
            float ny;
            if (d > 0.0001f) {
                nx = dx / d;
                ny = dy / d;
            } else {
                // Ball centre is inside the wall: push out the nearest side.
                float l = bx - w.left;
                float r = w.right - bx;
                float t = by - w.top;
                float b = w.bottom - by;
                float m = Math.min(Math.min(l, r), Math.min(t, b));
                if (m == l) {
                    nx = -1f;
                    ny = 0f;
                    d = -l;
                } else if (m == r) {
                    nx = 1f;
                    ny = 0f;
                    d = -r;
                } else if (m == t) {
                    nx = 0f;
                    ny = -1f;
                    d = -t;
                } else {
                    nx = 0f;
                    ny = 1f;
                    d = -b;
                }
            }
            float pen = radius - d;
            bx += nx * pen;
            by += ny * pen;
            float vn = vx * nx + vy * ny;
            if (vn < 0f) {
                vx -= 1.4f * vn * nx;
                vy -= 1.4f * vn * ny;
                if (-vn > cell * 3f) {
                    buzz(15);
                }
            }
        }
    }

    private void checkHoles() {
        for (int i = 0; i < holes.size(); i++) {
            int[] h = holes.get(i);
            float hx = offX + (h[1] + 0.5f) * cell;
            float hy = offY + (h[0] + 0.5f) * cell;
            float dx = bx - hx;
            float dy = by - hy;
            if (dx * dx + dy * dy < (cell * 0.25f) * (cell * 0.25f)) {
                elapsed += 5f;
                resetBall();
                showMessage("Fell in a hole!  +5s");
                buzz(80);
                return;
            }
        }
    }

    private void checkGoal() {
        float gx = offX + (cols - 0.5f) * cell;
        float gy = offY + (rows - 0.5f) * cell;
        float dx = bx - gx;
        float dy = by - gy;
        if (dx * dx + dy * dy < (cell * 0.3f) * (cell * 0.3f)) {
            String time = String.format(Locale.US, "%.1fs", elapsed);
            int cleared = level;
            level++;
            if (level > bestLevel) {
                bestLevel = level;
                prefs.edit().putInt("best", bestLevel).apply();
            }
            buildLevel();
            showMessage("Level " + cleared + " cleared in " + time);
            buzz(120);
        }
    }

    private void showMessage(String text) {
        message = text;
        messageTime = 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!ready) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        if (dt > 0.05f) {
            dt = 0.05f;
        }
        step(dt);

        canvas.drawColor(0xFF0E141B);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF18222D);
        canvas.drawRect(offX, offY, offX + cols * cell, offY + rows * cell, paint);

        // Goal.
        float gx = offX + (cols - 0.5f) * cell;
        float gy = offY + (rows - 0.5f) * cell;
        paint.setColor(0xFF2E7D32);
        canvas.drawCircle(gx, gy, cell * 0.36f, paint);
        paint.setColor(0xFF66BB6A);
        canvas.drawCircle(gx, gy, cell * 0.22f, paint);

        // Holes.
        for (int i = 0; i < holes.size(); i++) {
            int[] h = holes.get(i);
            float hx = offX + (h[1] + 0.5f) * cell;
            float hy = offY + (h[0] + 0.5f) * cell;
            paint.setColor(0xFF3E2723);
            canvas.drawCircle(hx, hy, cell * 0.3f, paint);
            paint.setColor(0xFF000000);
            canvas.drawCircle(hx, hy, cell * 0.24f, paint);
        }

        // Walls.
        paint.setColor(WALL_COLORS[(level - 1) % WALL_COLORS.length]);
        for (int i = 0; i < walls.size(); i++) {
            canvas.drawRect(walls.get(i), paint);
        }

        // Ball.
        paint.setColor(0x66000000);
        canvas.drawCircle(bx + radius * 0.15f, by + radius * 0.2f, radius, paint);
        paint.setColor(0xFFFFB347);
        canvas.drawCircle(bx, by, radius, paint);
        paint.setColor(0x88FFFFFF);
        canvas.drawCircle(bx - radius * 0.3f, by - radius * 0.3f, radius * 0.3f, paint);

        // HUD.
        paint.setColor(0xFFFFFFFF);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(hudH * 0.36f);
        float base = hudH * 0.62f;
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Level " + level, getWidth() * 0.04f, base, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.format(Locale.US, "%.1fs", elapsed), getWidth() / 2f, base, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Best " + bestLevel, getWidth() * 0.96f, base, paint);

        if (messageTime > 0f) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(hudH * 0.3f);
            float tw = paint.measureText(message);
            float my = hudH + hudH * 0.5f;
            paint.setColor(0xCC000000);
            canvas.drawRect(getWidth() / 2f - tw / 2f - 24f, my - hudH * 0.35f,
                    getWidth() / 2f + tw / 2f + 24f, my + hudH * 0.2f, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawText(message, getWidth() / 2f, my, paint);
        }

        postInvalidateOnAnimation();
    }
}
