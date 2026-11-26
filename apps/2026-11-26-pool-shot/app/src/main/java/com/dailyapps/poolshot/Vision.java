package com.dailyapps.poolshot;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/** Hand-rolled image analysis: felt colour, table corners, perspective warp and ball finding. */
final class Vision {
    private Vision() {}

    static final class Felt {
        float hue, sat, val;
    }

    static void toHsv(int[] px, float[] h, float[] s, float[] v) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            v[i] = max / 255f;
            int d = max - min;
            s[i] = max == 0 ? 0f : d / (float) max;
            float hue;
            if (d == 0) hue = 0f;
            else if (max == r) {
                hue = 60f * ((g - b) / (float) d);
                if (hue < 0) hue += 360f;
            } else if (max == g) hue = 60f * ((b - r) / (float) d) + 120f;
            else hue = 60f * ((r - g) / (float) d) + 240f;
            h[i] = hue;
        }
    }

    static float hueDist(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }

    /** Dominant saturated hue inside a region: that is the cloth. */
    static Felt findFelt(float[] h, float[] s, float[] v, int w, int x0, int y0, int x1, int y1) {
        int[] hist = new int[72];
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int i = y * w + x;
                if (s[i] > 0.2f && v[i] > 0.12f) hist[((int) (h[i] / 5f)) % 72]++;
            }
        }
        int best = 0, bestSum = -1;
        for (int i = 0; i < 72; i++) {
            int sum = hist[(i + 71) % 72] + hist[i] + hist[(i + 1) % 72];
            if (sum > bestSum) {
                bestSum = sum;
                best = i;
            }
        }
        float center = best * 5f + 2.5f;
        double cs = 0, sn = 0, ss = 0, vv = 0;
        int n = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int i = y * w + x;
                if (s[i] > 0.2f && v[i] > 0.12f && hueDist(h[i], center) < 15f) {
                    double a = Math.toRadians(h[i]);
                    cs += Math.cos(a);
                    sn += Math.sin(a);
                    ss += s[i];
                    vv += v[i];
                    n++;
                }
            }
        }
        Felt f = new Felt();
        if (n == 0) {
            f.hue = 120f;
            f.sat = 0.5f;
            f.val = 0.5f;
            return f;
        }
        float hue = (float) Math.toDegrees(Math.atan2(sn, cs));
        if (hue < 0) hue += 360f;
        f.hue = hue;
        f.sat = (float) (ss / n);
        f.val = (float) (vv / n);
        return f;
    }

    static boolean isFelt(Felt f, float h, float s, float v) {
        float hd = hueDist(h, f.hue);
        if (hd < 22f && s > Math.max(0.15f, f.sat * 0.45f) && v > f.val * 0.35f
                && v < Math.min(1f, f.val * 1.9f + 0.1f)) return true;
        return hd < 14f && s > f.sat * 0.6f && v > f.val * 0.18f;
    }

    private static boolean[] erode(boolean[] in, int w, int h) {
        boolean[] out = new boolean[in.length];
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                out[i] = in[i] && in[i - 1] && in[i + 1] && in[i - w] && in[i + w]
                        && in[i - w - 1] && in[i - w + 1] && in[i + w - 1] && in[i + w + 1];
            }
        }
        return out;
    }

    private static boolean[] dilate(boolean[] in, int w, int h) {
        boolean[] out = new boolean[in.length];
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                out[i] = in[i] || in[i - 1] || in[i + 1] || in[i - w] || in[i + w]
                        || in[i - w - 1] || in[i - w + 1] || in[i + w - 1] || in[i + w + 1];
            }
        }
        return out;
    }

    /** Guesses the four playing-surface corners (clockwise, first edge = a long rail) in source pixels. */
    static float[] autoCorners(Bitmap src) {
        int sw0 = src.getWidth(), sh0 = src.getHeight();
        float scale = 480f / Math.max(sw0, sh0);
        if (scale > 1f) scale = 1f;
        int w = Math.max(8, Math.round(sw0 * scale)), h = Math.max(8, Math.round(sh0 * scale));
        float[] fallback = {sw0 * 0.1f, sh0 * 0.2f, sw0 * 0.9f, sh0 * 0.2f, sw0 * 0.9f, sh0 * 0.8f,
                sw0 * 0.1f, sh0 * 0.8f};
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != src) small.recycle();
        float[] hh = new float[px.length], ss = new float[px.length], vv = new float[px.length];
        toHsv(px, hh, ss, vv);
        Felt f = findFelt(hh, ss, vv, w, (int) (w * 0.3f), (int) (h * 0.3f), (int) (w * 0.7f), (int) (h * 0.7f));
        boolean[] m = new boolean[px.length];
        for (int i = 0; i < px.length; i++) m[i] = isFelt(f, hh[i], ss[i], vv[i]);
        m = dilate(dilate(m, w, h), w, h);
        m = erode(erode(m, w, h), w, h);

        // seed: felt pixel closest to the centre
        int seed = -1;
        int cx = w / 2, cy = h / 2;
        for (int rad = 0; rad < Math.max(w, h) / 2 && seed < 0; rad++) {
            for (int dy = -rad; dy <= rad && seed < 0; dy++) {
                for (int dx = -rad; dx <= rad; dx++) {
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h) continue;
                    if (m[y * w + x]) {
                        seed = y * w + x;
                        break;
                    }
                }
            }
        }
        if (seed < 0) return fallback;
        boolean[] seen = new boolean[px.length];
        int[] q = new int[px.length];
        int head = 0, tail = 0;
        q[tail++] = seed;
        seen[seed] = true;
        float minS = Float.MAX_VALUE, maxS = -Float.MAX_VALUE, minD = Float.MAX_VALUE, maxD = -Float.MAX_VALUE;
        int tl = seed, br = seed, tr = seed, bl = seed;
        while (head < tail) {
            int i = q[head++];
            int x = i % w, y = i / w;
            float sum = x + y, dif = x - y;
            if (sum < minS) { minS = sum; tl = i; }
            if (sum > maxS) { maxS = sum; br = i; }
            if (dif > maxD) { maxD = dif; tr = i; }
            if (dif < minD) { minD = dif; bl = i; }
            if (x > 0 && !seen[i - 1] && m[i - 1]) { seen[i - 1] = true; q[tail++] = i - 1; }
            if (x < w - 1 && !seen[i + 1] && m[i + 1]) { seen[i + 1] = true; q[tail++] = i + 1; }
            if (y > 0 && !seen[i - w] && m[i - w]) { seen[i - w] = true; q[tail++] = i - w; }
            if (y < h - 1 && !seen[i + w] && m[i + w]) { seen[i + w] = true; q[tail++] = i + w; }
        }
        if (tail < px.length * 0.05) return fallback;
        int[] idx = {tl, tr, br, bl};
        float[] c = new float[8];
        for (int k = 0; k < 4; k++) {
            c[k * 2] = (idx[k] % w + 0.5f) / scale;
            c[k * 2 + 1] = (idx[k] / w + 0.5f) / scale;
        }
        float horiz = dist(c, 0, 1) + dist(c, 3, 2);
        float vert = dist(c, 1, 2) + dist(c, 0, 3);
        if (vert > horiz) c = rotate(c);
        return c;
    }

    static float dist(float[] c, int a, int b) {
        float dx = c[a * 2] - c[b * 2], dy = c[a * 2 + 1] - c[b * 2 + 1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Shifts the corner order by one so a different edge becomes the first long rail. */
    static float[] rotate(float[] c) {
        float[] o = new float[8];
        for (int k = 0; k < 4; k++) {
            int s = (k + 1) % 4;
            o[k * 2] = c[s * 2];
            o[k * 2 + 1] = c[s * 2 + 1];
        }
        return o;
    }

    /** Solves the homography mapping (x,y) -> (X,Y) for four point pairs. Returns 9 values, h[8] = 1. */
    static double[] homography(float[] from, float[] to) {
        double[][] a = new double[8][9];
        for (int k = 0; k < 4; k++) {
            double x = from[k * 2], y = from[k * 2 + 1], X = to[k * 2], Y = to[k * 2 + 1];
            a[k * 2] = new double[]{x, y, 1, 0, 0, 0, -x * X, -y * X, X};
            a[k * 2 + 1] = new double[]{0, 0, 0, x, y, 1, -x * Y, -y * Y, Y};
        }
        for (int col = 0; col < 8; col++) {
            int piv = col;
            for (int r = col + 1; r < 8; r++) if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            double[] tmp = a[col];
            a[col] = a[piv];
            a[piv] = tmp;
            double d = a[col][col];
            if (Math.abs(d) < 1e-12) d = 1e-12;
            for (int j = col; j < 9; j++) a[col][j] /= d;
            for (int r = 0; r < 8; r++) {
                if (r == col) continue;
                double fct = a[r][col];
                if (fct == 0) continue;
                for (int j = col; j < 9; j++) a[r][j] -= fct * a[col][j];
            }
        }
        double[] hm = new double[9];
        for (int i = 0; i < 8; i++) hm[i] = a[i][8];
        hm[8] = 1;
        return hm;
    }

    /** Warps the quadrilateral into a top-down outW x outH image (bilinear sampling). */
    static Bitmap warp(Bitmap src, float[] corners, int outW, int outH) {
        int sw = src.getWidth(), sh = src.getHeight();
        int[] sp = new int[sw * sh];
        src.getPixels(sp, 0, sw, 0, 0, sw, sh);
        float[] dst = {0, 0, outW, 0, outW, outH, 0, outH};
        double[] H = homography(dst, corners);
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            double Y = y + 0.5;
            for (int x = 0; x < outW; x++) {
                double X = x + 0.5;
                double den = H[6] * X + H[7] * Y + 1.0;
                if (Math.abs(den) < 1e-9) den = 1e-9;
                double fx = (H[0] * X + H[1] * Y + H[2]) / den - 0.5;
                double fy = (H[3] * X + H[4] * Y + H[5]) / den - 0.5;
                if (fx < 0) fx = 0;
                if (fy < 0) fy = 0;
                if (fx > sw - 1.001) fx = sw - 1.001;
                if (fy > sh - 1.001) fy = sh - 1.001;
                int ix = (int) fx, iy = (int) fy;
                float ax = (float) (fx - ix), ay = (float) (fy - iy);
                int p00 = sp[iy * sw + ix], p10 = sp[iy * sw + ix + 1];
                int p01 = sp[(iy + 1) * sw + ix], p11 = sp[(iy + 1) * sw + ix + 1];
                int r = lerp2((p00 >> 16) & 255, (p10 >> 16) & 255, (p01 >> 16) & 255, (p11 >> 16) & 255, ax, ay);
                int g = lerp2((p00 >> 8) & 255, (p10 >> 8) & 255, (p01 >> 8) & 255, (p11 >> 8) & 255, ax, ay);
                int b = lerp2(p00 & 255, p10 & 255, p01 & 255, p11 & 255, ax, ay);
                out[y * outW + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888);
    }

    private static int lerp2(int a, int b, int c, int d, float ax, float ay) {
        float top = a + (b - a) * ax;
        float bot = c + (d - c) * ax;
        int v = Math.round(top + (bot - top) * ay);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Finds balls in a rectified table image (width = 2 x height). rUnits = ball radius in table widths. */
    static List<Ball> detectBalls(Bitmap rect, float rUnits) {
        int W = rect.getWidth(), H = rect.getHeight();
        float ppu = H;
        float rpx = rUnits * ppu;
        int[] px = new int[W * H];
        rect.getPixels(px, 0, W, 0, 0, W, H);
        float[] hh = new float[px.length], ss = new float[px.length], vv = new float[px.length];
        toHsv(px, hh, ss, vv);
        Felt f = findFelt(hh, ss, vv, W, (int) (W * 0.05f), (int) (H * 0.05f), (int) (W * 0.95f), (int) (H * 0.95f));
        boolean[] non = new boolean[px.length];
        int border = Math.max(3, Math.round(rpx * 0.25f));
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (x < border || y < border || x >= W - border || y >= H - border) continue;
                non[i] = !isFelt(f, hh[i], ss[i], vv[i]);
            }
        }
        int k = Math.max(1, Math.round(rpx * 0.12f));
        for (int i = 0; i < k; i++) non = erode(non, W, H);
        for (int i = 0; i < k; i++) non = dilate(non, W, H);

        float expected = (float) (Math.PI * rpx * rpx);
        List<float[]> centers = new ArrayList<>();
        boolean[] seen = new boolean[px.length];
        int[] q = new int[px.length];
        for (int start = 0; start < px.length; start++) {
            if (!non[start] || seen[start]) continue;
            int head = 0, tail = 0;
            q[tail++] = start;
            seen[start] = true;
            int minX = W, minY = H, maxX = 0, maxY = 0;
            double sx = 0, sy = 0;
            while (head < tail) {
                int i = q[head++];
                int x = i % W, y = i / W;
                sx += x;
                sy += y;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (x > 0 && non[i - 1] && !seen[i - 1]) { seen[i - 1] = true; q[tail++] = i - 1; }
                if (x < W - 1 && non[i + 1] && !seen[i + 1]) { seen[i + 1] = true; q[tail++] = i + 1; }
                if (y > 0 && non[i - W] && !seen[i - W]) { seen[i - W] = true; q[tail++] = i - W; }
                if (y < H - 1 && non[i + W] && !seen[i + W]) { seen[i + W] = true; q[tail++] = i + W; }
            }
            int area = tail;
            float ratio = area / expected;
            if (ratio < 0.35f || ratio > 8f) continue;
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            if (area / (float) Math.max(bw, bh) < rpx * 0.9f) continue; // thin: cue stick, line, edge
            int n = Math.max(1, Math.round(ratio / 1.2f));
            if (n == 1) {
                float fill = area / (float) (bw * bh);
                float aspect = Math.max(bw, bh) / (float) Math.min(bw, bh);
                if (fill < 0.35f || aspect > 2.2f) continue;
                centers.add(new float[]{(float) (sx / area), (float) (sy / area)});
            } else {
                kmeans(q, tail, W, n, expected, centers);
            }
        }

        // merge near duplicates
        List<float[]> merged = new ArrayList<>();
        for (float[] c : centers) {
            boolean done = false;
            for (float[] m : merged) {
                float dx = m[0] - c[0], dy = m[1] - c[1];
                if (dx * dx + dy * dy < (1.3f * rpx) * (1.3f * rpx)) {
                    m[0] = (m[0] + c[0]) / 2f;
                    m[1] = (m[1] + c[1]) / 2f;
                    done = true;
                    break;
                }
            }
            if (!done) merged.add(new float[]{c[0], c[1]});
        }

        // classify by colour
        int nB = merged.size();
        float[] whiteF = new float[nB], darkF = new float[nB], colF = new float[nB], hueA = new float[nB], valA = new float[nB];
        float sr = rpx * 0.72f;
        for (int b = 0; b < nB; b++) {
            float cx = merged.get(b)[0], cy = merged.get(b)[1];
            int tot = 0, wh = 0, dk = 0, co = 0;
            double hc = 0, hs = 0, vs = 0;
            for (int y = (int) (cy - sr); y <= (int) (cy + sr); y++) {
                for (int x = (int) (cx - sr); x <= (int) (cx + sr); x++) {
                    if (x < 0 || y < 0 || x >= W || y >= H) continue;
                    float dx = x - cx, dy = y - cy;
                    if (dx * dx + dy * dy > sr * sr) continue;
                    int i = y * W + x;
                    tot++;
                    if (ss[i] < 0.28f && vv[i] > 0.62f) wh++;
                    else if (vv[i] < 0.22f) dk++;
                    else if (ss[i] >= 0.35f) {
                        co++;
                        double a = Math.toRadians(hh[i]);
                        hc += Math.cos(a);
                        hs += Math.sin(a);
                        vs += vv[i];
                    }
                }
            }
            if (tot == 0) tot = 1;
            whiteF[b] = wh / (float) tot;
            darkF[b] = dk / (float) tot;
            colF[b] = co / (float) tot;
            float hue = (float) Math.toDegrees(Math.atan2(hs, hc));
            if (hue < 0) hue += 360f;
            hueA[b] = hue;
            valA[b] = co > 0 ? (float) (vs / co) : 0.5f;
        }
        int cue = -1, eight = -1;
        for (int b = 0; b < nB; b++) {
            if (whiteF[b] > 0.5f && (cue < 0 || whiteF[b] > whiteF[cue])) cue = b;
        }
        for (int b = 0; b < nB; b++) {
            if (b == cue) continue;
            if (darkF[b] > 0.45f && (eight < 0 || darkF[b] > darkF[eight])) eight = b;
        }
        List<Ball> out = new ArrayList<>();
        for (int b = 0; b < nB; b++) {
            int num;
            if (b == cue) num = 0;
            else if (b == eight) num = 8;
            else {
                int base = baseFromHue(hueA[b], valA[b], colF[b]);
                boolean stripe = whiteF[b] > 0.2f;
                num = stripe ? base + 8 : base;
            }
            float x = merged.get(b)[0] / ppu, y = merged.get(b)[1] / ppu;
            x = Math.max(rUnits, Math.min(2f - rUnits, x));
            y = Math.max(rUnits, Math.min(1f - rUnits, y));
            out.add(new Ball(num, x, y));
        }
        return out;
    }

    private static int baseFromHue(float hue, float val, float colF) {
        if (colF < 0.05f) return 1;
        if (hue < 15f || hue >= 340f) return val < 0.45f ? 7 : 3;
        if (hue < 38f) return val < 0.45f ? 7 : 5;
        if (hue < 70f) return 1;
        if (hue < 170f) return 6;
        if (hue < 255f) return 2;
        return 4;
    }

    private static void kmeans(int[] pix, int count, int W, int n, float expected, List<float[]> out) {
        int stride = Math.max(1, count / 3000);
        int m = (count + stride - 1) / stride;
        float[] xs = new float[m], ys = new float[m];
        double mx = 0, my = 0;
        for (int j = 0; j < m; j++) {
            int i = pix[j * stride];
            xs[j] = i % W;
            ys[j] = i / W;
            mx += xs[j];
            my += ys[j];
        }
        mx /= m;
        my /= m;
        float[] cx = new float[n], cy = new float[n];
        // farthest-point initialisation
        int far = 0;
        double fd = -1;
        for (int j = 0; j < m; j++) {
            double d = (xs[j] - mx) * (xs[j] - mx) + (ys[j] - my) * (ys[j] - my);
            if (d > fd) { fd = d; far = j; }
        }
        cx[0] = xs[far];
        cy[0] = ys[far];
        for (int c = 1; c < n; c++) {
            far = 0;
            fd = -1;
            for (int j = 0; j < m; j++) {
                double best = Double.MAX_VALUE;
                for (int e = 0; e < c; e++) {
                    double d = (xs[j] - cx[e]) * (xs[j] - cx[e]) + (ys[j] - cy[e]) * (ys[j] - cy[e]);
                    if (d < best) best = d;
                }
                if (best > fd) { fd = best; far = j; }
            }
            cx[c] = xs[far];
            cy[c] = ys[far];
        }
        int[] asg = new int[m];
        int[] cnt = new int[n];
        for (int it = 0; it < 12; it++) {
            double[] sx = new double[n], sy = new double[n];
            for (int c = 0; c < n; c++) cnt[c] = 0;
            for (int j = 0; j < m; j++) {
                int bc = 0;
                double bd = Double.MAX_VALUE;
                for (int c = 0; c < n; c++) {
                    double d = (xs[j] - cx[c]) * (xs[j] - cx[c]) + (ys[j] - cy[c]) * (ys[j] - cy[c]);
                    if (d < bd) { bd = d; bc = c; }
                }
                asg[j] = bc;
                sx[bc] += xs[j];
                sy[bc] += ys[j];
                cnt[bc]++;
            }
            for (int c = 0; c < n; c++) {
                if (cnt[c] > 0) {
                    cx[c] = (float) (sx[c] / cnt[c]);
                    cy[c] = (float) (sy[c] / cnt[c]);
                }
            }
        }
        float minPts = expected * 0.3f / stride;
        for (int c = 0; c < n; c++) {
            if (cnt[c] >= minPts) out.add(new float[]{cx[c], cy[c]});
        }
    }
}
