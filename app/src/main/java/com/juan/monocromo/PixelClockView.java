package com.juan.monocromo;

import android.content.Context;

import java.util.Calendar;

/**
 * Reloj pixel art: dígitos de 8 bits, dos puntos que parpadean, barra de segundos
 * y un barrido de arriba abajo cuando cambia un dígito.
 */
final class PixelClockView extends PixelView {
    private static final int W = 17;
    private static final int H = 8;
    private static final String[][] DIGITS = {
            {"###", "#.#", "#.#", "#.#", "###"},
            {".#.", "##.", ".#.", ".#.", "###"},
            {"###", "..#", "###", "#..", "###"},
            {"###", "..#", "###", "..#", "###"},
            {"#.#", "#.#", "###", "..#", "..#"},
            {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"},
            {"###", "..#", "..#", ".#.", ".#."},
            {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"},
    };
    private static final int[] DIGIT_X = {0, 4, 10, 14};

    private boolean use24h = true;
    private final int[] shown = {-1, -1, -1, -1};
    private final int[] changedAt = new int[4];

    PixelClockView(Context c) {
        super(c, W, H, 125);
    }

    void set24h(boolean v) {
        use24h = v;
    }

    @Override
    protected void compose(int f) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (!use24h) {
            hour %= 12;
            if (hour == 0) hour = 12;
        }
        int min = now.get(Calendar.MINUTE);
        int sec = now.get(Calendar.SECOND);
        int[] digits = {hour / 10, hour % 10, min / 10, min % 10};

        for (int i = 0; i < 4; i++) {
            if (digits[i] != shown[i]) {
                changedAt[i] = shown[i] < 0 ? f - 10 : f;
                shown[i] = digits[i];
            }
            if (i == 0 && digits[0] == 0 && !use24h) continue;
            // Barrido: las filas del dígito nuevo aparecen una a una durante 5 cuadros.
            int rows = Math.min(5, f - changedAt[i] + 1);
            String[] art = DIGITS[digits[i]];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < 3; x++) {
                    if (art[y].charAt(x) == '#') set(DIGIT_X[i] + x, y);
                }
            }
        }

        // Dos puntos: encendidos en los segundos pares.
        if (sec % 2 == 0) {
            set(8, 1);
            set(8, 3);
        }

        // Barra de segundos: se llena a lo largo del minuto y la punta parpadea.
        int filled = sec * W / 60;
        for (int x = 0; x < filled; x++) set(x, 7);
        if ((f / 2) % 2 == 0) set(filled, 7);
    }
}
