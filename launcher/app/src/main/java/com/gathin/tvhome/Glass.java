package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * "Liquid glass" without live blur (too heavy for a 1.7 GB TV on Android 11): a translucent fill
 * with a vertical sheen, outlined by a rim that is bright along the top edge and fades towards the
 * bottom, the way light catches the edge of a glass pane.
 */
final class Glass extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius, stroke;
    private final int fillTop, fillBottom, rimTop, rimBottom;

    private Glass(float radius, float density, int fillTop, int fillBottom, int rimTop, int rimBottom) {
        this.radius = radius;
        this.stroke = Math.max(1f, density * 1.2f);
        this.fillTop = fillTop; this.fillBottom = fillBottom; this.rimTop = rimTop; this.rimBottom = rimBottom;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
    }

    /** Standard panel: buttons, weather. */
    static Drawable panel(float radiusPx, float density) {
        return make(radiusPx, density, 0x2BFFFFFF, 0x0FFFFFFF, 0x99FFFFFF, 0x14FFFFFF);
    }

    /** Darker, more opaque glass for sheets that sit over busy content (drawer, menus). */
    static Drawable sheet(float radiusPx, float density) {
        return make(radiusPx, density, 0xE6262629, 0xF014141A, 0x66FFFFFF, 0x0FFFFFFF);
    }

    static Drawable make(float radiusPx, float density, int fillTop, int fillBottom, int rimTop, int rimBottom) {
        return new Glass(radiusPx, density, fillTop, fillBottom, rimTop, rimBottom);
    }

    /** A top-bar button: glass at rest, solid white when focused (tvOS buttons). */
    static Drawable button(float radiusPx, float density) {
        GradientDrawable white = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFE9E9EE});
        white.setCornerRadius(radiusPx);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, white);
        s.addState(new int[]{}, panel(radiusPx, density));
        return s;
    }

    /** The weather panel: glass that brightens, with the rim lit all round, when focused. */
    static Drawable focusPanel(float radiusPx, float density) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},
                make(radiusPx, density, 0x40FFFFFF, 0x1FFFFFFF, 0xFFFFFFFF, 0xCCFFFFFF));
        s.addState(new int[]{}, panel(radiusPx, density));
        return s;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, fillTop, fillBottom, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, b.top, 0, b.bottom, rimTop, rimBottom, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float h = stroke / 2f;
        r.set(b.left + h, b.top + h, b.right - h, b.bottom - h);
        c.drawRoundRect(r, radius, radius, fill);
        c.drawRoundRect(r, radius, radius, rim);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); rim.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); rim.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
