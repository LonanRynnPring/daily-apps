package com.dailyapps.laserbounce;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A procedurally generated laser puzzle. The generator builds a solution path first, so every level is solvable. */
public class Level {
    public static final int EMPTY = 0, SLASH = 1, BACK = 2;
    // directions: 0 right, 1 down, 2 left, 3 up
    public static final int[] DX = {1, 0, -1, 0};
    public static final int[] DY = {0, 1, 0, -1};
    // reflection tables: [mirror type][incoming direction] -> outgoing direction
    public static final int[][] REFL = {{0, 1, 2, 3}, {3, 2, 1, 0}, {1, 0, 3, 2}};

    public int n;
    public int emitRow;
    public int number;
    public int budget;
    public int par;
    public int targetCount;
    public boolean[][] wall;
    public boolean[][] target;
    public int[][] solution;

    public static class Trace {
        public List<float[]> pts = new ArrayList<float[]>();
        public boolean[][] hit;
        public int hitCount;
    }

    public static Level generate(int number) {
        long seed = number * 7919L + 13L;
        for (int attempt = 0; attempt < 300; attempt++) {
            Random r = new Random(seed + attempt * 104729L);
            Level lv = tryGen(number, r);
            if (lv != null) return lv;
        }
        return fallback(number);
    }

    private static Level fallback(int number) {
        Level lv = new Level();
        lv.number = number;
        lv.n = 5;
        lv.emitRow = 4;
        lv.wall = new boolean[5][5];
        lv.target = new boolean[5][5];
        lv.solution = new int[5][5];
        lv.solution[4][2] = SLASH;
        lv.target[0][2] = true;
        lv.targetCount = 1;
        lv.budget = 1;
        lv.par = 1;
        return lv;
    }

    private static Level tryGen(int number, Random r) {
        int n = Math.min(9, 5 + number / 10);
        int turns = Math.min(1 + number / 5, 6);
        boolean[][] vis = new boolean[n][n];
        int[][] mir = new int[n][n];
        int er = r.nextInt(n);
        int x = 0, y = er, d = 0;
        List<int[]> path = new ArrayList<int[]>();
        vis[y][x] = true;
        path.add(new int[]{x, y});
        int turnsLeft = turns;
        int guard = 0;
        while (guard++ < 50) {
            int maxLen = 0;
            int cx = x, cy = y;
            while (true) {
                int nx = cx + DX[d], ny = cy + DY[d];
                if (nx < 0 || ny < 0 || nx >= n || ny >= n || vis[ny][nx]) break;
                maxLen++;
                cx = nx;
                cy = ny;
            }
            if (maxLen < 1) return null;
            int len = 1 + r.nextInt(maxLen);
            for (int i = 0; i < len; i++) {
                x += DX[d];
                y += DY[d];
                vis[y][x] = true;
                path.add(new int[]{x, y});
            }
            if (turnsLeft == 0) break;
            int a = (d + 1) % 4, b = (d + 3) % 4;
            if (r.nextBoolean()) {
                int t = a;
                a = b;
                b = t;
            }
            int nd = -1;
            int[] cand = {a, b};
            for (int k = 0; k < 2; k++) {
                int nx = x + DX[cand[k]], ny = y + DY[cand[k]];
                if (nx >= 0 && ny >= 0 && nx < n && ny < n && !vis[ny][nx]) {
                    nd = cand[k];
                    break;
                }
            }
            if (nd < 0) return null;
            int type = REFL[SLASH][d] == nd ? SLASH : BACK;
            mir[y][x] = type;
            d = nd;
            turnsLeft--;
        }
        if (turnsLeft != 0) return null;

        Level lv = new Level();
        lv.number = number;
        lv.n = n;
        lv.emitRow = er;
        lv.wall = new boolean[n][n];
        lv.target = new boolean[n][n];
        lv.solution = mir;
        int[] end = path.get(path.size() - 1);
        lv.target[end[1]][end[0]] = true;
        lv.targetCount = 1;
        List<int[]> cands = new ArrayList<int[]>();
        for (int i = 1; i < path.size() - 1; i++) {
            int[] c = path.get(i);
            if (mir[c[1]][c[0]] == EMPTY) cands.add(c);
        }
        int extra = Math.min(cands.size(), 1 + number / 12);
        if (extra > 3) extra = 3;
        for (int i = 0; i < extra && !cands.isEmpty(); i++) {
            int[] c = cands.remove(r.nextInt(cands.size()));
            lv.target[c[1]][c[0]] = true;
            lv.targetCount++;
        }
        int walls = n * n / 6;
        int tries = 0;
        while (walls > 0 && tries++ < 200) {
            int wx = r.nextInt(n), wy = r.nextInt(n);
            if (!vis[wy][wx] && !lv.wall[wy][wx]) {
                lv.wall[wy][wx] = true;
                walls--;
            }
        }
        lv.budget = turns;
        int par = 0;
        for (int yy = 0; yy < n; yy++)
            for (int xx = 0; xx < n; xx++) {
                if (mir[yy][xx] == SLASH) par += 1;
                else if (mir[yy][xx] == BACK) par += 2;
            }
        lv.par = par;
        return lv;
    }

    /** Follow the beam from the emitter through the given mirror layout. */
    public Trace trace(int[][] m) {
        Trace t = new Trace();
        t.hit = new boolean[n][n];
        t.pts.add(new float[]{-0.5f, emitRow});
        int x = -1, y = emitRow, d = 0;
        int guard = n * n * 4 + 20;
        boolean ended = false;
        while (guard-- > 0) {
            int nx = x + DX[d], ny = y + DY[d];
            if (nx < 0 || ny < 0 || nx >= n || ny >= n || wall[ny][nx]) {
                t.pts.add(new float[]{x + 0.5f * DX[d], y + 0.5f * DY[d]});
                ended = true;
                break;
            }
            x = nx;
            y = ny;
            if (target[y][x] && !t.hit[y][x]) {
                t.hit[y][x] = true;
                t.hitCount++;
            }
            if (m[y][x] != EMPTY) {
                t.pts.add(new float[]{x, y});
                d = REFL[m[y][x]][d];
            }
        }
        if (!ended) t.pts.add(new float[]{x, y});
        return t;
    }
}
