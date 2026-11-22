package com.dailyapps.pocketpet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws the pet as chunky pixel art on a little LCD screen. */
public class PetView extends View {
    private static final int G = 32;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final int[] px = new int[G * G];
    private final boolean[] solid = new boolean[G * G];
    private final float[] hsv = new float[3];

    PetState pet;
    long floatUntil, heartsUntil, sparkleUntil;
    int floatEmoji;

    public PetView(Context c) {
        super(c);
    }

    static String emoji(int cp) {
        return new String(Character.toChars(cp));
    }

    private static boolean ell(float x, float y, float cx, float cy, float rx, float ry) {
        float dx = (x - cx) / rx;
        float dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1f;
    }

    private int hsvColor(float h, float s, float v) {
        hsv[0] = h;
        hsv[1] = s;
        hsv[2] = v;
        return Color.HSVToColor(hsv);
    }

    private void set(int x, int y, int color) {
        if (x >= 0 && x < G && y >= 0 && y < G) px[y * G + x] = color;
    }

    @Override
    protected void onDraw(Canvas c) {
        postInvalidateDelayed(110);
        int w = getWidth();
        int h = getHeight();

        // LCD panel
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3E4A36);
        rf.set(0, 0, w, h);
        c.drawRoundRect(rf, 28, 28, p);
        p.setColor(0xFFD6E6C3);
        rf.set(6, 6, w - 6, h - 6);
        c.drawRoundRect(rf, 24, 24, p);

        if (pet == null || !pet.exists) return;
        long now = System.currentTimeMillis();

        float u = Math.min(w, h) / (float) G;
        float ox = (w - u * G) / 2f;
        float oy = (h - u * G) / 2f;

        buildSprite(now);
        p.setStyle(Paint.Style.FILL);
        for (int y = 0; y < G; y++) {
            for (int x = 0; x < G; x++) {
                int col = px[y * G + x];
                if (col != 0) {
                    p.setColor(col);
                    c.drawRect(ox + x * u, oy + y * u, ox + (x + 1) * u + 0.5f, oy + (y + 1) * u + 0.5f, p);
                }
            }
        }

