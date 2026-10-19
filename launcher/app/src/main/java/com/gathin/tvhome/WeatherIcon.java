package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Small weather glyphs drawn on a 24x24 grid: no bitmaps, no icon font, crisp at any size. */
public class WeatherIcon extends View {

    public static final int CLEAR = 0, MOON = 1, PARTLY = 2, CLOUD = 3, FOG = 4, RAIN = 5,
            SNOW = 6, THUNDER = 7, SUNRISE = 8, SUNSET = 9;

    private static final int SUN = 0xFFFFC94D, CLOUD_FILL = 0xFFF2F2F5, CLOUD_DIM = 0xFFB8BCC6,
            DROP = 0xFF6FB6FF, MOON_FILL = 0xFFE6E8F0;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind = CLOUD;

    public WeatherIcon(Context c) { this(c, null); }

    public WeatherIcon(Context c, AttributeSet a) {
        super(c, a);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setKind(int k) {
        if (k != kind) { kind = k; invalidate(); }
    }

    /** WMO weather code (Open-Meteo) to a glyph. */
    public static int forCode(int code, boolean day) {
        if (code <= 1) return day ? CLEAR : MOON;
        if (code == 2) return day ? PARTLY : CLOUD;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return THUNDER;
        return RAIN;   // drizzle, rain, freezing rain, showers
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        line.setStrokeWidth(1.6f);
        switch (kind) {
            case CLEAR: sun(c, 12, 12, 4.6f, true); break;
            case MOON: moon(c); break;
            case PARTLY:
                sun(c, 8.5f, 8.5f, 3.4f, true);
                cloud(c, 1.5f, 3f, CLOUD_FILL);
                break;
            case CLOUD:
                cloud(c, -3.5f, -1f, CLOUD_DIM);
                cloud(c, 0f, 1.5f, CLOUD_FILL);
                break;
            case FOG:
                line.setColor(CLOUD_DIM);
                line.setStrokeWidth(2f);
                c.drawLine(4, 8, 20, 8, line);
                c.drawLine(2.5f, 12, 18, 12, line);
                c.drawLine(5, 16, 21.5f, 16, line);
                c.drawLine(7, 20, 17, 20, line);
                break;
            case RAIN:
                cloud(c, 0, -3f, CLOUD_FILL);
                line.setColor(DROP);
                line.setStrokeWidth(1.8f);
                c.drawLine(8.5f, 17, 7.3f, 20.5f, line);
                c.drawLine(12.5f, 17, 11.3f, 20.5f, line);
                c.drawLine(16.5f, 17, 15.3f, 20.5f, line);
                break;
            case SNOW:
                cloud(c, 0, -3f, CLOUD_FILL);
                fill.setColor(CLOUD_FILL);
                c.drawCircle(8, 18.5f, 1.2f, fill);
                c.drawCircle(12, 20.5f, 1.2f, fill);
                c.drawCircle(16, 18.5f, 1.2f, fill);
                break;
            case THUNDER:
                cloud(c, 0, -3f, CLOUD_DIM);
                Path bolt = new Path();
                bolt.moveTo(13, 13.5f); bolt.lineTo(9.3f, 18.6f); bolt.lineTo(12, 18.6f);
                bolt.lineTo(10.8f, 22.5f); bolt.lineTo(15, 17); bolt.lineTo(12.3f, 17);
                bolt.lineTo(13.8f, 13.5f); bolt.close();
                fill.setColor(SUN);
                c.drawPath(bolt, fill);
                break;
            case SUNRISE:
            case SUNSET:
                horizon(c, kind == SUNRISE);
                break;
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r, boolean rays) {
        fill.setColor(SUN);
        c.drawCircle(cx, cy, r, fill);
        if (!rays) return;
        line.setColor(SUN);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r * 1.5f, cy + sin * r * 1.5f, cx + cos * r * 1.95f, cy + sin * r * 1.95f, line);
        }
    }

    private void moon(Canvas c) {
        Path disc = new Path();
        disc.addCircle(12, 12, 7, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(16, 8.5f, 6, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(MOON_FILL);
        c.drawPath(disc, fill);
    }

    /** A soft three-bump cloud, offset from its home position at the lower middle of the grid. */
    private void cloud(Canvas c, float dx, float dy, int color) {
        Path p = new Path();
        p.addCircle(8 + dx, 14 + dy, 4f, Path.Direction.CW);
        Path q = new Path();
        q.addCircle(13 + dx, 11 + dy, 5.2f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addCircle(17.5f + dx, 14.5f + dy, 3.5f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addRoundRect(new RectF(4 + dx, 13 + dy, 21 + dx, 18 + dy), 2.5f, 2.5f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    /** Half sun on the horizon with an arrow: up for sunrise, down for sunset. */
    private void horizon(Canvas c, boolean up) {
        c.save();
        c.clipRect(0, 0, 24, 17.5f);
        fill.setColor(SUN);
        c.drawCircle(12, 18, 5.5f, fill);
        c.restore();
        line.setColor(CLOUD_FILL);
        line.setStrokeWidth(1.6f);
        c.drawLine(2.5f, 19.5f, 21.5f, 19.5f, line);
        line.setColor(SUN);
        if (up) {
            c.drawLine(12, 2.5f, 12, 9, line);
            c.drawLine(9.3f, 5.2f, 12, 2.5f, line);
            c.drawLine(14.7f, 5.2f, 12, 2.5f, line);
        } else {
            c.drawLine(12, 2.5f, 12, 9, line);
            c.drawLine(9.3f, 6.3f, 12, 9, line);
            c.drawLine(14.7f, 6.3f, 12, 9, line);
        }
    }
}
