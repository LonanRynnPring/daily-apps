package com.dailyapps.orbitsling;

import java.util.Random;

/** A procedurally generated, verified-solvable gravity puzzle. Probes land on planets and can hop between them. */
final class Level {
    static final float W = 1.4f;
    static final float DT = 0.01f;
    static final float SPEED = 4f;
    static final float MAXPULL = 0.45f;
    static final int MAXP = 6;
    private static final int SOLVER_CAP = 1500;
    private static final int BUCKETS = 12;
    static final int ORBIT_CAP = 6000;

    interface Stop {
        boolean stop();
    }

    float h = 2.6f;
    float sx, sy, tx, ty;
    float tr = 0.055f;
    int n;
    int par = 1;
    int wins = 0;
    final float[] px = new float[MAXP];
    final float[] py = new float[MAXP];
    final float[] pr = new float[MAXP];
    final float[] pg = new float[MAXP];

    /** The flying probe. step() returns 0 flying, 1 reached target, 2 landed on a planet, 3 lost in space. */
    static final class Probe {
        float x, y, vx, vy;
        int steps;
        int landed = -1;

        void reset(float nx, float ny, float nvx, float nvy) {
            x = nx;
            y = ny;
            vx = nvx;
            vy = nvy;
            steps = 0;
            landed = -1;
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
            float ex = x - l.tx;
            float ey = y - l.ty;
            if (ex * ex + ey * ey < l.tr * l.tr) return 1;
            for (int i = 0; i < l.n; i++) {
                float dx = x - l.px[i];
                float dy = y - l.py[i];
                float d2 = dx * dx + dy * dy;
                if (d2 < l.pr[i] * l.pr[i]) {
                    float d = (float) Math.sqrt(d2);
                    if (d < 1e-5f) {
                        dx = 0f;
                        dy = -1f;
                        d = 1f;
                    }
                    float rr = l.pr[i] + 0.004f;
                    x = l.px[i] + dx / d * rr;
                    y = l.py[i] + dy / d * rr;
                    vx = 0f;
                    vy = 0f;
                    landed = i;
                    return 2;
                }
            }
            if (x < -0.5f || x > W + 0.5f || y < -0.5f || y > l.h + 0.5f) return 3;
            return 0;
        }
    }

    static int planetCount(int num) {
        if (num <= 3) return 3;
        if (num <= 8) return 4;
        if (num <= 16) return 5;
        return 6;
    }

    static int minPar(int num) {
        if (num <= 3) return 1;
        if (num <= 10) return 2;
        return 3;
    }

    static int maxWins(int num) {
        return Math.max(3, 60 - 2 * num);
    }

    static Level make(int num, float height) {
        Level best = null;
        Level last = null;
        for (int att = 0; att < 40; att++) {
            Level l = build(num, att, height);
            last = l;
            if (l.n < 2) continue;
            int par = l.solve(num >= 2 && att < 28);
            if (par <= 0) continue;
            l.par = par;
            if (par >= minPar(num) && l.wins <= maxWins(num)) return l;
            if (best == null || par > best.par || (par == best.par && l.wins < best.wins)) best = l;
        }
        return best != null ? best : last;
    }

    private static Level build(int num, int att, float height) {
        Random r = new Random(num * 7919L + att * 104729L + 13L);
        Level l = new Level();
        l.h = height;
        l.sx = W * (0.07f + r.nextFloat() * 0.11f);
        l.sy = height * (0.14f + r.nextFloat() * 0.72f);
        l.tx = W * (0.80f + r.nextFloat() * 0.13f);
        l.ty = height * (0.14f + r.nextFloat() * 0.72f);
        l.tr = Math.max(0.042f, 0.065f - num * 0.0008f);
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
                    float off = (r.nextFloat() - 0.5f) * 0.5f;
                    x = mx + (-dy / len) * off;
                    y = my + (dx / len) * off;
                } else {
                    x = W * (0.14f + r.nextFloat() * 0.72f);
                    y = height * (0.10f + r.nextFloat() * 0.80f);
                }
                boolean ok = dist(x, y, l.sx, l.sy) > rad + 0.12f && dist(x, y, l.tx, l.ty) > rad + 0.10f;
                for (int j = 0; j < l.n && ok; j++) {
                    if (dist(x, y, l.px[j], l.py[j]) < rad + l.pr[j] + 0.09f) ok = false;
                }
                if (!ok) continue;
                float g = 11f * rad * rad;
                if (i >= 1 && num >= 5 && r.nextFloat() < 0.35f) g = -g * 0.8f;
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

    private int run(Probe p, float x, float y, float vx, float vy) {
        p.reset(x, y, vx, vy);
        int st = 0;
        while (st == 0) {
            st = p.step(this);
            if (st == 0 && p.steps >= SOLVER_CAP) return 4;
        }
        return st;
    }

    private boolean wins(Probe p, float x, float y, double ang, float v) {
        return run(p, x, y, (float) (Math.cos(ang) * v), (float) (Math.sin(ang) * v)) == 1;
    }