        drawExtras(c, now, u, ox, oy);
    }

    private void buildSprite(long now) {
        java.util.Arrays.fill(px, 0);
        java.util.Arrays.fill(solid, false);

        int stage = pet.stage(now);
        int mood = pet.mood();
        boolean egg = stage == PetState.EGG;

        float rx, ry, cys;
        switch (stage) {
            case PetState.EGG:
                rx = 8f;
                ry = 11f;
                cys = 19f;
                break;
            case PetState.BABY:
                rx = 7f;
                ry = 6f;
                cys = 23f;
                break;
            case PetState.CHILD:
                rx = 8f;
                ry = 8f;
                cys = 21f;
                break;
            case PetState.TEEN:
                rx = 9f;
                ry = 9f;
                cys = 20f;
                break;
            default:
                rx = 10f;
                ry = 10f;
                cys = 19f;
                break;
        }

        int bob = (int) ((now / (mood == PetState.MOOD_SLEEP ? 900L : 450L)) % 2L);
        float cx = 16f;
        float cyb = cys + bob;
        if (egg) {
            cyb = cys;
            if (now % 3000L < 600L) cx += Math.round(Math.sin(now / 60.0) * 1.5);
        }
        if (mood == PetState.MOOD_DEAD) cyb = cys - 3f + bob;

        for (int y = 0; y < G; y++) {
            for (int x = 0; x < G; x++) {
                float fx = x + 0.5f;
                float fy = y + 0.5f;
                boolean s = ell(fx, fy, cx, cyb, rx, ry);
                if (stage >= PetState.CHILD) {
                    s = s || ell(fx, fy, cx - rx * 0.6f, cyb - ry + 0.5f, 2.4f, 3.2f)
                            || ell(fx, fy, cx + rx * 0.6f, cyb - ry + 0.5f, 2.4f, 3.2f);
                }
                if (stage >= PetState.TEEN) {
                    s = s || ell(fx, fy, cx - rx * 0.45f, cyb + ry - 0.5f, 3f, 2f)
                            || ell(fx, fy, cx + rx * 0.45f, cyb + ry - 0.5f, 3f, 2f);
                }
                if (stage >= PetState.ADULT) {
                    s = s || ell(fx, fy, cx - rx - 0.5f, cyb + 1f, 2f, 3f)
                            || ell(fx, fy, cx + rx + 0.5f, cyb + 1f, 2f, 3f)
                            || (fx > cx - 0.6f && fx < cx + 0.6f && fy > cyb - ry - 4.5f && fy < cyb - ry + 0.5f)
                            || ell(fx, fy, cx, cyb - ry - 4.5f, 1.7f, 1.7f);
                }
                solid[y * G + x] = s;
            }
        }

        int bodyCol, bellyCol, lineCol, spotCol;
        if (egg) {
            bodyCol = 0xFFF6F1DC;
            bellyCol = bodyCol;
            lineCol = 0xFF6B6048;
            spotCol = hsvColor(pet.hue, 0.45f, 0.95f);
        } else if (mood == PetState.MOOD_DEAD) {
            bodyCol = 0xFFD3DBE4;
            bellyCol = 0xFFEEF2F6;
            lineCol = 0xFF6B7684;
            spotCol = bodyCol;
        } else if (mood == PetState.MOOD_SICK) {
            bodyCol = hsvColor(95f, 0.40f, 0.82f);
            bellyCol = hsvColor(95f, 0.18f, 0.95f);
            lineCol = hsvColor(95f, 0.7f, 0.33f);
            spotCol = bodyCol;
        } else {
            bodyCol = hsvColor(pet.hue, 0.45f, 0.97f);
            bellyCol = hsvColor(pet.hue, 0.16f, 1f);
            lineCol = hsvColor(pet.hue, 0.7f, 0.34f);
            spotCol = bodyCol;
        }

        for (int y = 0; y < G; y++) {
            for (int x = 0; x < G; x++) {
                if (!solid[y * G + x]) continue;
                boolean edge = !sol(x - 1, y) || !sol(x + 1, y) || !sol(x, y - 1) || !sol(x, y + 1);
                int col;
                if (edge) {
                    col = lineCol;
                } else {
                    col = bodyCol;
                    float fx = x + 0.5f;
                    float fy = y + 0.5f;
                    if (egg) {
                        if ((x * 5 + y * 3) % 7 == 0 && ell(fx, fy, cx, cyb, rx - 1.5f, ry - 1.5f)) col = spotCol;
                    } else if (ell(fx, fy, cx, cyb + ry * 0.35f, rx * 0.55f, ry * 0.5f)) {
                        col = bellyCol;
                    }
                }
                px[y * G + x] = col;
            }
        }

        if (egg) {
            long left = pet.bornAt + PetState.HATCH_MS - now;
            if (left < 25000L) {
                int cy = Math.round(cyb);
                int x0 = Math.round(cx) - 3;
                set(x0, cy, lineCol);
                set(x0 + 1, cy + 1, lineCol);
                set(x0 + 2, cy, lineCol);
                set(x0 + 3, cy + 1, lineCol);
                set(x0 + 4, cy, lineCol);
                if (left < 10000L) {
                    set(x0 + 5, cy + 1, lineCol);
                    set(x0 + 6, cy, lineCol);
                    set(x0 + 1, cy - 1, lineCol);
                }
            }
            return;
        }

        drawFace(now, mood, cx, cyb, rx, ry);
    }

    private boolean sol(int x, int y) {
        return x >= 0 && x < G && y >= 0 && y < G && solid[y * G + x];
    }

    private void drawFace(long now, int mood, float cx, float cyb, float rx, float ry) {
        int dark = 0xFF1E2A1E;
        int ex1 = Math.round(cx - rx * 0.42f) - 1;
        int ex2 = Math.round(cx + rx * 0.42f);
        int ey = Math.round(cyb - ry * 0.2f);
        int mx = Math.round(cx);
        int my = Math.round(cyb + ry * 0.3f);
        boolean eating = now < floatUntil;
        boolean blink = (now % 3200L) < 160L;

        for (int e = 0; e < 2; e++) {
            int ex = e == 0 ? ex1 : ex2;
            if (mood == PetState.MOOD_DEAD || mood == PetState.MOOD_SICK) {
                set(ex, ey, dark);
                set(ex + 2, ey, dark);
                set(ex + 1, ey + 1, dark);
                set(ex, ey + 2, dark);
                set(ex + 2, ey + 2, dark);
            } else if (mood == PetState.MOOD_SLEEP || blink) {
                set(ex, ey + 1, dark);
                set(ex + 1, ey + 1, dark);
            } else if (mood == PetState.MOOD_HAPPY || eating) {
                set(ex, ey + 1, dark);
                set(ex + 1, ey, dark);
                set(ex + 2, ey + 1, dark);
            } else {
                set(ex, ey, dark);
                set(ex + 1, ey, dark);
                set(ex, ey + 1, dark);
                set(ex + 1, ey + 1, dark);
                if (mood == PetState.MOOD_SAD) set(e == 0 ? ex : ex + 1, ey + 3, 0xFF4A90E2);
            }
        }

        if (mood == PetState.MOOD_HAPPY || mood == PetState.MOOD_OK) {
            int cheek = 0xFFFF8FA3;
            set(ex1 - 1, ey + 3, cheek);
            set(ex1, ey + 3, cheek);
            set(ex2 + 2, ey + 3, cheek);
            set(ex2 + 3, ey + 3, cheek);
        }

        if (mood == PetState.MOOD_DEAD) return;
        if (eating && (now / 200L) % 2L == 0L) {
            for (int dx = -1; dx <= 1; dx++) {
                set(mx + dx, my, dark);
                set(mx + dx, my + 1, dark);
            }
        } else if (mood == PetState.MOOD_HAPPY) {
            set(mx - 2, my, dark);
            set(mx - 1, my + 1, dark);
            set(mx, my + 1, dark);
            set(mx + 1, my + 1, dark);
            set(mx + 2, my, dark);
        } else if (mood == PetState.MOOD_SAD || mood == PetState.MOOD_SICK) {
            set(mx - 2, my + 1, dark);
            set(mx - 1, my, dark);
            set(mx, my, dark);
            set(mx + 1, my, dark);
            set(mx + 2, my + 1, dark);
        } else if (mood == PetState.MOOD_SLEEP) {
            set(mx - 1, my, dark);
            set(mx, my, dark);
        } else {
            set(mx - 1, my, dark);
            set(mx, my, dark);
            set(mx + 1, my, dark);
        }
    }

    private void drawText(Canvas c, String s, float gx, float gy, float size, float u, float ox, float oy) {
        tp.setTextSize(size * u);
        c.drawText(s, ox + gx * u, oy + gy * u + size * u * 0.85f, tp);
    }

    private void drawExtras(Canvas c, long now, float u, float ox, float oy) {
        tp.setTextAlign(Paint.Align.CENTER);
        int mood = pet.mood();
        boolean egg = !pet.hatched;
        if (egg) return;

        if (mood == PetState.MOOD_DEAD) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u * 0.9f);
            p.setColor(0xFFFFD84A);
            rf.set(ox + 11f * u, oy + 3f * u, ox + 21f * u, oy + 6f * u);
            c.drawOval(rf, p);
            p.setStyle(Paint.Style.FILL);
        }

        for (int i = 0; i < pet.poops && i < 4; i++) {
            float gx = i == 0 ? 26f : i == 1 ? 3f : i == 2 ? 27f : 2.5f;
            float gy = i == 0 ? 25f : i == 1 ? 25f : 19f;
            if (i == 3) gy = 19f;
            drawText(c, emoji(0x1F4A9), gx, gy, 4.5f, u, ox, oy);
        }

        if (mood == PetState.MOOD_SLEEP) {
            float bobY = (now / 700L) % 2L == 0L ? 0f : 1f;
            drawText(c, emoji(0x1F4A4), 25f, 4f + bobY, 5f, u, ox, oy);
        }
        if (mood == PetState.MOOD_SICK) {
            drawText(c, emoji(0x1F912), 6f, 4f, 5f, u, ox, oy);
        }
        if (pet.needsAttention() && (now / 500L) % 2L == 0L) {
            drawText(c, emoji(0x2757), 4f, 2f, 5f, u, ox, oy);
        }
        if (now < heartsUntil) {
            float rise = ((heartsUntil - now) % 1000L) / 1000f * 3f;
            drawText(c, emoji(0x2764) + emoji(0xFE0F), 16f, 3f + rise, 5f, u, ox, oy);
        }
        if (now < sparkleUntil) {
            drawText(c, emoji(0x2728), 6f, 10f + ((now / 250L) % 2L), 5f, u, ox, oy);
            drawText(c, emoji(0x2728), 26f, 12f - ((now / 250L) % 2L), 5f, u, ox, oy);
        }
        if (now < floatUntil) {
            float t = 1f - (floatUntil - now) / 2500f;
            drawText(c, emoji(floatEmoji), 25f - t * 6f, 16f + (now / 300L) % 2L, 5f, u, ox, oy);
        }
    }
}
