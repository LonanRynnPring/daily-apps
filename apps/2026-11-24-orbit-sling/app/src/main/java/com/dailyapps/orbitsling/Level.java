package com.dailyapps.orbitsling;

import java.util.Random;

/** A procedurally generated, verified-solvable gravity puzzle. */
final class Level {
    static final float W = 1f;
    static final float H = 1.6f;
    static final float DT = 0.01f;
    static final float SPEED = 4f;
    static final float MAXPULL = 0.35f;
    static final int MAX_STEPS = 900;

    float sx, sy, tx, ty;
    float tr = 0.055f;
    int n;
    final float[] px = new float[4];
    final float[] py = new float[4];
    final float[] pr = new float[4];
    final float[] pg = new float[4];

    /** The flying probe. step() returns 0 flying, 1 reached target, 2 crashed, 3 lost in space. */
    static final class Probe {
        float x, y, vx, vy;
        int steps;

        void reset(Level l, float nvx, float nvy) {
            x = l.sx;
            y = l.sy;
            vx = nvx;
            vy = nvy;
            steps = 0;
        }

        int step(Level l) {
            float ax = 0f;
            float ay = 0f;
            for (int i = 0; i < l.n; i++) {
                float dx = l.px[i] - x;
                float dy = l.py[i] - y;
                float d2 = dx * dx + dy * dy + 0.0004f;
                float inv = (float) (1.0 / (d2 * Math.sqrt(d2)));
                ax += l.pg[i] * dx * inv;
                ay += l.pg[i] * dy * inv;
            }
            vx += ax * DT;
            vy += ay * DT;
            x += vx * DT;
            y += vy * DT;
            steps++;
            for (int i = 0; i < l.n; i++) {
                float dx = x - l.px[i];
                float dy = y - l.py[i];
                if (dx * dx + dy * dy < l.pr[i] * l.pr[i]) return 2;
            }
            float ex = x - l.tx;
            float ey = y - l.ty;
            if (ex * ex + ey * ey < l.tr * l.tr) return 1;
            if (x < -0.4f || x > 1.4f || y < -0.4f || y > 2.0f || steps >= MAX_STEPS) return 3;
            return 0;
        }
    }

    static int planetCount(int num) {
        if (num <= 5) return 1;
        if (num <= 10) return 2;
        if (num <= 18) return 3;
        return 4;
    }

    static Level make(int num) {
        Level best = null;
        for (int att = 0; att < 150; att++) {
            Level l = build(num, att);
            best = l;
            if (l.n > 0 && l.valid(num, att)) return l;
        }
        return best;
    }

    private static Level build(int num, int att) {
        Random r = new Random(num * 7919L + att * 104729L + 13L);
        Level l = new Level();
        l.sx = 0.10f + r.nextFloat() * 0.15f;
        l.sy = 0.30f + r.nextFloat() * 1.0f;
        l.tx = 0.75f + r.nextFloat() * 0.15f;
        l.ty = 0.30f + r.nextFloat() * 1.0f;
        int count = planetCount(num);
        for (int i = 0; i < count; i++) {
            for (int tries = 0; tries < 40; tries++) {
                float rad = 0.04f + r.nextFloat() * 0.05f;
                float x;
                float y;
                if (i == 0) {
                    float mx = (l.sx + l.tx) / 2f;
                    float my = (l.sy + l.ty) / 2f;
                    float dx = l.tx - l.sx;
                    float dy = l.ty - l.sy;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    float off = (r.nextFloat() - 0.5f) * 0.3f;
                    x = mx + (-dy / len) * off;
                    y = my + (dx / len) * off;
                } else {
                    x = 0.25f + r.nextFloat() * 0.5f;
                    y = 0.25f + r.nextFloat() * 1.1f;
                }
                boolean ok = dist(x, y, l.sx, l.sy) > rad + 0.12f && dist(x, y, l.tx, l.ty) > rad + 0.10f;
                for (int j = 0; j < l.n && ok; j++) {
                    if (dist(x, y, l.px[j], l.py[j]) < rad + l.pr[j] + 0.10f) ok = false;
                }
                if (!ok) continue;
                float g = 9f * rad * rad;
                if (i >= 1 && num >= 8 && r.nextFloat() < 0.35f) g = -g * 0.8f;
                l.px[l.n] = x;
                l.py[l.n] = y;
                l.pr[l.n] = rad;
                l.pg[l.n] = g;
                l.n++;
                break;
            }
        }
        return l;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean valid(int num, int att) {
        Probe p = new Probe();
        // A straight-line shot must not simply work (needs some gravity bending).
        if (num >= 2 && att < 100) {
            float ang = (float) Math.atan2(ty - sy, tx - sx);
            for (int k = 0; k < 10; k++) {
                float v = (0.08f + 0.03f * k) * SPEED;
                if (run(p, (float) Math.cos(ang) * v, (float) Math.sin(ang) * v) == 1) return false;
            }
        }
        int wins = 0;
        for (int a = 0; a < 120; a++) {
            double ang = a * 3.0 * Math.PI / 180.0;
            for (int k = 0; k < 10; k++) {
                float v = (0.08f + 0.03f * k) * SPEED;
                if (run(p, (float) (Math.cos(ang) * v), (float) (Math.sin(ang) * v)) == 1) wins++;
            }
        }
        return wins >= (num < 10 ? 3 : 2);
    }

    private int run(Probe p, float vx, float vy) {
        p.reset(this, vx, vy);
        int st = 0;
        while (st == 0) st = p.step(this);
        return st;
    }
}
