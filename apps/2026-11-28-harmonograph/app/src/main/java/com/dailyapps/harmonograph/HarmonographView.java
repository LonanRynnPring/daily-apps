package com.dailyapps.harmonograph;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Animated pen that traces the harmonograph onto an offscreen bitmap. */
public class HarmonographView extends View {
    private Params params = new Params();
    private float[] pts = new float[4];
    private int drawn = 0;
    private Bitmap bmp;
    private Canvas bc;
    private final Paint paint = Renderer.makePaint();
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint();
    private final float[] buf = Renderer.newBuffer();
    private boolean paused = false;
    private int speed = 3; // 0 = instant, 1..5
    private final float density;

    public HarmonographView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        text.setTextSize(12 * density);
        pen.setStyle(Paint.Style.FILL);
    }

    public void setSpeed(int s) {
        speed = s;
        invalidate();
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isFinished() {
        return drawn >= pts.length / 2 - 1;
    }

    /** Animate a fresh drawing of the given params. */
    public void animateParams(Params p) {
        params = p.copy();
        paused = false;
        restart(speed == 0);
    }

    /** Show the given params fully drawn at once (used while editing). */
    public void showParams(Params p) {
        params = p.copy();
        restart(true);
    }

    private void restart(boolean instant) {
        pts = Renderer.compute(params, 1);
        drawn = 0;
        if (bc != null) {
            bc.drawColor(Palettes.BG[params.palette]);
            paint.setStrokeWidth(Renderer.strokeFor(params, bmp.getWidth(), bmp.getHeight()));
            if (instant) {
                drawTo(pts.length / 2 - 1);
            }
        }
        invalidate();
    }

    private void drawTo(int target) {
        if (bc == null) return;
        int w = bmp.getWidth(), h = bmp.getHeight();
        float scale = Math.min(w, h) * 0.45f;
        Renderer.drawSegments(bc, pts, drawn, target, params, w / 2f, h / 2f, scale, paint, buf);
        drawn = Math.max(drawn, Math.min(target, pts.length / 2 - 1));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bc = new Canvas(bmp);
        boolean wasDone = isFinished();
        restart(speed == 0 || wasDone);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bmp == null) return;
        int total = pts.length / 2 - 1;
        if (!paused && drawn < total) {
            int step = speed <= 0 ? total : speed * speed * 12 + 8;
            drawTo(Math.min(total, drawn + step));
            if (drawn < total) postInvalidateOnAnimation();
        }
        canvas.drawBitmap(bmp, 0, 0, null);

        int w = getWidth(), h = getHeight();
        boolean light = Palettes.isLight(params.palette);
        if (drawn < total && drawn > 0) {
            float scale = Math.min(w, h) * 0.45f;
            float px = w / 2f + pts[2 * drawn] * scale;
            float py = h / 2f - pts[2 * drawn + 1] * scale;
            int col = Palettes.colorAt(params.palette, drawn / (float) Math.max(1, total));
            pen.setColor(col);
            pen.setAlpha(70);
            canvas.drawCircle(px, py, 10 * density, pen);
            pen.setAlpha(255);
            canvas.drawCircle(px, py, 3.5f * density, pen);
        }
        // progress bar
        bar.setColor(light ? 0x33000000 : 0x33FFFFFF);
        canvas.drawRect(0, h - 3 * density, w, h, bar);
        bar.setColor(Palettes.colorAt(params.palette, 0.5f));
        canvas.drawRect(0, h - 3 * density, w * (drawn / (float) Math.max(1, total)), h, bar);

        text.setColor(light ? 0x99000000 : 0x99FFFFFF);
        canvas.drawText(params.ratioText(), 10 * density, h - 10 * density, text);
        if (paused && drawn < total) {
            canvas.drawText("Paused - tap to resume", 10 * density, 20 * density, text);
        } else if (drawn >= total) {
            canvas.drawText("Tap to replay", 10 * density, 20 * density, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (isFinished()) {
                paused = false;
                restart(speed == 0);
            } else {
                paused = !paused;
                invalidate();
            }
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
