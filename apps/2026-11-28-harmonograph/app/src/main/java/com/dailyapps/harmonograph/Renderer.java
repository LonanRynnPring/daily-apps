package com.dailyapps.harmonograph;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Pure maths and drawing of harmonograph curves. */
public final class Renderer {
    private Renderer() {}

    public static final float DT = 0.01f;
    private static final int CHUNK = 48;

    /** Returns interleaved x,y points normalised to roughly [-1,1]. */
    public static float[] compute(Params p, int stepDiv) {
        float dt = DT * Math.max(1, stepDiv);
        int n = (int) (p.length / dt) + 1;
        float[] out = new float[n * 2];
        double[] ph = new double[Params.N];
        for (int i = 0; i < Params.N; i++) ph[i] = Math.toRadians(p.p[i]);
        float extX = p.a[Params.X1] + p.a[Params.X2] + p.a[Params.ROT];
        float extY = p.a[Params.Y1] + p.a[Params.Y2] + p.a[Params.ROT];
        float ext = Math.max(extX, extY);
        if (ext < 1e-4f) ext = 1f;
        for (int k = 0; k < n; k++) {
            double t = k * dt;
            double x = p.a[Params.X1] * Math.sin(p.f[Params.X1] * t + ph[Params.X1]) * Math.exp(-p.d[Params.X1] * t)
                    + p.a[Params.X2] * Math.sin(p.f[Params.X2] * t + ph[Params.X2]) * Math.exp(-p.d[Params.X2] * t);
            double y = p.a[Params.Y1] * Math.sin(p.f[Params.Y1] * t + ph[Params.Y1]) * Math.exp(-p.d[Params.Y1] * t)
                    + p.a[Params.Y2] * Math.sin(p.f[Params.Y2] * t + ph[Params.Y2]) * Math.exp(-p.d[Params.Y2] * t);
            if (p.a[Params.ROT] > 0) {
                double e = p.a[Params.ROT] * Math.exp(-p.d[Params.ROT] * t);
                double ang = p.f[Params.ROT] * t + ph[Params.ROT];
                x += e * Math.sin(ang);
                y += e * Math.cos(ang);
            }
            out[2 * k] = (float) (x / ext);
            out[2 * k + 1] = (float) (y / ext);
        }
        return out;
    }

    public static Paint makePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        return paint;
    }

    public static float strokeFor(Params p, int w, int h) {
        return Math.max(0.6f, p.width * Math.min(w, h) / 700f);
    }

    /**
     * Draw segments [from, to) (segment k joins point k and k+1) onto the canvas.
     */
    public static void drawSegments(Canvas c, float[] pts, int from, int to, Params p,
                                    float cx, float cy, float scale, Paint paint, float[] buf) {
        int n = pts.length / 2;
        if (to > n - 1) to = n - 1;
        int seg = from;
        while (seg < to) {
            int end = Math.min(to, seg + CHUNK);
            float frac = (seg + (end - seg) * 0.5f) / Math.max(1, n - 1);
            int col = Palettes.colorAt(p.palette, frac);
            paint.setColor(col);
            paint.setAlpha(Palettes.isLight(p.palette) ? 210 : 200);
            int bi = 0;
            for (int k = seg; k < end; k++) {
                buf[bi++] = cx + pts[2 * k] * scale;
                buf[bi++] = cy - pts[2 * k + 1] * scale;
                buf[bi++] = cx + pts[2 * k + 2] * scale;
                buf[bi++] = cy - pts[2 * k + 3] * scale;
            }
            c.drawLines(buf, 0, bi, paint);
            seg = end;
        }
    }

    public static float[] newBuffer() {
        return new float[CHUNK * 4];
    }

    /** Render a whole drawing into a new square bitmap. */
    public static Bitmap renderBitmap(Params p, int size, int stepDiv) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Palettes.BG[p.palette]);
        float[] pts = compute(p, stepDiv);
        Paint paint = makePaint();
        paint.setStrokeWidth(strokeFor(p, size, size));
        float scale = size * 0.45f;
        drawSegments(c, pts, 0, pts.length / 2 - 1, p, size / 2f, size / 2f, scale, paint, newBuffer());
        return bmp;
    }
}
