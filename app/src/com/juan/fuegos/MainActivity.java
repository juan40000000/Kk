package com.juan.fuegos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    /** Elige una configuración EGL ES2 de 8 bits por canal si existe; si no, la mejor disponible. */
    static class RobustConfigChooser implements GLSurfaceView.EGLConfigChooser {
        @Override
        public javax.microedition.khronos.egl.EGLConfig chooseConfig(javax.microedition.khronos.egl.EGL10 egl,
                                                                     javax.microedition.khronos.egl.EGLDisplay display) {
            final int EGL_OPENGL_ES2_BIT = 4;
            int[] attribs = {
                    javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, 5,
                    javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, 6,
                    javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, 5,
                    javax.microedition.khronos.egl.EGL10.EGL_NONE
            };
            int[] num = new int[1];
            egl.eglChooseConfig(display, attribs, null, 0, num);
            if (num[0] <= 0) throw new IllegalArgumentException("Sin configuración OpenGL ES 2");
            javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[num[0]];
            egl.eglChooseConfig(display, attribs, configs, num[0], num);
            javax.microedition.khronos.egl.EGLConfig best = configs[0];
            int bestScore = Integer.MIN_VALUE;
            int[] v = new int[1];
            for (javax.microedition.khronos.egl.EGLConfig c : configs) {
                if (c == null) continue;
                int r = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, v);
                int g = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, v);
                int b = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, v);
                int a = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE, v);
                int d = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, v);
                int st = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_STENCIL_SIZE, v);
                int samples = get(egl, display, c, javax.microedition.khronos.egl.EGL10.EGL_SAMPLES, v);
                int score = 0;
                score += (r == 8 && g == 8 && b == 8) ? 1000 : (r + g + b) * 10;
                score -= a == 0 ? 0 : 5;
                score -= d / 8 + st + samples * 50;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }

        private static int get(javax.microedition.khronos.egl.EGL10 egl, javax.microedition.khronos.egl.EGLDisplay d,
                               javax.microedition.khronos.egl.EGLConfig c, int attr, int[] v) {
            v[0] = 0;
            return egl.eglGetConfigAttrib(d, c, attr, v) ? v[0] : 0;
        }
    }

    private GLSurfaceView glView;
    private Fireworks fw;
    private SoundEngine sound;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private float dp;

    private TextView scoreText, comboText, bestText, hintText, autoBtn, soundBtn, finaleBtn, vibBtn;
    private ProgressBar meterBar;
    private LinearLayout topBar;
    private HorizontalScrollView bottomScroll;
    private final TextView[] chips = new TextView[Fireworks.KIND_COUNT + 1];
    private boolean vibrate = true;
    private boolean autoOn = false;
    private boolean finaleReady = false;
    private int lastBestSaved = 0;

    private static final int[] CHIP_KINDS = {
            Fireworks.K_RANDOM, Fireworks.K_PEONY, Fireworks.K_CHRYS, Fireworks.K_WILLOW, Fireworks.K_PALM,
            Fireworks.K_RING, Fireworks.K_CROSSETTE, Fireworks.K_STROBE, Fireworks.K_CRACKLE,
            Fireworks.K_KAMURO, Fireworks.K_PISTIL, Fireworks.K_HEART, Fireworks.K_SALUTE
    };
    private static final int[] CHIP_COLORS = {
            0xffffffff, 0xffff3b30, 0xffffb340, 0xffe8a040, 0xffffc060, 0xff40c8ff, 0xff7cff6b,
            0xfff0f4ff, 0xffffd27a, 0xffffcf8a, 0xffc070ff, 0xffff5a8c, 0xffffffff
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("fuegos", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrate = prefs.getBoolean("vib", true);

        sound = new SoundEngine();
        sound.init(getApplicationContext());
        sound.setEnabled(prefs.getBoolean("sound", true));

        fw = new Fireworks(sound, new Fireworks.Haptics() {
            @Override
            public void buzz(int ms, int amplitude) {
                doVibrate(ms, amplitude);
            }
        });
        lastBestSaved = prefs.getInt("best", 0);
        fw.setBest(lastBestSaved);
        fw.listener = new Fireworks.Listener() {
            @Override
            public void onStats(final int score, final int combo, final float meter, final int best) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStats(score, combo, meter, best);
                    }
                });
            }
        };

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(new RobustConfigChooser());
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderer(new FireworksRenderer(fw, dp));
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                handleTouch(e);
                return true;
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(glView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        buildOverlay(root);
        setContentView(root);
        hideSystemUi();

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l = 0, t = 0, r = 0, b = 0;
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout dc = insets.getDisplayCutout();
                    if (dc != null) {
                        l = dc.getSafeInsetLeft();
                        t = dc.getSafeInsetTop();
                        r = dc.getSafeInsetRight();
                        b = dc.getSafeInsetBottom();
                    }
                }
                int pad = (int) (8 * dp);
                topBar.setPadding(l + pad, t + pad, r + pad, pad);
                bottomScroll.setPadding(l + pad, 0, r + pad, b + pad);
                return insets;
            }
        });
    }

    // ================================================================= interfaz

    private void buildOverlay(FrameLayout root) {
        // Barra superior
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (8 * dp);
        topBar.setPadding(pad, pad, pad, pad);

        LinearLayout scoreCol = new LinearLayout(this);
        scoreCol.setOrientation(LinearLayout.VERTICAL);
        scoreText = label("0", 26, true);
        bestText = label("Récord 0", 12, false);
        bestText.setTextColor(0xbbffffff);
        scoreCol.addView(scoreText);
        scoreCol.addView(bestText);
        topBar.addView(scoreCol);

        comboText = label("", 20, true);
        comboText.setTextColor(0xffffd27a);
        comboText.setPadding((int) (12 * dp), 0, 0, 0);
        topBar.addView(comboText);

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        soundBtn = button(sound.isEnabled() ? "🔊" : "🔇");
        soundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !sound.isEnabled();
                sound.setEnabled(on);
                soundBtn.setText(on ? "🔊" : "🔇");
                prefs.edit().putBoolean("sound", on).apply();
            }
        });
        topBar.addView(soundBtn);

        vibBtn = button(vibrate ? "📳" : "📴");
        vibBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrate = !vibrate;
                vibBtn.setText(vibrate ? "📳" : "📴");
                prefs.edit().putBoolean("vib", vibrate).apply();
                if (vibrate) doVibrate(20, 120);
            }
        });
        topBar.addView(vibBtn);

        autoBtn = button("AUTO");
        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoOn = !autoOn;
                styleButton(autoBtn, autoOn ? 0xff2d6cff : 0x55000000, autoOn ? 0xff8fb0ff : 0x66ffffff);
                final boolean on = autoOn;
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        fw.setAuto(on);
                    }
                });
                hideHint();
            }
        });
        topBar.addView(autoBtn);

        LinearLayout finaleCol = new LinearLayout(this);
        finaleCol.setOrientation(LinearLayout.VERTICAL);
        finaleCol.setGravity(Gravity.CENTER_HORIZONTAL);
        finaleBtn = button("GRAN FINAL");
        finaleBtn.setAlpha(0.5f);
        finaleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean free = autoOn;
                if (!finaleReady && !free) {
                    hintText.setText("Encadena explosiones seguidas\npara cargar la Gran Final");
                    hintText.setAlpha(1f);
                    hintText.animate().alpha(0f).setStartDelay(1800).setDuration(800).start();
                    return;
                }
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        fw.startFinale(free);
                    }
                });
                hideHint();
            }
        });
        finaleCol.addView(finaleBtn);
        meterBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meterBar.setMax(1000);
        meterBar.setProgress(0);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams((int) (96 * dp), (int) (6 * dp));
        mlp.topMargin = (int) (3 * dp);
        finaleCol.addView(meterBar, mlp);
        topBar.addView(finaleCol);

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(topBar, tlp);

        // Selector de carcasas
        bottomScroll = new HorizontalScrollView(this);
        bottomScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < CHIP_KINDS.length; i++) {
            final int kind = CHIP_KINDS[i];
            String name = kind == Fireworks.K_RANDOM ? "Aleatorio" : Fireworks.KIND_NAMES[kind];
            TextView chip = label("● " + name, 14, true);
            chip.setPadding((int) (14 * dp), (int) (9 * dp), (int) (14 * dp), (int) (9 * dp));
            final int idx = i;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectChip(idx);
                    glView.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            fw.setSelectedKind(kind);
                        }
                    });
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = (int) (8 * dp);
            row.addView(chip, clp);
            chips[i] = chip;
        }
        bottomScroll.addView(row);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bottomScroll, blp);
        selectChip(0);

        hintText = label("Toca el cielo para lanzar\nMantén pulsado para una ráfaga", 18, false);
        hintText.setGravity(Gravity.CENTER);
        hintText.setTextColor(0xddffffff);
        root.addView(hintText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(6 * dp, 0, 0, 0xff000000);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String text) {
        TextView b = label(text, 13, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        b.setShadowLayer(0, 0, 0, 0);
        styleButton(b, 0x55000000, 0x66ffffff);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (6 * dp);
        b.setLayoutParams(lp);
        b.setClickable(true);
        return b;
    }

    private void styleButton(TextView b, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(20 * dp);
        d.setStroke((int) Math.max(1, dp), stroke);
        b.setBackground(d);
    }

    private void selectChip(int idx) {
        for (int i = 0; i < chips.length; i++) {
            TextView c = chips[i];
            if (c == null) continue;
            boolean sel = i == idx;
            GradientDrawable d = new GradientDrawable();
            d.setColor(sel ? 0x30ffffff : 0x55000000);
            d.setCornerRadius(22 * dp);
            d.setStroke((int) Math.max(1, (sel ? 2 : 1) * dp), sel ? CHIP_COLORS[i] : 0x44ffffff);
            c.setBackground(d);
            String name = CHIP_KINDS[i] == Fireworks.K_RANDOM ? "Aleatorio" : Fireworks.KIND_NAMES[CHIP_KINDS[i]];
            android.text.SpannableString ss = new android.text.SpannableString("● " + name);
            ss.setSpan(new android.text.style.ForegroundColorSpan(CHIP_COLORS[i]), 0, 1, 0);
            c.setText(ss);
            c.setAlpha(sel ? 1f : 0.8f);
        }
    }

    private void updateStats(int score, int combo, float meter, int best) {
        scoreText.setText(String.valueOf(score));
        bestText.setText("Récord " + best);
        if (combo >= 2) {
            comboText.setText("x" + combo + " COMBO");
            comboText.setScaleX(1.15f);
            comboText.setScaleY(1.15f);
            comboText.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        } else {
            comboText.setText("");
        }
        meterBar.setProgress((int) (meter * 1000));
        boolean ready = meter >= 1f;
        if (ready != finaleReady) {
            finaleReady = ready;
            finaleBtn.setAlpha(ready ? 1f : 0.5f);
            styleButton(finaleBtn, ready ? 0xffd4380d : 0x55000000, ready ? 0xffffc069 : 0x66ffffff);
            if (ready) {
                finaleBtn.setScaleX(1.2f);
                finaleBtn.setScaleY(1.2f);
                finaleBtn.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
            }
        }
        if (best > lastBestSaved + 50 || (best > lastBestSaved && combo == 0)) {
            lastBestSaved = best;
            prefs.edit().putInt("best", best).apply();
        }
    }

    private void hideHint() {
        if (hintText.getAlpha() > 0f) hintText.animate().alpha(0f).setDuration(600).start();
    }

    // ================================================================= entrada táctil

    private void handleTouch(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                final int id = e.getPointerId(idx);
                final float x = e.getX(idx), y = e.getY(idx);
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        fw.touchDown(id, x, y);
                    }
                });
                hideHint();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int n = e.getPointerCount();
                final int[] ids = new int[n];
                final float[] xs = new float[n], ys = new float[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = e.getPointerId(i);
                    xs[i] = e.getX(i);
                    ys[i] = e.getY(i);
                }
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < n; i++) fw.touchMove(ids[i], xs[i], ys[i]);
                    }
                });
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                final int id = e.getPointerId(e.getActionIndex());
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        fw.touchUp(id);
                    }
                });
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        fw.touchCancelAll();
                    }
                });
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void doVibrate(int ms, int amplitude) {
        if (!vibrate || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, Math.max(1, Math.min(255, amplitude))));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
        sound.pause();
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                fw.touchCancelAll();
            }
        });
        int best = Math.max(lastBestSaved, prefs.getInt("best", 0));
        prefs.edit().putInt("best", best).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        sound.resume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sound.release();
    }
}
