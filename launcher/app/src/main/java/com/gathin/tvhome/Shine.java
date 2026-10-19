package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The light on a focused tile: a soft gloss over the top, and a diagonal sheen that sweeps across
 * once when the tile is selected (progress 0..1). Drawn as the tile's foreground, clipped to its corners.
 */
final class Shine extends Drawable {

    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private LinearGradient band;
    private float progress = 1f;

    void setProgress(float p) {
        progress = p;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect b) {
        gloss.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        float w = b.width() * 0.45f;
        band = new LinearGradient(0, 0, w, 0, new int[]{0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        sweep.setShader(band);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, gloss);
        if (progress >= 1f || band == null) return;
        float w = b.width() * 0.45f;
        float x = b.left - w + (b.width() + w * 2) * progress;
        m.setRotate(20f);
        m.postTranslate(x, 0);
        band.setLocalMatrix(m);
        c.drawRect(b, sweep);
    }

    @Override public void setAlpha(int alpha) { gloss.setAlpha(alpha); sweep.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
