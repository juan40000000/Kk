package com.juan.fuegos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Modo Guerra 3D: tu ciudad contra la ciudad enemiga (CPU). */
public class WarActivity extends Activity {

    private GLSurfaceView glView;
    private WarRenderer renderer;
    private War war;
    private SoundEngine sound;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private float dp;
    private boolean vibrate;

    private LinearLayout topBar, bottomBar, menuPanel;
    private ProgressBar playerBar, enemyBar;
    private TextView playerPct, enemyPct, shieldText, msgText, menuTitle, menuSub, recordText;
    private final TextView[] weaponBtns = new TextView[War.WCOUNT];
    private final LinearLayout[] menuRows = new LinearLayout[2];
    private int lastSelected = -1;
    private int lastDifficulty = 1;

    private static final int[] WEAPON_COLORS = {0xff7fb8ff, 0xffffc060, 0xffff8a3d, 0xffff4d2e};
    private static final String[] WEAPON_ICONS = {"🚀", "💥", "🎇", "🔥"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("fuegos", MODE_PRIVATE);
        vibrate = prefs.getBoolean("vib", true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        sound = new SoundEngine();
        sound.init(getApplicationContext());
        sound.setEnabled(prefs.getBoolean("sound", true));

        war = new War(sound, new Fireworks.Haptics() {
            @Override
            public void buzz(int ms, int amplitude) {
                doVibrate(ms, amplitude);
            }
        });
        war.listener = new War.Listener() {
            @Override
            public void onStats(final float pi, final float ei, float[] cd, final int charges, final int sel) {
                final float[] c = cd.clone();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStats(pi, ei, c, charges, sel);
                    }
                });
            }

