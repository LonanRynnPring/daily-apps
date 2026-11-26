package com.dailyapps.poolshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ghost-ball shot finder with a simple make-probability model and cue-ball position estimate. */
final class Shots {
    private Shots() {}

    static final int ANY = 0, SOLIDS = 1, STRIPES = 2, LOWEST = 3, EIGHT = 4;
    static final String[] TARGET_NAMES = {"Any ball", "Solids", "Stripes", "Lowest (9-ball)", "8-ball only"};

    static final float[][] POCKETS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}};
    static final boolean[] CORNER = {true, false, true, true, false, true};
    /** Names as seen on screen, where the table is drawn upright (x runs downwards, y=0 on the right). */
    static final String[] POCKET_NAMES = {"top-right corner", "right side pocket", "bottom-right corner",
            "top-left corner", "left side pocket", "bottom-left corner"};

    static final class Config {
        float r = 0.0225f;
        float cornerSlop = 0.028f;
        float sideSlop = 0.032f;
        double sigma = 0.003;
        boolean banks = true;
        int target = ANY;
    }

    static final class Shot {
        Ball obj;
        int pocket;
        int rail = -1;
        float ax, ay;   // pocket aim point
        float kx, ky;   // bank point (if rail >= 0)
        float gx, gy;   // ghost ball (cue position at contact)
        double cut;     // radians
        boolean cutRight;
        double d1, travel;
        double prob;
        double next = 1;
        boolean scratch;
        float[] cuePath = new float[0];
        float restX, restY;
        double score;
    }

    static Ball cue(List<Ball> balls) {
        for (Ball b : balls) if (b.num == 0) return b;
        return null;
    }

    static List<Ball> targets(List<Ball> balls, int target) {
        List<Ball> out = new ArrayList<>();
        Ball eight = null;
        Ball lowest = null;
        for (Ball b : balls) {
            if (b.num == 0) continue;
            if (b.num == 8) eight = b;
            if (lowest == null || b.num < lowest.num) lowest = b;
            switch (target) {
                case SOLIDS:
                    if (b.num >= 1 && b.num <= 7) out.add(b);
                    break;
                case STRIPES:
                    if (b.num >= 9) out.add(b);
                    break;
                case ANY:
                    out.add(b);
                    break;
                default:
                    break;
            }
        }
        if (target == LOWEST) {
            if (lowest != null) out.add(lowest);
        } else if (target == EIGHT) {
            if (eight != null) out.add(eight);
        } else if ((target == SOLIDS || target == STRIPES) && out.isEmpty() && eight != null) {
            out.add(eight);
        }
        return out;
    }

    static float[] aimPoint(int p, float r) {
        float px = POCKETS[p][0], py = POCKETS[p][1];
        if (CORNER[p]) {
            float k = 0.5f * r;
            return new float[]{px == 0 ? -k : px + k, py == 0 ? -k : py + k};
        }
        return new float[]{px, py == 0 ? -0.8f * r : 1f + 0.8f * r};
    }

    static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay;
        double l2 = vx * vx + vy * vy;
        double t = l2 < 1e-12 ? 0 : ((px - ax) * vx + (py - ay) * vy) / l2;
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        double dx = ax + vx * t - px, dy = ay + vy * t - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Evaluates pocketing o in pocket p, optionally off one rail (0: y=r, 1: y=1-r, 2: x=r, 3: x=2-r). */
    static Shot eval(List<Ball> balls, Ball cue, Ball o, int p, int rail, Config c) {
        float r = c.r;
        float[] a = aimPoint(p, r);
        double ax = a[0], ay = a[1];
        double tx = ax, ty = ay, kx = 0, ky = 0;
        float px = POCKETS[p][0], py = POCKETS[p][1];
        if (rail >= 0) {
            if (rail == 0 && py == 0) return null;
            if (rail == 1 && py == 1) return null;
            if (rail == 2 && px == 0) return null;
            if (rail == 3 && px == 2) return null;
            double mx = ax, my = ay, line;
            if (rail <= 1) {
                line = rail == 0 ? r : 1 - r;
                my = 2 * line - ay;
                if ((o.y - line) * (my - line) >= 0) return null;
                double t = (line - o.y) / (my - o.y);
                if (t <= 0 || t >= 1) return null;
                kx = o.x + (mx - o.x) * t;
                ky = line;
                double fr = kx % 1.0;
                if (kx < 0.12 || kx > 1.88 || (kx > 0.88 && kx < 1.12) || fr < 0) return null;
            } else {
                line = rail == 2 ? r : 2 - r;
                mx = 2 * line - ax;
                if ((o.x - line) * (mx - line) >= 0) return null;
                double t = (line - o.x) / (mx - o.x);
                if (t <= 0 || t >= 1) return null;
                kx = line;
                ky = o.y + (my - o.y) * t;
                if (ky < 0.12 || ky > 0.88) return null;
            }
            tx = kx;
            ty = ky;
        }
        double dx = tx - o.x, dy = ty - o.y;
        double len1 = Math.sqrt(dx * dx + dy * dy);
        if (len1 < 1e-6) return null;
        dx /= len1;
        dy /= len1;
        double travel = len1;
        double fx = dx, fy = dy;
        if (rail >= 0) {
            double ex = ax - kx, ey = ay - ky;
            double l2 = Math.sqrt(ex * ex + ey * ey);
            if (l2 < 1e-6) return null;
            travel += l2;
            fx = ex / l2;
            fy = ey / l2;
        }
        double gx = o.x - dx * 2 * r, gy = o.y - dy * 2 * r;
        if (gx < r * 0.9 || gx > 2 - r * 0.9 || gy < r * 0.9 || gy > 1 - r * 0.9) return null;
        double cx = gx - cue.x, cy = gy - cue.y;
        double d1 = Math.sqrt(cx * cx + cy * cy);
        if (d1 < 1e-4) return null;
        cx /= d1;
        cy /= d1;
        double cosT = cx * dx + cy * dy;
        if (cosT < 0.2079) return null; // > 78 degree cut
        double lim = 2 * r * 0.97;
        for (Ball b : balls) {
            if (b == cue || b == o) continue;
            if (segDist(b.x, b.y, cue.x, cue.y, gx, gy) < lim) return null;
            if (segDist(b.x, b.y, o.x, o.y, tx, ty) < lim) return null;
            if (rail >= 0 && segDist(b.x, b.y, kx, ky, ax, ay) < lim) return null;
        }
        // pocket acceptance
        double bx, by;
        if (CORNER[p]) {
            bx = px == 0 ? 0.7071 : -0.7071;
            by = py == 0 ? 0.7071 : -0.7071;
        } else {
            bx = 0;
            by = py == 0 ? 1 : -1;
        }
        double cosA = -(fx * bx + fy * by);
        double w = CORNER[p] ? c.cornerSlop * clamp01((cosA - 0.2) / 0.8)
                : c.sideSlop * clamp01((cosA - 0.5) / 0.5);
        if (w <= 1e-5) return null;
        double allowed = Math.atan(w / Math.max(travel, 0.05));
        if (rail >= 0) allowed *= 0.55;
        double dc = allowed * 2 * r * Math.max(0.05, cosT) / Math.max(d1, 2 * r);
        double prob = 1 - Math.exp(-(dc * dc) / (2 * c.sigma * c.sigma));
        double total = d1 + travel;
        if (total > 2.2) prob *= 0.9; // power shots lose accuracy
        if (rail >= 0) prob *= 0.85;

        Shot s = new Shot();
        s.obj = o;
        s.pocket = p;
        s.rail = rail;
        s.ax = (float) ax;
        s.ay = (float) ay;
        s.kx = (float) kx;
        s.ky = (float) ky;
        s.gx = (float) gx;
        s.gy = (float) gy;
        s.cut = Math.acos(Math.min(1, cosT));
        s.cutRight = (cx * dy - cy * dx) > 0;
        s.d1 = d1;
        s.travel = travel;
        s.prob = prob;
        return s;
    }

    /** Stun-shot cue-ball path along the tangent line, with rail bounces, ball stops and scratch check. */
    static void traceCue(Shot s, List<Ball> balls, Ball cue, Config c) {
        float r = c.r;
        double dx = s.obj.x - s.gx, dy = s.obj.y - s.gy;
        double l = Math.sqrt(dx * dx + dy * dy);
        dx /= l;
        dy /= l;
        double cx = s.gx - cue.x, cy = s.gy - cue.y;
        double d1 = Math.sqrt(cx * cx + cy * cy);
        cx /= d1;
        cy /= d1;
        double cosT = Math.cos(s.cut);
        List<Float> pts = new ArrayList<>();
        pts.add(s.gx);
        pts.add(s.gy);
        if (s.cut < Math.toRadians(4)) {
            s.restX = s.gx;
            s.restY = s.gy;
            s.cuePath = new float[]{s.gx, s.gy};
            return;
        }
        double vx = cx - dx * cosT, vy = cy - dy * cosT;
        double vl = Math.sqrt(vx * vx + vy * vy);
        vx /= vl;
        vy /= vl;
        double L = 0.15 + 0.55 * Math.sin(s.cut) + 0.15 * Math.min(1, (s.d1 + s.travel) / 1.5);
        double step = r * 0.5;
        double x = s.gx, y = s.gy;
        boolean scratch = false;
        for (double d = 0; d < L; d += step) {
            x += vx * step;
            y += vy * step;
            boolean bounce = false;
            if (x < r) { x = 2 * r - x; vx = -vx; bounce = true; }
            if (x > 2 - r) { x = 2 * (2 - r) - x; vx = -vx; bounce = true; }
            if (y < r) { y = 2 * r - y; vy = -vy; bounce = true; }
            if (y > 1 - r) { y = 2 * (1 - r) - y; vy = -vy; bounce = true; }
            for (int p = 0; p < 6; p++) {
                double ex = x - POCKETS[p][0], ey = y - POCKETS[p][1];
                double lim = CORNER[p] ? 1.7 * r : 1.4 * r;
                if (ex * ex + ey * ey < lim * lim) scratch = true;
            }
            if (scratch) break;
            boolean hit = false;
            for (Ball b : balls) {
                if (b == cue || b == s.obj) continue;
                double ex = x - b.x, ey = y - b.y;
                if (ex * ex + ey * ey < 4 * r * r) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                x -= vx * step;
                y -= vy * step;
                break;
            }
            if (bounce) {
                pts.add((float) x);
                pts.add((float) y);
            }
        }
        pts.add((float) x);
        pts.add((float) y);
        s.scratch = scratch;
        s.restX = (float) x;
        s.restY = (float) y;
        float[] arr = new float[pts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = pts.get(i);
        s.cuePath = arr;
    }

    static List<Shot> find(List<Ball> balls, Config c) {
        List<Shot> out = new ArrayList<>();
        Ball cue = cue(balls);
        if (cue == null) return out;
        for (Ball o : targets(balls, c.target)) {
            for (int p = 0; p < 6; p++) {
                Shot s = eval(balls, cue, o, p, -1, c);
                if (s != null) out.add(s);
                if (c.banks) {
                    for (int rail = 0; rail < 4; rail++) {
                        Shot b = eval(balls, cue, o, p, rail, c);
                        if (b != null) out.add(b);
                    }
                }
            }
        }
        for (Shot s : out) {
            traceCue(s, balls, cue, c);
            s.next = nextValue(s, balls, cue, c);
            s.score = s.prob * (s.scratch ? 0.45 : 1.0) * (0.65 + 0.35 * s.next);
        }
        Collections.sort(out, (x, y) -> Double.compare(y.score, x.score));
        return out;
    }

    /** Best direct-shot probability from where the cue ball stops, with the object ball gone. */
    private static double nextValue(Shot s, List<Ball> balls, Ball cue, Config c) {
        if (s.scratch) return 0;
        List<Ball> rest = new ArrayList<>();
        Ball fake = new Ball(0, s.restX, s.restY);
        rest.add(fake);
        for (Ball b : balls) if (b != cue && b != s.obj) rest.add(b);
        List<Ball> tg = targets(rest, c.target);
        if (tg.isEmpty()) return 1;
        double best = 0;
        for (Ball o : tg) {
            for (int p = 0; p < 6; p++) {
                Shot n = eval(rest, fake, o, p, -1, c);
                if (n != null && n.prob > best) best = n.prob;
            }
        }
        return best;
    }

    static String fraction(double cut) {
        double deg = Math.toDegrees(cut);
        if (deg < 4) return "full-ball hit";
        double overlap = 1 - Math.sin(cut);
        if (overlap > 0.66) return "¾-ball hit";
        if (overlap > 0.4) return "½-ball hit";
        if (overlap > 0.19) return "¼-ball hit";
        if (overlap > 0.09) return "⅛-ball hit";
        return "very thin cut";
    }

    static String pace(Shot s) {
        double t = s.d1 + s.travel;
        if (s.rail >= 0) t += 0.4;
        if (t < 0.8) return "soft pace";
        if (t < 1.6) return "medium pace";
        return "firm pace";
    }
}
