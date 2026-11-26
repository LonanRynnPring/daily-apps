package com.dailyapps.poolshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** Shows the photo with four draggable corner handles and a magnifier for precise placement. */
final class CornerView extends View {
    private final Bitmap photo;
    private float[] corners; // in photo pixels
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final float density;
    private float scale = 1, offX = 0, offY = 0;
    private int drag = -1;
    private float lastX, lastY;

    CornerView(Context c, Bitmap photo, float[] corners) {
        super(c);
        this.photo = photo;
        this.corners = corners.clone();
        density = c.getResources().getDisplayMetrics().density;
    }

    float[] getCorners() {
        return corners.clone();
    }

    void setCorners(float[] c) {
        corners = c.clone();
        invalidate();
    }

    private void layoutPhoto() {
        float pad = 18 * density;
        float w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
        scale = Math.min(w / photo.getWidth(), h / photo.getHeight());
        offX = (getWidth() - photo.getWidth() * scale) / 2f;
        offY = (getHeight() - photo.getHeight() * scale) / 2f;
    }

    private float sx(float x) {
        return offX + x * scale;
    }

    private float sy(float y) {
        return offY + y * scale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(12, 14, 12));
        layoutPhoto();
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(offX, offY);
        canvas.drawBitmap(photo, matrix, bmpPaint);

        path.reset();
        path.moveTo(sx(corners[0]), sy(corners[1]));
        for (int k = 1; k < 4; k++) path.lineTo(sx(corners[k * 2]), sy(corners[k * 2 + 1]));
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(50, 255, 210, 80));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            boolean longRail = k == 0 || k == 2;
            paint.setColor(longRail ? Color.rgb(255, 196, 64) : Color.rgb(120, 220, 255));
            paint.setStrokeWidth((longRail ? 3.5f : 2f) * density);
            float x1 = sx(corners[k * 2]), y1 = sy(corners[k * 2 + 1]);
            float x2 = sx(corners[n * 2]), y2 = sy(corners[n * 2 + 1]);
            canvas.drawLine(x1, y1, x2, y2, paint);
            if (longRail) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle((x1 + x2) / 2f, (y1 + y2) / 2f, 5 * density, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        for (int k = 0; k < 4; k++) {
            float x = sx(corners[k * 2]), y = sy(corners[k * 2 + 1]);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(k == drag ? Color.argb(200, 255, 196, 64) : Color.argb(140, 0, 0, 0));
            canvas.drawCircle(x, y, 16 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * density);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 16 * density, paint);
            canvas.drawLine(x - 6 * density, y, x + 6 * density, y, paint);
            canvas.drawLine(x, y - 6 * density, x, y + 6 * density, paint);
        }

        if (drag >= 0) drawLoupe(canvas);
    }

    private void drawLoupe(Canvas canvas) {
        float px = corners[drag * 2], py = corners[drag * 2 + 1];
        float rad = 62 * density;
        float lx = sx(px) < getWidth() / 2f ? getWidth() - rad - 12 * density : rad + 12 * density;
        float ly = rad + 12 * density;
        float zoom = scale * 3f;
        canvas.save();
        path.reset();
        path.addCircle(lx, ly, rad, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawColor(Color.BLACK);
        matrix.reset();
        matrix.postScale(zoom, zoom);
        matrix.postTranslate(lx - px * zoom, ly - py * zoom);
        canvas.drawBitmap(photo, matrix, bmpPaint);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f * density);
        canvas.drawCircle(lx, ly, rad, paint);
        paint.setColor(Color.rgb(255, 196, 64));
        canvas.drawLine(lx - 14 * density, ly, lx + 14 * density, ly, paint);
        canvas.drawLine(lx, ly - 14 * density, lx, ly + 14 * density, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                drag = -1;
                float best = 48 * density;
                for (int k = 0; k < 4; k++) {
                    float dx = sx(corners[k * 2]) - x, dy = sy(corners[k * 2 + 1]) - y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < best) {
                        best = d;
                        drag = k;
                    }
                }
                lastX = x;
                lastY = y;
                if (drag >= 0) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (drag >= 0) {
                    // relative drag so the finger does not hide the point
                    float nx = corners[drag * 2] + (x - lastX) / scale;
                    float ny = corners[drag * 2 + 1] + (y - lastY) / scale;
                    corners[drag * 2] = Math.max(0, Math.min(photo.getWidth() - 1, nx));
                    corners[drag * 2 + 1] = Math.max(0, Math.min(photo.getHeight() - 1, ny));
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drag = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
