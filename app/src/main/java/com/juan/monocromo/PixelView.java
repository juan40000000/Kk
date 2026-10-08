package com.juan.monocromo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Arrays;

/** Base para animaciones pixel art de 8 bits en blanco y negro sobre una rejilla pequeña. */
abstract class PixelView extends View {
    private final int w;
    private final int h;
    private final int frameMs;
    private final boolean[][] px;
    private final Paint paint = new Paint();
    /** Si una escena lo activa, el cuadro se dibuja en negativo (fondo blanco). */
    protected boolean inverted;

    PixelView(Context c, int w, int h, int frameMs) {
        super(c);
        this.w = w;
        this.h = h;
        this.frameMs = frameMs;
        this.px = new boolean[w][h];
        paint.setAntiAlias(false);
    }

    /** Pinta el cuadro número {@code f} llamando a {@link #set}. */
    protected abstract void compose(int f);

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int f = (int) ((SystemClock.uptimeMillis() / frameMs) % 1_000_000);
        for (boolean[] col : px) Arrays.fill(col, false);
        inverted = false;
        compose(f);

        float size = Math.min(getWidth() / (float) w, getHeight() / (float) h);
        if (size >= 1) size = (float) Math.floor(size);
        float ox = (getWidth() - size * w) / 2f;
        float oy = (getHeight() - size * h) / 2f;
        paint.setColor(0xFFFFFFFF);
        if (inverted) {
            c.drawRect(ox, oy, ox + size * w, oy + size * h, paint);
            paint.setColor(0xFF000000);
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (px[x][y]) c.drawRect(ox + x * size, oy + y * size, ox + (x + 1) * size, oy + (y + 1) * size, paint);
            }
        }
        if (getWindowVisibility() == VISIBLE) postInvalidateDelayed(frameMs);
    }

    protected final void set(int x, int y) {
        if (x >= 0 && x < w && y >= 0 && y < h) px[x][y] = true;
    }

    protected final void sprite(String[] art, int x0, int y0) {
        for (int y = 0; y < art.length; y++) {
            for (int x = 0; x < art[y].length(); x++) {
                if (art[y].charAt(x) == '#') set(x0 + x, y0 + y);
            }
        }
    }

    protected static int pingPong(int t, int n) {
        int p = t % (2 * n);
        return p < n ? p : 2 * n - p;
    }
}
