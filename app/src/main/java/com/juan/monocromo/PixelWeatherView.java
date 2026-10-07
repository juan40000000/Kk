package com.juan.monocromo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Animación del clima en pixel art de 8 bits, en blanco y negro. */
final class PixelWeatherView extends View {
    private static final int W = 24;
    private static final int H = 16;
    private static final int FRAME_MS = 160;

    private static final String[] CLOUD = {
            "......###.....",
            "..###.#####...",
            ".###########..",
            "#############.",
            "##############",
            ".############.",
    };
    private static final String[] MOON = {
            "..###..",
            ".###...",
            "###....",
            "###....",
            "###....",
            ".###...",
            "..###..",
    };
    private static final String[] BOLT = {
            "..##.",
            ".##..",
            "####.",
            "..##.",
            ".##..",
            ".#...",
    };
    private static final int[][] STARS = {{15, 3}, {19, 6}, {14, 10}, {20, 12}, {17, 1}, {3, 13}, {22, 2}};

    private final boolean[][] px = new boolean[W][H];
    private final Paint paint = new Paint();
    private int scene = Weather.UNKNOWN;
    private boolean day = true;
    private boolean inverted;

    PixelWeatherView(Context c) {
        super(c);
        paint.setAntiAlias(false);
    }

    void setScene(int scene, boolean day) {
        this.scene = scene;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int f = (int) ((SystemClock.uptimeMillis() / FRAME_MS) % 100_000);
        for (boolean[] col : px) java.util.Arrays.fill(col, false);
        inverted = false;
        compose(f);

        float size = Math.min(getWidth() / (float) W, getHeight() / (float) H);
        if (size >= 1) size = (float) Math.floor(size);
        float ox = (getWidth() - size * W) / 2f;
        float oy = (getHeight() - size * H) / 2f;
        if (inverted) {
            paint.setColor(0xFFFFFFFF);
            c.drawRect(ox, oy, ox + size * W, oy + size * H, paint);
            paint.setColor(0xFF000000);
        } else {
            paint.setColor(0xFFFFFFFF);
        }
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (px[x][y]) c.drawRect(ox + x * size, oy + y * size, ox + (x + 1) * size, oy + (y + 1) * size, paint);
            }
        }
        if (getWindowVisibility() == VISIBLE) postInvalidateDelayed(FRAME_MS);
    }

    private void compose(int f) {
        switch (scene) {
            case Weather.CLEAR:
                if (day) sun(12, 8, f);
                else night(f);
                break;
            case Weather.PARTLY:
                if (day) sun(8, 6, f);
                else sprite(MOON, 4, 2);
                sprite(CLOUD, 8 + pingPong(f / 3, 3), 9);
                break;
            case Weather.CLOUDY:
                sprite(CLOUD, 1 + pingPong(f / 3, 4), 2);
                sprite(CLOUD, 9 - pingPong(f / 4, 3), 9);
                break;
            case Weather.FOG:
                for (int row = 3; row < H; row += 3) {
                    int dir = (row / 3) % 2 == 0 ? 1 : -1;
                    for (int x = 0; x < W; x++) {
                        if (Math.floorMod(x + dir * (f / 2), 6) < 4) set(x, row);
                    }
                }
                break;
            case Weather.RAIN:
                sprite(CLOUD, 5, 1);
                drops(f);
                break;
            case Weather.SNOW:
                sprite(CLOUD, 5, 1);
                for (int i = 0; i < 6; i++) {
                    int x = 6 + i * 2 + i / 2 + (((f / 2 + i) % 2 == 0) ? 0 : 1);
                    int y = 7 + (f / 2 + i * 3) % 9;
                    set(x, y);
                }
                break;
            case Weather.STORM:
                sprite(CLOUD, 5, 1);
                drops(f);
                if (f % 12 < 2) sprite(BOLT, 11, 7);
                inverted = f % 24 == 0;
                break;
            default:
                // Cargando / sin configurar: tres puntos que parpadean.
                for (int i = 0; i < 3; i++) {
                    if ((f / 2) % 4 > i) {
                        set(8 + i * 4, 8);
                        set(9 + i * 4, 8);
                        set(8 + i * 4, 9);
                        set(9 + i * 4, 9);
                    }
                }
                break;
        }
    }

    private void sun(int cx, int cy, int f) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                if (dx * dx + dy * dy <= 6) set(cx + dx, cy + dy);
            }
        }
        boolean straight = (f / 3) % 2 == 0;
        int[][] dirs = straight
                ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        int from = straight ? 4 : 3;
        for (int[] d : dirs) {
            for (int r = from; r <= from + 1; r++) set(cx + d[0] * r, cy + d[1] * r);
        }
    }

    private void night(int f) {
        sprite(MOON, 5, 4);
        for (int i = 0; i < STARS.length; i++) {
            int x = STARS[i][0], y = STARS[i][1];
            int phase = (f / 2 + i * 2) % 7;
            if (phase == 0) continue;
            set(x, y);
            if (phase == 3) {
                set(x - 1, y);
                set(x + 1, y);
                set(x, y - 1);
                set(x, y + 1);
            }
        }
    }

    private void drops(int f) {
        for (int i = 0; i < 6; i++) {
            int x = 6 + i * 2 + i / 2;
            int y = 7 + (f + i * 5) % 9;
            set(x, y);
            set(x, y + 1);
        }
    }

    private void sprite(String[] art, int x0, int y0) {
        for (int y = 0; y < art.length; y++) {
            for (int x = 0; x < art[y].length(); x++) {
                if (art[y].charAt(x) == '#') set(x0 + x, y0 + y);
            }
        }
    }

    private void set(int x, int y) {
        if (x >= 0 && x < W && y >= 0 && y < H) px[x][y] = true;
    }

    private static int pingPong(int t, int n) {
        int p = t % (2 * n);
        return p < n ? p : 2 * n - p;
    }
}
