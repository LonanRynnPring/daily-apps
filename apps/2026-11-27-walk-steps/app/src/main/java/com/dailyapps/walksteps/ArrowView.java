package com.dailyapps.walksteps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Draws a turn arrow for a step kind. */
class ArrowView extends View {
    private String kind = Parser.OTHER;
    private int color = 0xFFFFFFFF;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    ArrowView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(String kind, int color) {
        this.kind = kind == null ? Parser.OTHER : kind;
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        stroke.setColor(color);
        fill.setColor(color);
        stroke.setStrokeWidth(s * 0.11f);
        float bottom = cy + s * 0.38f;
        float mid = cy + s * 0.05f;
        float len = s * 0.34f;
        switch (kind) {
            case Parser.LEFT: bent(c, cx + s * 0.1f, bottom, mid, -90, len); break;
            case Parser.RIGHT: bent(c, cx - s * 0.1f, bottom, mid, 90, len); break;
            case Parser.SLIGHT_LEFT: bent(c, cx + s * 0.08f, bottom, mid, -45, len); break;
            case Parser.SLIGHT_RIGHT: bent(c, cx - s * 0.08f, bottom, mid, 45, len); break;
            case Parser.SHARP_LEFT: bent(c, cx + s * 0.12f, bottom, cy - s * 0.2f, -135, len * 0.9f); break;
            case Parser.SHARP_RIGHT: bent(c, cx - s * 0.12f, bottom, cy - s * 0.2f, 135, len * 0.9f); break;
            case Parser.STRAIGHT: bent(c, cx, bottom, cy, 0, s * 0.3f); break;
            case Parser.START: {
                c.drawCircle(cx, bottom - s * 0.02f, s * 0.08f, fill);
                bent(c, cx, bottom - s * 0.1f, cy, 0, s * 0.28f);
                break;
            }
            case Parser.UTURN: {
                float r = s * 0.17f;
                path.reset();
                path.moveTo(cx + r, bottom);
                path.lineTo(cx + r, cy - s * 0.08f);
                oval.set(cx - r, cy - s * 0.08f - r, cx + r, cy - s * 0.08f + r);
                path.arcTo(oval, 0, -180, false);
                path.lineTo(cx - r, cy + s * 0.12f);
                c.drawPath(path, stroke);
                head(c, cx - r, cy + s * 0.12f, 180, s);
                break;
            }
            case Parser.ROUNDABOUT: {
                float r = s * 0.2f;
                stroke.setStrokeWidth(s * 0.08f);
                c.drawCircle(cx, cy, r, stroke);
                stroke.setStrokeWidth(s * 0.11f);
                c.drawLine(cx, cy + r, cx, bottom + s * 0.06f, stroke);
                float ex = cx + r * 0.71f, ey = cy - r * 0.71f;
                float tx = ex + s * 0.2f, ty = ey - s * 0.2f;
                c.drawLine(ex, ey, tx, ty, stroke);
                head(c, tx, ty, 45, s);
                break;
            }
            case Parser.ARRIVE: {
                float px = cx - s * 0.16f;
                c.drawLine(px, cy - s * 0.38f, px, bottom, stroke);
                path.reset();
                path.moveTo(px, cy - s * 0.38f);
                path.lineTo(cx + s * 0.3f, cy - s * 0.24f);
                path.lineTo(px, cy - s * 0.08f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            default: {
                c.drawCircle(cx, cy, s * 0.14f, fill);
                break;
            }
        }
    }

    /** Stem from (x,bottom) up to (x,mid), then a segment at angle (degrees, 0 up, + right). */
    private void bent(Canvas c, float x, float bottom, float mid, float angle, float len) {
        double a = Math.toRadians(angle);
        float ex = (float) (x + Math.sin(a) * len);
        float ey = (float) (mid - Math.cos(a) * len);
        path.reset();
        path.moveTo(x, bottom);
        path.lineTo(x, mid);
        path.lineTo(ex, ey);
        c.drawPath(path, stroke);
        head(c, ex, ey, angle, Math.min(getWidth(), getHeight()));
    }

    private void head(Canvas c, float x, float y, float angle, float s) {
        double a = Math.toRadians(angle);
        float size = s * 0.2f;
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        float px = -dy, py = dx;
        path.reset();
        path.moveTo(x + dx * size * 0.9f, y + dy * size * 0.9f);
        path.lineTo(x + px * size * 0.75f - dx * size * 0.1f, y + py * size * 0.75f - dy * size * 0.1f);
        path.lineTo(x - px * size * 0.75f - dx * size * 0.1f, y - py * size * 0.75f - dy * size * 0.1f);
        path.close();
        c.drawPath(path, fill);
    }
}
