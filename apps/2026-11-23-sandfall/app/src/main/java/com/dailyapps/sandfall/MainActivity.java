package com.dailyapps.sandfall;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    static final int EMPTY = 0, SAND = 1, WATER = 2, STONE = 3, WOOD = 4, FIRE = 5, STEAM = 6, OIL = 7, ERASE = 99;
    static final String[] NAMES = {"Sand", "Water", "Stone", "Wood", "Fire", "Oil", "Erase"};
    static final int[] TOOLS = {SAND, WATER, STONE, WOOD, FIRE, OIL, ERASE};
    static final int[] COLORS = {0xFFE0C068, 0xFF3A7BD5, 0xFF7A7A85, 0xFF8B5A2B, 0xFFFF6A1A, 0xFF4A3A2A, 0xFF333344};

    SimView sim;
    TextView[] toolViews = new TextView[TOOLS.length];
    TextView pauseBtn, brushBtn;
    SensorManager sm;
    long lastShake = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF14141C);

        sim = new SimView(this);
        root.addView(sim, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        hs.addView(bar);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < TOOLS.length; i++) {
            final int idx = i;
            TextView t = button(NAMES[i], COLORS[i]);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selectTool(idx); }
            });
            toolViews[i] = t;
            bar.addView(t, lp());
        }
        brushBtn = button("Brush 2", 0xFF444466);
        brushBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sim.brush = sim.brush >= 5 ? 1 : sim.brush + 1;
                brushBtn.setText("Brush " + sim.brush);
            }
        });
        bar.addView(brushBtn, lp());
        pauseBtn = button("Pause", 0xFF444466);
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sim.paused = !sim.paused;
                pauseBtn.setText(sim.paused ? "Play" : "Pause");
            }
        });
        bar.addView(pauseBtn, lp());
        TextView clear = button("Clear", 0xFF8A2A2A);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sim.clearAll(); }
        });
        bar.addView(clear, lp());
        TextView hint = new TextView(this);
        hint.setText("Draw with your finger. Shake the phone to clear.");
        hint.setTextColor(0xFF8888AA);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(6));
        root.addView(hint);

        setContentView(root);
        selectTool(0);
        brushBtn.setText("Brush " + sim.brush);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    TextView button(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setBackground(box(color, 0));
        t.setTag(color);
        return t;
    }

    GradientDrawable box(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        if (stroke != 0) g.setStroke(dp(3), stroke);
        return g;
    }

    void selectTool(int idx) {
        sim.tool = TOOLS[idx];
        for (int i = 0; i < toolViews.length; i++) {
            toolViews[i].setBackground(box(COLORS[i], i == idx ? Color.WHITE : 0));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (a != null) sm.registerListener(this, a, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        double g = Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();
        if (g > 24 && now - lastShake > 1200) {
            lastShake = now;
            sim.clearAll();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {}

    static class SimView extends View {
        int tool = SAND, brush = 2;
        boolean paused = false;
        int cell;
        int gw = 0, gh = 0;
        byte[] grid, life;
        boolean[] moved;
        int[] pixels;
        Bitmap bmp;
        Rect src = new Rect(), dst = new Rect();
        Paint paint = new Paint();
        Random rnd = new Random();
        float tx = -1, ty = -1;
        boolean touching = false;
        int frame = 0;

        SimView(Context c) {
            super(c);
            cell = Math.max(3, (int) (c.getResources().getDisplayMetrics().density * 3.2f));
            paint.setFilterBitmap(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int nw = Math.max(10, w / cell), nh = Math.max(10, h / cell);
            if (nw == gw && nh == gh) return;
            gw = nw;
            gh = nh;
            grid = new byte[gw * gh];
            life = new byte[gw * gh];
            moved = new boolean[gw * gh];
            pixels = new int[gw * gh];
            bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        }

        void clearAll() {
            if (grid == null) return;
            java.util.Arrays.fill(grid, (byte) 0);
            java.util.Arrays.fill(life, (byte) 0);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                touching = true;
                tx = e.getX() / cell;
                ty = e.getY() / cell;
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                touching = false;
            }
            return true;
        }

        void spawn() {
            if (grid == null) return;
            int cx = (int) tx, cy = (int) ty;
            int r = brush * 2;
            boolean solid = tool == STONE || tool == WOOD || tool == ERASE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy > r * r) continue;
                    if (!solid && rnd.nextInt(3) != 0) continue;
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || y < 0 || x >= gw || y >= gh) continue;
                    int i = y * gw + x;
                    if (tool == ERASE) {
                        grid[i] = EMPTY;
                        life[i] = 0;
                    } else if (grid[i] == EMPTY || tool == FIRE) {
                        if (tool == FIRE && grid[i] != EMPTY && grid[i] != WOOD && grid[i] != OIL) continue;
                        grid[i] = (byte) tool;
                        life[i] = (byte) (tool == FIRE ? 20 + rnd.nextInt(30) : 0);
                    }
                }
            }
        }

        boolean empty(int x, int y) {
            return x >= 0 && y >= 0 && x < gw && y < gh && grid[y * gw + x] == EMPTY;
        }

        boolean is(int x, int y, int t) {
            return x >= 0 && y >= 0 && x < gw && y < gh && grid[y * gw + x] == t;
        }

        void swap(int x1, int y1, int x2, int y2) {
            int a = y1 * gw + x1, b = y2 * gw + x2;
            byte g = grid[a]; grid[a] = grid[b]; grid[b] = g;
            byte l = life[a]; life[a] = life[b]; life[b] = l;
            moved[a] = true;
            moved[b] = true;
        }

        boolean neighbor(int x, int y, int t) {
            return is(x + 1, y, t) || is(x - 1, y, t) || is(x, y + 1, t) || is(x, y - 1, t);
        }

        void step() {
            java.util.Arrays.fill(moved, false);
            boolean ltr = (frame & 1) == 0;
            for (int y = gh - 1; y >= 0; y--) {
                for (int k = 0; k < gw; k++) {
                    int x = ltr ? k : gw - 1 - k;
                    int i = y * gw + x;
                    int t = grid[i];
                    if (t == EMPTY || t == STONE || moved[i]) continue;
                    int dir = rnd.nextBoolean() ? 1 : -1;
                    switch (t) {
                        case SAND:
                            if (empty(x, y + 1) || is(x, y + 1, WATER) || is(x, y + 1, OIL)) swap(x, y, x, y + 1);
                            else if (empty(x + dir, y + 1) || is(x + dir, y + 1, WATER)) swap(x, y, x + dir, y + 1);
                            else if (empty(x - dir, y + 1) || is(x - dir, y + 1, WATER)) swap(x, y, x - dir, y + 1);
                            break;
                        case WATER:
                            if (empty(x, y + 1) || is(x, y + 1, OIL) && rnd.nextInt(2) == 0) swap(x, y, x, y + 1);
                            else if (empty(x + dir, y + 1)) swap(x, y, x + dir, y + 1);
                            else if (empty(x - dir, y + 1)) swap(x, y, x - dir, y + 1);
                            else if (empty(x + dir, y)) swap(x, y, x + dir, y);
                            else if (empty(x - dir, y)) swap(x, y, x - dir, y);
                            break;
                        case OIL:
                            if (empty(x, y + 1)) swap(x, y, x, y + 1);
                            else if (empty(x + dir, y + 1)) swap(x, y, x + dir, y + 1);
                            else if (empty(x + dir, y) && rnd.nextInt(2) == 0) swap(x, y, x + dir, y);
                            else if (empty(x - dir, y) && rnd.nextInt(2) == 0) swap(x, y, x - dir, y);
                            break;
                        case STEAM:
                            if (rnd.nextInt(60) == 0) { grid[i] = WATER; break; }
                            if (rnd.nextInt(3) != 0) {
                                if (empty(x, y - 1)) swap(x, y, x, y - 1);
                                else if (empty(x + dir, y - 1)) swap(x, y, x + dir, y - 1);
                                else if (empty(x + dir, y)) swap(x, y, x + dir, y);
                            }
                            if (y == 0 && rnd.nextInt(4) == 0) grid[i] = EMPTY;
                            break;
                        case FIRE:
                            if (neighbor(x, y, WATER)) {
                                grid[i] = STEAM;
                                life[i] = 0;
                                int wx = is(x + 1, y, WATER) ? x + 1 : is(x - 1, y, WATER) ? x - 1 : x;
                                int wy = is(x, y + 1, WATER) ? y + 1 : is(x, y - 1, WATER) ? y - 1 : y;
                                if (wx != x || wy != y) grid[wy * gw + wx] = EMPTY;
                                break;
                            }
                            // ignite flammables
                            int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                            for (int[] d : n) {
                                int nx = x + d[0], ny = y + d[1];
                                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
                                int ni = ny * gw + nx;
                                if (grid[ni] == WOOD && rnd.nextInt(18) == 0) {
                                    grid[ni] = FIRE;
                                    life[ni] = (byte) (25 + rnd.nextInt(40));
                                } else if (grid[ni] == OIL && rnd.nextInt(3) == 0) {
                                    grid[ni] = FIRE;
                                    life[ni] = (byte) (10 + rnd.nextInt(20));
                                }
                            }
                            life[i]--;
                            if (life[i] <= 0) {
                                grid[i] = EMPTY;
                            } else if (rnd.nextInt(3) == 0 && empty(x, y - 1)) {
                                swap(x, y, x, y - 1);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
            frame++;
        }

        int color(int t, int i) {
            int v = (i * 2654435761L % 1000) > 500 ? 1 : 0;
            switch (t) {
                case SAND: return v == 1 ? 0xFFE0C068 : 0xFFD2B055;
                case WATER: return v == 1 ? 0xFF3A7BD5 : 0xFF3470C4;
                case STONE: return v == 1 ? 0xFF7A7A85 : 0xFF6A6A75;
                case WOOD: return v == 1 ? 0xFF8B5A2B : 0xFF7C4F25;
                case OIL: return 0xFF4A3A2A;
                case STEAM: return 0xFFB8C4D8;
                case FIRE: {
                    int l = life[i];
                    return l > 25 ? 0xFFFFE066 : l > 10 ? 0xFFFF8A1A : 0xFFD43A10;
                }
                default: return 0xFF14141C;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (grid == null) return;
            if (touching) spawn();
            if (!paused) {
                step();
                step();
            }
            for (int i = 0; i < grid.length; i++) pixels[i] = color(grid[i], i);
            bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh);
            src.set(0, 0, gw, gh);
            dst.set(0, 0, gw * cell, gh * cell);
            c.drawColor(0xFF14141C);
            c.drawBitmap(bmp, src, dst, paint);
            postInvalidateOnAnimation();
        }
    }
}