    /**
     * Breadth-first search over shots and planet landings. Returns the minimum number of shots
     * (robust to small aiming errors), or 0 if no solution was found.
     */
    private int solve(boolean rejectDirect) {
        Probe p = new Probe();
        if (rejectDirect) {
            double ang = Math.atan2(ty - sy, tx - sx);
            for (int k = 0; k < 10; k++) {
                if (wins(p, sx, sy, ang, (0.08f + 0.03f * k) * SPEED)) return 0;
            }
        }
        int maxNodes = 1 + n * BUCKETS;
        float[] nx = new float[maxNodes];
        float[] ny = new float[maxNodes];
        int[] depth = new int[maxNodes];
        boolean[][] seen = new boolean[n][BUCKETS];
        int count = 1;
        nx[0] = sx;
        ny[0] = sy;
        int stepsDeg = 72;
        int found = 0;
        wins = 0;
        for (int head = 0; head < count; head++) {
            if (found > 0 && depth[head] >= found) break;
            if (depth[head] >= 4) continue;
            for (int a = 0; a < stepsDeg; a++) {
                double ang = a * 2.0 * Math.PI / stepsDeg;
                for (int k = 0; k < 6; k++) {
                    float v = (0.08f + 0.054f * k) * SPEED;
                    int st = run(p, nx[head], ny[head], (float) (Math.cos(ang) * v), (float) (Math.sin(ang) * v));
                    if (st == 1) {
                        double d = 0.035;
                        if (wins(p, nx[head], ny[head], ang + d, v) && wins(p, nx[head], ny[head], ang - d, v)) {
                            found = depth[head] + 1;
                            wins++;
                        }
                    } else if (st == 2 && count < maxNodes && found == 0) {
                        int pl = p.landed;
                        double la = Math.atan2(p.y - py[pl], p.x - px[pl]);
                        int b = (int) (((la + Math.PI) / (2 * Math.PI)) * BUCKETS);
                        if (b >= BUCKETS) b = BUCKETS - 1;
                        if (b < 0) b = 0;
                        if (!seen[pl][b]) {
                            seen[pl][b] = true;
                            nx[count] = p.x;
                            ny[count] = p.y;
                            depth[count] = depth[head] + 1;
                            count++;
                        }
                    }
                }
            }
        }
        return found;
    }

    private int flightScore(Probe p, float x, float y, float vx, float vy) {
        p.reset(x, y, vx, vy);
        while (true) {
            int st = p.step(this);
            if (st == 1) return 0;
            if (st != 0) return p.steps;
            if (p.steps >= ORBIT_CAP) return ORBIT_CAP;
        }
    }

    /**
     * Searches for a launch velocity from (x, y) that keeps the probe flying as long as possible
     * (reaching ORBIT_CAP means a stable orbit or endless loop). Returns {vx, vy, steps} or null.
     */
    float[] findOrbit(float x, float y, long deadline, Stop stop) {
        Probe p = new Probe();
        int best = -1;
        float bvx = 0f;
        float bvy = 0f;
        for (int a = 0; a < 120; a++) {
            double ang = a * 2.0 * Math.PI / 120;
            for (int k = 0; k < 10; k++) {
                float v = (0.08f + 0.041f * k) * SPEED;
                float vx = (float) (Math.cos(ang) * v);
                float vy = (float) (Math.sin(ang) * v);
                int sc = flightScore(p, x, y, vx, vy);
                if (sc > best) {
                    best = sc;
                    bvx = vx;
                    bvy = vy;
                    if (best >= ORBIT_CAP) return new float[]{bvx, bvy, best};
                }
            }
            if (stop.stop() || System.currentTimeMillis() > deadline) break;
        }
        Random r = new Random(7);
        float minV = 0.08f * SPEED;
        float maxV = MAXPULL * SPEED;
        while (!stop.stop() && System.currentTimeMillis() < deadline) {
            float vx;
            float vy;
            if (best > 0 && r.nextInt(10) < 7) {
                double ang = Math.atan2(bvy, bvx) + r.nextGaussian() * (r.nextBoolean() ? 0.03 : 0.003);
                double mag = Math.sqrt(bvx * bvx + bvy * bvy) * (1.0 + r.nextGaussian() * (r.nextBoolean() ? 0.03 : 0.003));
                mag = Math.max(minV, Math.min(maxV, mag));
                vx = (float) (Math.cos(ang) * mag);
                vy = (float) (Math.sin(ang) * mag);
            } else {
                double ang = r.nextDouble() * 2.0 * Math.PI;
                double mag = minV + r.nextDouble() * (maxV - minV);
                vx = (float) (Math.cos(ang) * mag);
                vy = (float) (Math.sin(ang) * mag);
            }
            int sc = flightScore(p, x, y, vx, vy);
            if (sc > best) {
                best = sc;
                bvx = vx;
                bvy = vy;
                if (best >= ORBIT_CAP) break;
            }
        }
        if (best <= 0) return null;
        return new float[]{bvx, bvy, best};
    }
}
