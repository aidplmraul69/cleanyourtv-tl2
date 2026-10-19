package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Apple Weather's range capsule: a dim track spanning the scale, the day's low-to-high span filled
 * with a gradient (temperature colours, or the UV scale), and an optional white dot for "now".
 */
public class RangeBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float scaleMin, scaleMax, lo, hi, now = Float.NaN;
    private boolean uv;

    public RangeBar(Context c) {
        super(c);
        track.setColor(0x33000000);
        dot.setColor(0xFFFFFFFF);
        dotRing.setColor(0x66000000);
    }

    /** Temperature span lo..hi on the scale min..max; now = NaN for no dot. */
    public void setTemps(float min, float max, float lo, float hi, float now) {
        this.scaleMin = min; this.scaleMax = Math.max(max, min + 1); this.lo = lo; this.hi = hi; this.now = now;
        uv = false;
        invalidate();
    }

    /** Whole UV scale 0..11+ in green-to-violet, dot at the value. */
    public void setUv(float value) {
        scaleMin = 0; scaleMax = 11; lo = 0; hi = 11; now = Math.min(value, 11);
        uv = true;
        invalidate();
    }

    static int tempColor(float t) {
        float[] stops = {-5, 5, 12, 18, 24, 30};
        int[] cols = {0xFF4F9BF7, 0xFF58C7E3, 0xFF7BD87B, 0xFFF0D44A, 0xFFF4A23F, 0xFFEE6447};
        if (t <= stops[0]) return cols[0];
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i]) return blend(cols[i - 1], cols[i], (t - stops[i - 1]) / (stops[i] - stops[i - 1]));
        }
        return cols[cols.length - 1];
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth(), rad = h / 2f;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, track);
        float span = scaleMax - scaleMin;
        float x0 = (lo - scaleMin) / span * w, x1 = (hi - scaleMin) / span * w;
        x1 = Math.max(x1, x0 + h);
        if (uv) {
            fill.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{0xFF5BD06B, 0xFFF2D14A, 0xFFF39A3C, 0xFFE5534B, 0xFFB565D9},
                    null, Shader.TileMode.CLAMP));
        } else {
            fill.setShader(new LinearGradient(x0, 0, x1, 0, tempColor(lo), tempColor(hi), Shader.TileMode.CLAMP));
        }
        r.set(x0, 0, x1, h);
        c.drawRoundRect(r, rad, rad, fill);
        if (!Float.isNaN(now)) {
            float x = Math.max(rad, Math.min(w - rad, (now - scaleMin) / span * w));
            c.drawCircle(x, h / 2f, rad + 1.5f * getResources().getDisplayMetrics().density, dotRing);
            c.drawCircle(x, h / 2f, rad + 0.5f * getResources().getDisplayMetrics().density, dot);
        }
    }
}
