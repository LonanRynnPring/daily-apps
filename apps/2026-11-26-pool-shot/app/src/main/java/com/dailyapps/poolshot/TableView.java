package com.dailyapps.poolshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** Top-down table drawn upright: table x (0..2) runs down the screen, table y (0..1) runs right to left. */
final class TableView extends View {
    interface Listener {
        void onBallTap(Ball b);

        void onEmptyTap(float x, float y);

        void onBallMoved();
    }

    private static final float CUSHION = 0.09f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final DashPathEffect dash;
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private List<Ball> balls;
    private Bitmap photo;
    private boolean showPhoto = true;
    private float r = 0.0225f;
    private Shots.Shot shot;
    private Listener listener;

    private float s = 100, left = 0, top = 0;
    private Ball dragBall;
    private float downX, downY;
    private boolean moved;

    TableView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{8 * density, 6 * density}, 0);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setData(List<Ball> balls, Bitmap photo, float r) {
        this.balls = balls;
        this.photo = photo;
        this.r = r;
        invalidate();
    }

    void setShowPhoto(boolean b) {
        showPhoto = b;
        invalidate();
    }

    void setShot(Shots.Shot s) {
        shot = s;
        invalidate();
    }

    private void layoutTable() {
        float w = getWidth(), h = getHeight();
        s = Math.min(w / (1 + 2 * CUSHION), h / (2 + 2 * CUSHION));
        left = (w - s) / 2f;
        top = (h - 2 * s) / 2f;
    }

    private float scrX(float tx, float ty) {
        return left + (1 - ty) * s;
    }

    private float scrY(float tx, float ty) {
        return top + tx * s;
    }

    private float tabX(float sxp, float syp) {
        return (syp - top) / s;
    }

    private float tabY(float sxp, float syp) {
        return 1 - (sxp - left) / s;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(16, 20, 18));
        layoutTable();
        float c = CUSHION * s;
        // rails
        rect.set(left - c, top - c, left + s + c, top + 2 * s + c);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(92, 52, 28));
        canvas.drawRoundRect(rect, c * 0.6f, c * 0.6f, paint);
        // felt or photo
        rect.set(left, top, left + s, top + 2 * s);
        paint.setColor(Color.rgb(22, 110, 70));
        canvas.drawRect(rect, paint);
        if (photo != null && showPhoto) {
            float k = s / photo.getHeight();
            matrix.setValues(new float[]{0, -k, left + s, k, 0, top, 0, 0, 1});
            canvas.save();
            canvas.clipRect(rect);
            canvas.drawBitmap(photo, matrix, bmpPaint);
            canvas.restore();
        }
        // diamonds
        paint.setColor(Color.rgb(235, 225, 200));
        for (int i = 1; i < 8; i++) {
            if (i == 4) continue;
            float tx = i * 0.25f;
            canvas.drawCircle(scrX(tx, -CUSHION / 2), scrY(tx, 0), 2 * density, paint);
            canvas.drawCircle(scrX(tx, 1 + CUSHION / 2), scrY(tx, 0), 2 * density, paint);
        }
        for (int i = 1; i < 4; i++) {
            float ty = i * 0.25f;
            canvas.drawCircle(scrX(0, ty), top - c / 2, 2 * density, paint);
            canvas.drawCircle(scrX(0, ty), top + 2 * s + c / 2, 2 * density, paint);
        }
        // pockets
        float pr = r * s * 2.1f;
        for (int p = 0; p < 6; p++) {
            float[] a = Shots.aimPoint(p, r);
            boolean hl = shot != null && shot.pocket == p;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(8, 8, 8));
            canvas.drawCircle(scrX(a[0], a[1]), scrY(a[0], a[1]), pr, paint);
            if (hl) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * density);
                paint.setColor(Color.rgb(255, 196, 64));
                canvas.drawCircle(scrX(a[0], a[1]), scrY(a[0], a[1]), pr + 3 * density, paint);
            }
        }

        if (shot != null) drawShot(canvas);

        if (balls != null) {
            for (Ball b : balls) drawBall(canvas, b);
        }
        if (shot != null) drawGhost(canvas);
    }

    private void drawShot(Canvas canvas) {
        Shots.Shot sh = shot;
        Ball cue = Shots.cue(balls);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // object ball path
        paint.setPathEffect(null);
        paint.setStrokeWidth(3 * density);
        paint.setColor(Color.rgb(255, 196, 64));
        path.reset();
        path.moveTo(scrX(sh.obj.x, sh.obj.y), scrY(sh.obj.x, sh.obj.y));
        if (sh.rail >= 0) path.lineTo(scrX(sh.kx, sh.ky), scrY(sh.kx, sh.ky));
        path.lineTo(scrX(sh.ax, sh.ay), scrY(sh.ax, sh.ay));
        canvas.drawPath(path, paint);
        // cue to ghost
        if (cue != null) {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2.5f * density);
            paint.setPathEffect(dash);
            path.reset();
            path.moveTo(scrX(cue.x, cue.y), scrY(cue.x, cue.y));
            path.lineTo(scrX(sh.gx, sh.gy), scrY(sh.gx, sh.gy));
            canvas.drawPath(path, paint);
        }
        // cue ball afterwards
        float[] cp = sh.cuePath;
        if (cp.length >= 4) {
            paint.setColor(sh.scratch ? Color.rgb(255, 80, 80) : Color.rgb(110, 220, 255));
            paint.setStrokeWidth(2 * density);
            paint.setPathEffect(new DashPathEffect(new float[]{3 * density, 5 * density}, 0));
            path.reset();
            path.moveTo(scrX(cp[0], cp[1]), scrY(cp[0], cp[1]));
            for (int i = 2; i + 1 < cp.length; i += 2) path.lineTo(scrX(cp[i], cp[i + 1]), scrY(cp[i], cp[i + 1]));
            canvas.drawPath(path, paint);
            paint.setPathEffect(null);
            paint.setStrokeWidth(1.5f * density);
            canvas.drawCircle(scrX(sh.restX, sh.restY), scrY(sh.restX, sh.restY), r * s, paint);
        }
        paint.setPathEffect(null);
    }

    private void drawGhost(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(dash);
        paint.setStrokeWidth(2 * density);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(scrX(shot.gx, shot.gy), scrY(shot.gx, shot.gy), r * s, paint);
        paint.setPathEffect(null);
        // contact point
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 196, 64));
        float mx = (shot.gx + shot.obj.x) / 2f, my = (shot.gy + shot.obj.y) / 2f;
        canvas.drawCircle(scrX(mx, my), scrY(mx, my), 2.5f * density, paint);
    }

    private void drawBall(Canvas canvas, Ball b) {
        float x = scrX(b.x, b.y), y = scrY(b.x, b.y);
        float br = r * s;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(90, 0, 0, 0));
        canvas.drawCircle(x + br * 0.15f, y + br * 0.2f, br, paint);
        if (b.isStripe()) {
            paint.setColor(Color.rgb(248, 246, 236));
            canvas.drawCircle(x, y, br, paint);
            canvas.save();
            path.reset();
            path.addCircle(x, y, br, Path.Direction.CW);
            canvas.clipPath(path);
            paint.setColor(Ball.color(b.num));
            canvas.drawRect(x - br, y - br * 0.55f, x + br, y + br * 0.55f, paint);
            canvas.restore();
        } else {
            paint.setColor(Ball.color(b.num));
            canvas.drawCircle(x, y, br, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        boolean target = shot != null && shot.obj == b;
        paint.setColor(target ? Color.rgb(255, 196, 64) : Color.argb(120, 0, 0, 0));
        if (target) paint.setStrokeWidth(2.5f * density);
        canvas.drawCircle(x, y, br, paint);
        if (b.num > 0 && br > 5 * density) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, br * 0.55f, paint);
            text.setColor(Color.BLACK);
            text.setTextSize(br * (b.num > 9 ? 0.6f : 0.75f));
            canvas.drawText(String.valueOf(b.num), x, y + br * 0.26f, text);
        }
    }

    private Ball ballAt(float sxp, float syp) {
        if (balls == null) return null;
        float lim = Math.max(r * s * 1.6f, 20 * density);
        Ball best = null;
        float bd = lim;
        for (Ball b : balls) {
            float dx = scrX(b.x, b.y) - sxp, dy = scrY(b.x, b.y) - syp;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragBall = ballAt(x, y);
                downX = x;
                downY = y;
                moved = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.hypot(x - downX, y - downY) > 10 * density) moved = true;
                if (moved && dragBall != null) {
                    float tx = tabX(x, y), ty = tabY(x, y);
                    dragBall.x = Math.max(r, Math.min(2 - r, tx));
                    dragBall.y = Math.max(r, Math.min(1 - r, ty));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (listener != null) {
                    if (moved) {
                        if (dragBall != null) listener.onBallMoved();
                    } else if (dragBall != null) {
                        listener.onBallTap(dragBall);
                    } else {
                        float tx = tabX(x, y), ty = tabY(x, y);
                        if (tx >= 0 && tx <= 2 && ty >= 0 && ty <= 1) {
                            listener.onEmptyTap(Math.max(r, Math.min(2 - r, tx)), Math.max(r, Math.min(1 - r, ty)));
                        }
                    }
                }
                dragBall = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragBall = null;
                return true;
            default:
                return true;
        }
    }
}