            @Override
            public void onState(final int state) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showState(state);
                    }
                });
            }

            @Override
            public void onMessage(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        flashMessage(msg);
                    }
                });
            }
        };

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(new MainActivity.RobustConfigChooser());
        glView.setPreserveEGLContextOnPause(true);
        renderer = new WarRenderer(war, dp);
        glView.setRenderer(renderer);
        glView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                    int i = e.getActionIndex();
                    final float x = e.getX(i), y = e.getY(i);
                    glView.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            renderer.tap(x, y);
                        }
                    });
                }
                return true;
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(glView, new FrameLayout.LayoutParams(-1, -1));
        buildHud(root);
        buildMenu(root);
        setContentView(root);
        hideSystemUi();
        showState(War.S_MENU);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l = 0, t = 0, r = 0, b = 0;
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout dc = insets.getDisplayCutout();
                    if (dc != null) {
                        l = dc.getSafeInsetLeft(); t = dc.getSafeInsetTop();
                        r = dc.getSafeInsetRight(); b = dc.getSafeInsetBottom();
                    }
                }
                int pad = (int) (8 * dp);
                topBar.setPadding(l + pad, t + pad, r + pad, pad);
                bottomBar.setPadding(l + pad, 0, r + pad, b + pad);
                return insets;
            }
        });
    }

    // ================================================================= HUD

    private void buildHud(FrameLayout root) {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = button("✕", 0x66000000, 0x66ffffff);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back);

        LinearLayout pCol = barColumn("TU CIUDAD", 0xff4da3ff, true);
        playerBar = (ProgressBar) pCol.getChildAt(1);
        playerPct = (TextView) ((LinearLayout) pCol.getChildAt(0)).getChildAt(1);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1f);
        plp.leftMargin = (int) (10 * dp);
        topBar.addView(pCol, plp);

        TextView vs = label("VS", 16, true);
        vs.setTextColor(0xffffd27a);
        vs.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        topBar.addView(vs);

        LinearLayout eCol = barColumn("ENEMIGO", 0xffff4d3d, false);
        enemyBar = (ProgressBar) eCol.getChildAt(1);
        enemyPct = (TextView) ((LinearLayout) eCol.getChildAt(0)).getChildAt(1);
        topBar.addView(eCol, new LinearLayout.LayoutParams(0, -2, 1f));

        root.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        for (int w = 0; w < War.WCOUNT; w++) {
            final int ww = w;
            TextView b = button(WEAPON_ICONS[w] + " " + War.W_NAMES[w], 0x66000000, 0x55ffffff);
            b.setTextSize(13);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    glView.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            war.selectWeapon(ww);
                        }
                    });
                    highlightWeapon(ww);
                }
            });
            weaponBtns[w] = b;
            bottomBar.addView(b);
        }
        shieldText = label("🛡 3", 15, true);
        shieldText.setPadding((int) (14 * dp), 0, 0, 0);
        bottomBar.addView(shieldText);
        root.addView(bottomBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        highlightWeapon(0);

        msgText = label("", 20, true);
        msgText.setGravity(Gravity.CENTER);
        msgText.setAlpha(0f);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        mlp.topMargin = (int) (70 * dp);
        root.addView(msgText, mlp);
    }

    private LinearLayout barColumn(String title, int color, boolean left) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = label(title, 12, true);
        t.setTextColor(color);
        TextView pct = label("100%", 12, true);
        pct.setPadding((int) (8 * dp), 0, 0, 0);
        row.addView(t);
        row.addView(pct);
        row.setGravity(left ? Gravity.START : Gravity.END);
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(1000);
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(color));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x55ffffff));
        }
        if (!left) bar.setRotation(180f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, (int) (8 * dp));
        blp.topMargin = (int) (3 * dp);
        col.addView(bar, blp);
        return col;
    }

    private void buildMenu(FrameLayout root) {
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        int p = (int) (22 * dp);
        menuPanel.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xbb05070f);
        bg.setCornerRadius(18 * dp);
        bg.setStroke((int) Math.max(1, dp), 0x44ffffff);
        menuPanel.setBackground(bg);

        menuTitle = label("GUERRA DE FUEGOS", 30, true);
        menuTitle.setGravity(Gravity.CENTER);
        menuTitle.setTextColor(0xffffd27a);
        menuPanel.addView(menuTitle);

        menuSub = label("", 14, false);
        menuSub.setGravity(Gravity.CENTER);
        menuSub.setTextColor(0xddffffff);
        menuSub.setPadding(0, (int) (8 * dp), 0, (int) (14 * dp));
        menuPanel.addView(menuSub);

        // Fila 1: dificultad (menú) / revancha (fin de partida)
        menuRows[0] = new LinearLayout(this);
        menuRows[0].setOrientation(LinearLayout.HORIZONTAL);
        menuRows[0].setGravity(Gravity.CENTER);
        String[] diffs = {"Fácil", "Normal", "Difícil"};
        int[] diffCol = {0xff3fbf6f, 0xffd9a21b, 0xffd4380d};
        for (int d = 0; d < 3; d++) {
            final int dd = d;
            TextView b = button(diffs[d], diffCol[d], 0x88ffffff);
            b.setTextSize(17);
            b.setPadding((int) (22 * dp), (int) (12 * dp), (int) (22 * dp), (int) (12 * dp));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startGame(dd);
                }
            });
            menuRows[0].addView(b);
        }
        menuPanel.addView(menuRows[0]);

        menuRows[1] = new LinearLayout(this);
        menuRows[1].setOrientation(LinearLayout.HORIZONTAL);
        menuRows[1].setGravity(Gravity.CENTER);
        TextView again = button("⚔ Revancha", 0xffd4380d, 0x88ffffff);
        again.setTextSize(17);
        again.setPadding((int) (22 * dp), (int) (12 * dp), (int) (22 * dp), (int) (12 * dp));
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame(lastDifficulty);
            }
        });
        TextView menu = button("Menú", 0x66000000, 0x88ffffff);
        menu.setTextSize(17);
        menu.setPadding((int) (22 * dp), (int) (12 * dp), (int) (22 * dp), (int) (12 * dp));
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                glView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        war.toMenu();
                    }
                });
            }
        });
        menuRows[1].addView(again);
        menuRows[1].addView(menu);
        menuPanel.addView(menuRows[1]);

        recordText = label("", 13, false);
        recordText.setGravity(Gravity.CENTER);
        recordText.setTextColor(0xaaffffff);
        recordText.setPadding(0, (int) (14 * dp), 0, 0);
        menuPanel.addView(recordText);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = (int) (16 * dp);
        root.addView(menuPanel, lp);
    }

    private void startGame(final int diff) {
        lastDifficulty = diff;
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                war.newGame(diff);
            }
        });
    }

    private void showState(int state) {
        boolean playing = state == War.S_PLAY;
        menuPanel.setVisibility(playing ? View.GONE : View.VISIBLE);
        topBar.setVisibility(state == War.S_MENU ? View.INVISIBLE : View.VISIBLE);
        bottomBar.setVisibility(playing ? View.VISIBLE : View.INVISIBLE);
        int wins = prefs.getInt("warWins", 0), losses = prefs.getInt("warLosses", 0);
        if (state == War.S_MENU) {
            menuTitle.setText("GUERRA DE FUEGOS");
            menuTitle.setTextColor(0xffffd27a);
            menuSub.setText("Destruye la ciudad enemiga con fuegos artificiales.\n" +
                    "Toca la ciudad enemiga para atacar · Toca un cohete enemigo para interceptarlo\n" +
                    "Elige la dificultad:");
            menuRows[0].setVisibility(View.VISIBLE);
            menuRows[1].setVisibility(View.GONE);
        } else if (state == War.S_WON || state == War.S_LOST) {
            boolean won = state == War.S_WON;
            if (won) wins++; else losses++;
            prefs.edit().putInt(won ? "warWins" : "warLosses", won ? wins : losses).apply();
            menuTitle.setText(won ? "¡VICTORIA!" : "DERROTA");
            menuTitle.setTextColor(won ? 0xff7cff6b : 0xffff4d3d);
            menuSub.setText(won ? "La ciudad enemiga ha caído. ¡Celebra con fuegos artificiales!"
                    : "Tu ciudad ha caído… ¿Revancha?");
            menuRows[0].setVisibility(View.GONE);
            menuRows[1].setVisibility(View.VISIBLE);
            menuPanel.setAlpha(0f);
            menuPanel.animate().alpha(1f).setStartDelay(1200).setDuration(600).start();
        }
        if (state != War.S_WON && state != War.S_LOST) menuPanel.setAlpha(1f);
        recordText.setText("Victorias " + wins + " · Derrotas " + losses);
    }

    private void updateStats(float pi, float ei, float[] cd, int charges, int sel) {
        // La partida termina al 25 %: se muestra como 0-100 % de la ciudad "en pie"
        float pShow = Math.max(0f, (pi - War.LOSE_AT) / (1f - War.LOSE_AT));
        float eShow = Math.max(0f, (ei - War.LOSE_AT) / (1f - War.LOSE_AT));
        playerBar.setProgress((int) (pShow * 1000));
        enemyBar.setProgress((int) (eShow * 1000));
        playerPct.setText(Math.round(pShow * 100) + "%");
        enemyPct.setText(Math.round(eShow * 100) + "%");
        for (int w = 0; w < War.WCOUNT; w++) {
            TextView b = weaponBtns[w];
            if (cd[w] > 0.01f) {
                float secs = cd[w] * War.W_COOLDOWN[w];
                b.setText(WEAPON_ICONS[w] + " " + War.W_NAMES[w] + " " + String.format(java.util.Locale.US, "%.1f", secs));
                b.setAlpha(0.45f);
            } else {
                b.setText(WEAPON_ICONS[w] + " " + War.W_NAMES[w]);
                b.setAlpha(1f);
            }
        }
        StringBuilder sb = new StringBuilder("🛡 ");
        for (int i = 0; i < 3; i++) sb.append(i < charges ? "●" : "○");
        shieldText.setText(sb.toString());
        if (sel != lastSelected) highlightWeapon(sel);
    }

    private void highlightWeapon(int w) {
        lastSelected = w;
        for (int i = 0; i < War.WCOUNT; i++) {
            boolean s = i == w;
            styleButton(weaponBtns[i], s ? 0x33ffffff : 0x66000000,
                    s ? WEAPON_COLORS[i] : 0x55ffffff, s ? 2 : 1);
        }
    }

    private void flashMessage(String msg) {
        msgText.setText(msg);
        msgText.animate().cancel();
        msgText.setAlpha(1f);
        msgText.setScaleX(1.15f);
        msgText.setScaleY(1.15f);
        msgText.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        msgText.animate().alpha(0f).setStartDelay(1300).setDuration(500).start();
    }

    // ================================================================= utilidades UI

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(6 * dp, 0, 0, 0xff000000);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String text, int fill, int stroke) {
        TextView b = label(text, 14, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (12 * dp), (int) (9 * dp), (int) (12 * dp), (int) (9 * dp));
        styleButton(b, fill, stroke, 1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = lp.rightMargin = (int) (4 * dp);
        b.setLayoutParams(lp);
        b.setClickable(true);
        return b;
    }

    private void styleButton(TextView b, int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(20 * dp);
        d.setStroke((int) Math.max(1, strokeDp * dp), stroke);
        b.setBackground(d);
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
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
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
