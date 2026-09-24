package com.juan.notasvoz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Colores y piezas de interfaz al estilo Windows Phone (Metro). */
public final class Metro {
    public static final int LIME = 0xFFA4C400;
    public static final int GREEN = 0xFF60A917;
    public static final int EMERALD = 0xFF008A00;
    public static final int TEAL = 0xFF00ABA9;
    public static final int CYAN = 0xFF1BA1E2;
    public static final int COBALT = 0xFF0050EF;
    public static final int INDIGO = 0xFF6A00FF;
    public static final int VIOLET = 0xFFAA00FF;
    public static final int PINK = 0xFFF472D0;
    public static final int MAGENTA = 0xFFD80073;
    public static final int CRIMSON = 0xFFA20025;
    public static final int RED = 0xFFE51400;
    public static final int ORANGE = 0xFFFA6800;
    public static final int AMBER = 0xFFF0A30A;
    public static final int YELLOW = 0xFFE3C800;
    public static final int BROWN = 0xFF825A2C;

    public static final int[] TILE_COLORS = {
            MAGENTA, CYAN, LIME, ORANGE, VIOLET, TEAL, RED, COBALT,
            AMBER, EMERALD, PINK, INDIGO, CRIMSON, GREEN, YELLOW, BROWN
    };

    public static final int APP_BAR = 0xFF1F1F1F;
    public static final int SUBTLE = 0xFF8A8A8A;

    public static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface SEMIBOLD = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public static final int REQ_SPEECH = 42;

    private Metro() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sp, Typeface face, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        return t;
    }

    /** Efecto "tilt": el elemento se hunde un poco al tocarlo, como en Windows Phone. */
    @SuppressLint("ClickableViewAccessibility")
    public static void tilt(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
            }
            return false;
        });
    }

    /** Transición "turnstile": la página entra girando desde el borde izquierdo. */
    public static void turnstileIn(View root) {
        root.setCameraDistance(8000 * root.getResources().getDisplayMetrics().density);
        root.setPivotX(0);
        root.post(() -> root.setPivotY(root.getHeight() / 2f));
        root.setRotationY(-75f);
        root.setAlpha(0f);
        root.animate().rotationY(0f).alpha(1f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    /** Aparición de un tile: se voltea como las live tiles. */
    public static void flipIn(View tile, long delay) {
        tile.setRotationX(-90f);
        tile.setAlpha(0f);
        tile.animate().rotationX(0f).alpha(1f).setStartDelay(delay).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** Botón circular de la barra inferior, con su etiqueta en minúsculas debajo. */
    public static LinearLayout appBarButton(Context c, int icon, String label, int fill,
                                            View.OnClickListener onClick) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 6));

        ImageView iv = new ImageView(c);
        int size = dp(c, 48);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (fill != 0) {
            circle.setColor(fill);
        } else {
            circle.setColor(Color.TRANSPARENT);
            circle.setStroke(dp(c, 2), Color.WHITE);
        }
        iv.setBackground(circle);
        iv.setImageResource(icon);
        int pad = dp(c, 12);
        iv.setPadding(pad, pad, pad, pad);
        box.addView(iv, new LinearLayout.LayoutParams(size, size));

        TextView t = text(c, label, 11, REGULAR, Color.WHITE);
        t.setPadding(0, dp(c, 4), 0, 0);
        box.addView(t);

        box.setOnClickListener(onClick);
        tilt(box);
        return box;
    }

    public static LinearLayout appBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(APP_BAR);
        bar.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return bar;
    }

    public static Intent speechIntent(String prompt) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        return i;
    }

    public static boolean startSpeech(Activity a, String prompt) {
        try {
            a.startActivityForResult(speechIntent(prompt), REQ_SPEECH);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(a,
                    "Este teléfono no tiene reconocimiento de voz (instala la app de Google)",
                    android.widget.Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Primera letra en mayúscula, como al dictar una frase. */
    public static String capitalize(String s) {
        s = s.trim();
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    public static String friendlyDate(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            int diff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR);
            if (diff == 0) return "hoy " + time;
            if (diff == 1) return "ayer " + time;
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(millis))
                    .toLowerCase(Locale.getDefault()).replace(".", "") + " " + time;
        }
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(millis))
                .toLowerCase(Locale.getDefault()).replace(".", "");
    }
}
