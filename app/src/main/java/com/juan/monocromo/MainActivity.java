package com.juan.monocromo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher monocromo al estilo Windows Phone: pantalla de inicio con mosaicos
 * en blanco y negro y, deslizando a la izquierda, la lista de todas las apps
 * en texto blanco sobre fondo negro, sin iconos ni colores.
 */
public class MainActivity extends Activity {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int GRAY = 0xFF8A8A8A;
    private static final int DIM = 0xFF3A3A3A;
    private static final String LETTERS = "#abcdefghijklmnopqrstuvwxyz";
    private static final int[] PAUSE_OPTIONS = {0, 5, 10, 20, 30, 60};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private final Map<String, AppEntry> byKey = new HashMap<>();

    private Prefs prefs;
    private Typeface light;
    private int screenW;

    private FrameLayout root;
    private ScrollView startPage;
    private LinearLayout startContent;
    private LinearLayout listPage;
    private EditText search;
    private ListView listView;
    private AppListAdapter adapter;
    private FrameLayout overlay;
    private Runnable overlayCancel;
    private boolean onList;

    private PixelClockView clockPixels;
    private TextView clockDate;
    private TextView clockStats;

    private FrameLayout grid;
    private final Map<String, Tile> tiles = new LinkedHashMap<>();
    private int cell;
    private int gap;
    private Tile resizing;
    private int resizeStart;
    private boolean resizeMoved;
    private boolean resizeChanged;
    private float downX;
    private float downY;

    private PixelWeatherView weatherPixels;
    private TextView weatherTemp;
    private TextView weatherDesc;
    private TextView weatherSub;
    private boolean weatherLoading;
    private long weatherLastTry;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private LiveData liveData;
    private int liveTickCount;
    private long lastLiveRefresh;

    private GestureDetector gestures;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            refreshWeather(false);
            if (System.currentTimeMillis() - lastLiveRefresh > 60_000) {
                lastLiveRefresh = System.currentTimeMillis();
                refreshLiveData();
            }
            handler.postDelayed(this, 10_000);
        }
    };

    /** Cada 4 s: los mosaicos dinámicos giran y se actualizan. */
    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            updateLiveTiles(true);
            handler.postDelayed(this, 4_000);
        }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        liveData = new LiveData(this);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        screenW = getResources().getDisplayMetrics().widthPixels;
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);

        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        root.setFitsSystemWindows(true);

        buildStartPage();
        buildListPage();

        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xF2000000);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);

        root.addView(startPage, match());
        root.addView(listPage, match());
        root.addView(overlay, match());
        setContentView(root);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < dp(70) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                if (dx < 0 && !onList) showList();
                else if (dx > 0 && onList) showStart();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        pinBrowserOnce();
        renderStart();
        adapter.rebuild(search.getText().toString());
        lastLiveRefresh = 0;
        handler.post(clockTick);
        handler.postDelayed(liveTick, 4_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(liveTick);
        // Las esperas (pausa, desbloqueo) solo cuentan mirando la pantalla: al salir se reinician.
        hideOverlay(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botón de inicio: volver siempre a la pantalla de inicio, arriba del todo.
        hideOverlay(true);
        showStart();
        startPage.smoothScrollTo(0, 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (overlay.getVisibility() != View.VISIBLE && resizing == null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) hideOverlay(true);
        else if (onList) showStart();
        // En la pantalla de inicio, "atrás" no hace nada: es el launcher.
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        apps.clear();
        byKey.clear();
        for (ResolveInfo ri : found) {
            // Este launcher no aparece en la lista; su navegador Calamuchita sí.
            if (ri.activityInfo == null || MainActivity.class.getName().equals(ri.activityInfo.name)) continue;
            CharSequence l = ri.loadLabel(pm);
            AppEntry a = new AppEntry(l == null ? ri.activityInfo.packageName : l.toString().trim(),
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
            if (byKey.containsKey(a.key)) continue;
            apps.add(a);
            byKey.put(a.key, a);
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(apps, (x, y) -> {
            boolean xs = "#".equals(x.letter), ys = "#".equals(y.letter);
            if (xs != ys) return xs ? -1 : 1;
            return collator.compare(x.lower, y.lower);
        });
    }

    /** La primera vez, ancla Calamuchita como mosaico ancho negro. */
    private void pinBrowserOnce() {
        if (prefs.isBrowserPinnedOnce()) return;
        String key = new ComponentName(this, BrowserActivity.class).flattenToString();
        if (!byKey.containsKey(key)) return;
        if (!prefs.isPinned(key)) prefs.togglePinned(key);
        prefs.setTileSize(key, Prefs.WIDE);
        if (!prefs.isBlackTile(key)) prefs.toggleBlackTile(key);
        prefs.setBrowserPinnedOnce();
    }

    private void requestLaunch(AppEntry a) {
        if (prefs.blockedUntil(a.key) > 0) showBlocked(a, false);
        else if (prefs.isDistracting(a.key) && prefs.pauseSeconds() > 0) showPause(a);
        else launch(a);
    }

    private void launch(AppEntry a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            prefs.countLaunch(a.key);
            if (search.length() > 0) search.setText("");
            hideKeyboard();
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "no se pudo abrir " + a.lower, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ inicio (mosaicos)

    /** Columnas de la rejilla: pequeño 1x1, mediano 2x2, ancho 4x2, grande 4x4. */
    private static final int COLS = 4;
    private static final int[] SIZE_W = {1, 2, 4, 4};
    private static final int[] SIZE_H = {1, 2, 2, 4};
    private static final String[] SIZE_NAMES = {"pequeño", "mediano", "ancho", "grande"};

    private static final class Tile {
        AppEntry app;
        FrameLayout view;
        FrameLayout front;
        TextView mono;
        TextView label;
        TextView mark;
        LinearLayout back;
        TextView big;
        TextView line;
        TextView backLabel;
        boolean showingBack;
    }

    private void buildStartPage() {
        startPage = new ScrollView(this);
        startPage.setVerticalScrollBarEnabled(false);
        startPage.setOverScrollMode(View.OVER_SCROLL_NEVER);
        startContent = new LinearLayout(this);
        startContent.setOrientation(LinearLayout.VERTICAL);
        startContent.setPadding(dp(12), dp(20), dp(12), dp(24));
        startPage.addView(startContent);
    }

    private void renderStart() {
        startContent.removeAllViews();
        tiles.clear();
        gap = dp(8);
        int avail = screenW - dp(24);
        cell = (avail - gap * (COLS - 1)) / COLS;
        int half = cell * 2 + gap;

        // Reloj y clima en la misma fila, fijos arriba.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(half, half);
        cp.rightMargin = gap;
        top.addView(buildClockTile(), cp);
        top.addView(buildWeatherTile(), new LinearLayout.LayoutParams(half, half));
        startContent.addView(top, lp(avail, half, gap));
        updateClock();
        updateWeather();

        grid = new FrameLayout(this);
        grid.setClipChildren(false);
        startContent.addView(grid, lp(avail, 0, 0));
        for (String key : prefs.pinned()) {
            AppEntry a = byKey.get(key);
            if (a == null || tiles.containsKey(key)) continue;
            Tile t = makeTile(a);
            tiles.put(key, t);
            grid.addView(t.view, new FrameLayout.LayoutParams(cell, cell));
        }
        layoutTiles(false);
        updateLiveTiles(false);

        if (tiles.isEmpty()) {
            TextView hint = text("desliza a la izquierda para ver tus apps.\n"
                    + "mantén pulsada una app para anclarla aquí.", 16, GRAY);
            hint.setPadding(dp(4), dp(12), dp(4), dp(12));
            startContent.addView(hint);
        } else if (!prefs.isHintSeen()) {
            TextView hint = text("mantén pulsado un mosaico y desliza para cambiar su tamaño. "
                    + "si sueltas sin deslizar: color, bloqueo y más.", 14, GRAY);
            hint.setPadding(dp(4), dp(4), dp(4), dp(4));
            startContent.addView(hint);
        }

        // Pie: ajustes y flecha hacia la lista, como en Windows Phone.
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(4), dp(16), dp(4), 0);
        TextView dots = text("• • •", 20, WHITE);
        dots.setPadding(0, dp(8), dp(24), dp(8));
        dots.setOnClickListener(v -> showSettings());
        footer.addView(dots);
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView all = text("todas las apps", 16, WHITE);
        all.setPadding(0, 0, dp(12), 0);
        footer.addView(all);
        TextView arrow = text("→", 22, WHITE);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(roundOutline());
        footer.addView(arrow, new LinearLayout.LayoutParams(dp(44), dp(44)));
        footer.setOnClickListener(v -> showList());
        startContent.addView(footer);
    }

    private View buildClockTile() {
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setPadding(dp(10), dp(12), dp(10), dp(8));
        clock.setBackground(outline(BLACK, WHITE, 2));
        clockPixels = new PixelClockView(this);
        clock.addView(clockPixels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        clockDate = text("", 13, WHITE);
        clockDate.setSingleLine(true);
        clockDate.setPadding(dp(2), dp(6), 0, 0);
        clockStats = text("", 11, GRAY);
        clockStats.setSingleLine(true);
        clockStats.setEllipsize(TextUtils.TruncateAt.END);
        clockStats.setPadding(dp(2), 0, 0, 0);
        clock.addView(clockDate);
        clock.addView(clockStats);
        clock.setOnClickListener(v -> showStats());
        clock.setOnLongClickListener(v -> {
            showSettings();
            return true;
        });
        addTilt(clock);
        return clock;
    }

    private View buildWeatherTile() {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(dp(10), dp(10), dp(10), dp(8));
        w.setBackground(outline(BLACK, WHITE, 2));
        weatherPixels = new PixelWeatherView(this);
        w.addView(weatherPixels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        weatherTemp = text("", 24, WHITE);
        weatherTemp.setIncludeFontPadding(false);
        row.addView(weatherTemp);
        weatherDesc = text("", 12, WHITE);
        weatherDesc.setSingleLine(true);
        weatherDesc.setEllipsize(TextUtils.TruncateAt.END);
        weatherDesc.setPadding(dp(6), 0, 0, dp(2));
        row.addView(weatherDesc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setPadding(dp(2), dp(4), 0, 0);
        w.addView(row);
        weatherSub = text("", 11, GRAY);
        weatherSub.setSingleLine(true);
        weatherSub.setEllipsize(TextUtils.TruncateAt.END);
        weatherSub.setPadding(dp(2), 0, 0, 0);
        w.addView(weatherSub);

        w.setOnClickListener(v -> {
            if (prefs.weatherMode().isEmpty()) weatherSetup();
            else refreshWeather(true);
        });
        w.setOnLongClickListener(v -> {
            weatherSetup();
            return true;
        });
        addTilt(w);
        return w;
    }

    private Tile makeTile(AppEntry a) {
        Tile t = new Tile();
        t.app = a;
        t.view = new FrameLayout(this);
        t.view.setCameraDistance(8000 * getResources().getDisplayMetrics().density);

        // Cara delantera: inicial grande y nombre.
        t.front = new FrameLayout(this);
        t.view.addView(t.front, match());
        t.mono = text(a.initial(), 44, BLACK);
        t.front.addView(t.mono, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        t.label = tileLabel(a.lower);
        t.front.addView(t.label, bottomLabel());
        // Arriba a la derecha: ⧗ si tiene pausa, o el número de notificaciones.
        t.mark = text("", 14, BLACK);
        FrameLayout.LayoutParams mk = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        mk.setMargins(0, dp(2), dp(6), 0);
        t.front.addView(t.mark, mk);

        // Cara trasera (mosaico dinámico): información de la app.
        t.back = new LinearLayout(this);
        t.back.setOrientation(LinearLayout.VERTICAL);
        t.back.setPadding(dp(8), dp(6), dp(8), dp(6));
        t.back.setVisibility(View.GONE);
        t.big = text("", 20, BLACK);
        t.big.setEllipsize(TextUtils.TruncateAt.END);
        t.line = text("", 12, BLACK);
        t.line.setEllipsize(TextUtils.TruncateAt.END);
        t.backLabel = tileLabel(a.lower);
        t.backLabel.setPadding(0, dp(4), 0, 0);
        t.back.addView(t.big);
        t.back.addView(t.line);
        t.back.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        t.back.addView(t.backLabel);
        // El espaciador empuja la etiqueta abajo; el texto queda arriba.
        t.back.setGravity(Gravity.TOP);
        t.view.addView(t.back, match());

        t.view.setOnClickListener(v -> requestLaunch(a));
        t.view.setOnLongClickListener(v -> {
            startResize(t);
            return true;
        });
        t.view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (t == resizing) {
                        onResizeMove(e.getRawX() - downX, e.getRawY() - downY);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    if (t == resizing) {
                        boolean menu = e.getActionMasked() == MotionEvent.ACTION_UP
                                && !resizeMoved && !resizeChanged;
                        endResize();
                        if (menu) tileMenu(a);
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
        return t;
    }

    private TextView tileLabel(String s) {
        TextView l = text(s, 13, BLACK);
        l.setTypeface(Typeface.DEFAULT);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        return l;
    }

    private FrameLayout.LayoutParams bottomLabel() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        p.setMargins(dp(8), 0, dp(8), dp(6));
        return p;
    }

    /** Coloca los mosaicos en la rejilla: cada uno en el primer hueco libre, como en Windows Phone. */
    private void layoutTiles(boolean animate) {
        if (grid == null) return;
        if (animate) TransitionManager.beginDelayedTransition(grid, new ChangeBounds().setDuration(160));
        List<boolean[]> used = new ArrayList<>();
        int rows = 0;
        for (String key : prefs.pinned()) {
            Tile t = tiles.get(key);
            if (t == null) continue;
            int size = prefs.tileSize(key);
            int w = SIZE_W[size], h = SIZE_H[size];
            int row = 0, col = 0;
            search:
            for (row = 0; ; row++) {
                for (col = 0; col + w <= COLS; col++) {
                    if (fits(used, row, col, w, h)) break search;
                }
            }
            for (int r = row; r < row + h; r++) {
                for (int c = col; c < col + w; c++) used.get(r)[c] = true;
            }
            rows = Math.max(rows, row + h);

            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) t.view.getLayoutParams();
            p.width = w * cell + (w - 1) * gap;
            p.height = h * cell + (h - 1) * gap;
            p.leftMargin = col * (cell + gap);
            p.topMargin = row * (cell + gap);
            t.view.setLayoutParams(p);
            styleTile(t, size);
        }
        ViewGroup.LayoutParams gp = grid.getLayoutParams();
        gp.height = rows * (cell + gap);
        grid.setLayoutParams(gp);
    }

    private static boolean fits(List<boolean[]> used, int row, int col, int w, int h) {
        while (used.size() < row + h) used.add(new boolean[COLS]);
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (used.get(r)[c]) return false;
            }
        }
        return true;
    }

    private void styleTile(Tile t, int size) {
        boolean black = prefs.isBlackTile(t.app.key);
        int fg = black ? WHITE : BLACK;
        t.view.setBackground(black ? outline(BLACK, WHITE, 2) : solid(WHITE));
        for (TextView v : new TextView[]{t.mono, t.label, t.mark, t.big, t.line, t.backLabel}) v.setTextColor(fg);
        boolean small = size == Prefs.SMALL;
        t.mono.setTextSize(TypedValue.COMPLEX_UNIT_SP, new int[]{20, 44, 52, 80}[size]);
        // En los pequeños la inicial sube un poco para dejar sitio al nombre.
        FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) t.mono.getLayoutParams();
        mp.bottomMargin = small ? dp(12) : 0;
        t.mono.setLayoutParams(mp);
        t.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, small ? 10 : 13);
        FrameLayout.LayoutParams lpl = (FrameLayout.LayoutParams) t.label.getLayoutParams();
        lpl.setMargins(small ? dp(4) : dp(8), 0, small ? dp(4) : dp(8), small ? dp(3) : dp(6));
        t.label.setLayoutParams(lpl);
        t.big.setTextSize(TypedValue.COMPLEX_UNIT_SP, new int[]{14, 18, 22, 28}[size]);
        t.big.setMaxLines(size == Prefs.LARGE ? 3 : 2);
        t.line.setMaxLines(new int[]{1, 2, 2, 8}[size]);
    }

    // ---- mosaicos dinámicos ----

    /** Actualiza bloqueo, notificaciones e información; si {@code flip}, gira algunos mosaicos. */
    private void updateLiveTiles(boolean flip) {
        if (grid == null) return;
        boolean live = prefs.liveTiles();
        if (flip) liveTickCount++;
        int i = 0;
        for (Tile t : tiles.values()) {
            String key = t.app.key;
            int size = prefs.tileSize(key);
            boolean distracting = prefs.isDistracting(key);
            long until = prefs.blockedUntil(key);
            if (until > 0) {
                // Bloqueada: siempre de frente, apagada y con el tiempo que falta.
                showFace(t, false, false);
                t.mono.setAlpha(0.25f);
                t.label.setText("⊘ " + remaining(until));
                t.mark.setText(size == Prefs.SMALL ? "⊘" : "");
                i++;
                continue;
            }
            t.mono.setAlpha(1f);
            t.label.setText(t.app.lower);
            LiveData.Info info = live
                    ? liveData.infoFor(t.app.component.getPackageName(), prefs.launches(key))
                    : null;
            t.mark.setText(distracting ? "⧗" : info != null && info.badge > 0 ? String.valueOf(info.badge) : "");
            if (info == null || size == Prefs.SMALL) {
                showFace(t, false, flip);
            } else {
                t.big.setText(info.big);
                t.line.setText(info.line);
                t.line.setVisibility(info.line.isEmpty() ? View.GONE : View.VISIBLE);
                // Escalonado: no giran todos a la vez.
                if (flip && resizing == null && (liveTickCount + i) % 3 == 0) showFace(t, !t.showingBack, true);
            }
            i++;
        }
    }

    private void showFace(Tile t, boolean back, boolean animate) {
        if (t.showingBack == back) return;
        t.showingBack = back;
        Runnable swap = () -> {
            t.front.setVisibility(back ? View.GONE : View.VISIBLE);
            t.back.setVisibility(back ? View.VISIBLE : View.GONE);
        };
        if (!animate) {
            swap.run();
            return;
        }
        t.view.animate().rotationX(90).setDuration(170).withEndAction(() -> {
            swap.run();
            t.view.setRotationX(-90);
            t.view.animate().rotationX(0).setDuration(170).start();
        }).start();
    }

    private void refreshLiveData() {
        io.execute(() -> {
            liveData.refresh();
            handler.post(() -> {
                updateLiveTiles(false);
                updateClock();
            });
        });
    }

    // ---- redimensionar: mantener pulsado y deslizar ----

    private void startResize(Tile t) {
        resizing = t;
        resizeStart = prefs.tileSize(t.app.key);
        resizeMoved = false;
        resizeChanged = false;
        showFace(t, false, false);
        t.view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        t.view.getParent().requestDisallowInterceptTouchEvent(true);
        for (Tile other : tiles.values()) {
            if (other != t) other.view.animate().alpha(0.4f).setDuration(120).start();
        }
        t.view.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start();
    }

    /** Hacia la derecha/abajo agranda, hacia la izquierda/arriba achica. */
    private void onResizeMove(float dx, float dy) {
        if (Math.abs(dx) + Math.abs(dy) > dp(12)) resizeMoved = true;
        int steps = (int) ((dx + dy) / (cell + gap));
        int target = Math.max(Prefs.SMALL, Math.min(Prefs.LARGE, resizeStart + steps));
        String key = resizing.app.key;
        if (target == prefs.tileSize(key)) return;
        prefs.setTileSize(key, target);
        prefs.setHintSeen();
        resizeChanged = true;
        resizing.view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        layoutTiles(true);
    }

    private void endResize() {
        for (Tile other : tiles.values()) other.view.animate().alpha(1f).setDuration(120).start();
        boolean changed = resizeChanged;
        resizing = null;
        if (changed) {
            // Quitar la pista una vez aprendido el gesto.
            handler.post(this::renderStart);
        }
    }

    private void updateClock() {
        if (clockPixels == null) return;
        Date now = new Date();
        clockPixels.set24h(android.text.format.DateFormat.is24HourFormat(this));
        clockDate.setText(new SimpleDateFormat("EEE d 'de' MMM", new Locale("es"))
                .format(now).toLowerCase(Locale.getDefault()).replace(".", ""));
        int total = prefs.totalLaunches();
        int avoided = prefs.avoided();
        String stats = LiveData.hasUsageAccess(this)
                ? "pantalla " + LiveData.duration(liveData.screenMs / 60_000)
                : total + (total == 1 ? " apertura" : " aperturas");
        if (avoided > 0) stats += " · " + avoided + " evitados";
        clockStats.setText(stats);
    }

    /** "1 h 20 min" que faltan hasta {@code until}. */
    private static String remaining(long until) {
        long min = Math.max(1, (until - System.currentTimeMillis() + 59_999) / 60_000);
        return LiveData.duration(min);
    }

    // ------------------------------------------------------------------ clima

    private static final long WEATHER_MAX_AGE = 30 * 60 * 1000L;
    private static final int REQ_LOCATION = 7;

    private void updateWeather() {
        if (weatherTemp == null) return;
        Weather.Data d = prefs.loadWeather();
        if (prefs.weatherMode().isEmpty()) {
            weatherPixels.setScene(Weather.UNKNOWN, true);
            weatherTemp.setText("clima");
            weatherDesc.setText("");
            weatherSub.setText("toca para configurar");
        } else if (d == null) {
            weatherPixels.setScene(Weather.UNKNOWN, true);
            weatherTemp.setText("--°");
            weatherDesc.setText(weatherLoading ? "cargando…" : "sin datos");
            weatherSub.setText(prefs.weatherCity());
        } else {
            weatherPixels.setScene(Weather.scene(d.code), d.day);
            weatherTemp.setText(Math.round(d.temp) + "°");
            weatherDesc.setText(Weather.describe(d.code));
            String city = prefs.weatherCity();
            weatherSub.setText((city.isEmpty() ? "" : city + "  ·  ")
                    + "↑" + Math.round(d.max) + "°  ↓" + Math.round(d.min) + "°");
        }
    }

    private void refreshWeather(boolean force) {
        String mode = prefs.weatherMode();
        if (mode.isEmpty() || weatherLoading) return;
        Weather.Data cached = prefs.loadWeather();
        long now = System.currentTimeMillis();
        if (!force && cached != null && now - cached.time < WEATHER_MAX_AGE) return;
        // Sin internet no reintentar más de una vez cada 2 minutos.
        if (!force && now - weatherLastTry < 2 * 60 * 1000L) return;
        weatherLastTry = now;
        final boolean gps = "gps".equals(mode);
        if (gps) {
            Location l = lastLocation();
            if (l != null) prefs.setWeatherPlace("gps", l.getLatitude(), l.getLongitude());
        }
        final double lat = prefs.weatherLat(), lon = prefs.weatherLon();
        weatherLoading = true;
        updateWeather();
        io.execute(() -> {
            Weather.Data d = null;
            try {
                d = Weather.fetch(lat, lon);
            } catch (Exception ignored) {
                // Sin red: se queda el último dato guardado.
            }
            String city = gps ? cityName(lat, lon) : null;
            final Weather.Data fd = d;
            handler.post(() -> {
                weatherLoading = false;
                if (fd != null) prefs.saveWeather(fd);
                if (city != null) prefs.setWeatherCity(city);
                updateWeather();
                if (fd == null && force) toast("no se pudo actualizar el clima");
            });
        });
    }

    private void weatherSetup() {
        String[] items = {"usar mi ubicación", "escribir una ciudad", "actualizar ahora"};
        dialog().setTitle("clima").setItems(items, (d, which) -> {
            if (which == 0) useMyLocation();
            else if (which == 1) askCity();
            else refreshWeather(true);
        }).show();
    }

    private void useMyLocation() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        locateNow();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) locateNow();
        else askCity();
    }

    private void locateNow() {
        Location l = lastLocation();
        if (l != null) {
            setGpsPlace(l);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                @Override public void onLocationChanged(Location loc) { setGpsPlace(loc); }
                @Override public void onStatusChanged(String p, int s, Bundle b) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
            }, Looper.getMainLooper());
            toast("buscando tu ubicación…");
        } catch (Exception e) {
            toast("activa la ubicación o escribe una ciudad");
            askCity();
        }
    }

    private void setGpsPlace(Location l) {
        prefs.setWeatherPlace("gps", l.getLatitude(), l.getLongitude());
        prefs.setWeatherCity("");
        prefs.clearWeatherData();
        refreshWeather(true);
    }

    private Location lastLocation() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return best;
    }

    /** Nombre de la ciudad para unas coordenadas (bloqueante). */
    private String cityName(double lat, double lon) {
        try {
            List<Address> list = new Geocoder(this, new Locale("es")).getFromLocation(lat, lon, 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                if (a.getLocality() != null) return a.getLocality();
                if (a.getSubAdminArea() != null) return a.getSubAdminArea();
            }
        } catch (Exception ignored) {
            // Geocoder no disponible en algunos teléfonos.
        }
        return null;
    }

    private void askCity() {
        final EditText input = new EditText(this);
        input.setHint("ciudad");
        input.setSingleLine(true);
        input.setText(prefs.weatherCity());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        dialog().setTitle("¿de qué ciudad?").setView(box)
                .setPositiveButton("buscar", (d, w) -> {
                    final String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    io.execute(() -> {
                        Weather.Place p = null;
                        try {
                            p = Weather.geocode(name);
                        } catch (Exception ignored) {
                            // Se avisa abajo.
                        }
                        final Weather.Place fp = p;
                        handler.post(() -> {
                            if (fp == null) {
                                toast("no encontré esa ciudad (¿hay internet?)");
                                return;
                            }
                            prefs.setWeatherPlace("city", fp.lat, fp.lon);
                            prefs.setWeatherCity(fp.name);
                            prefs.clearWeatherData();
                            refreshWeather(true);
                        });
                    });
                })
                .setNegativeButton("cancelar", null).show();
    }

    // ------------------------------------------------------------------ lista de apps

    private void buildListPage() {
        listPage = new LinearLayout(this);
        listPage.setOrientation(LinearLayout.VERTICAL);
        listPage.setBackgroundColor(BLACK);
        listPage.setPadding(dp(16), dp(16), dp(16), 0);
        listPage.setVisibility(View.GONE);

        search = new EditText(this);
        search.setHint("buscar");
        search.setTextColor(WHITE);
        search.setHintTextColor(GRAY);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(outline(BLACK, WHITE, 2));
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                adapter.rebuild(s.toString());
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            AppEntry first = adapter.firstApp();
            if (first != null) requestLaunch(first);
            return true;
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.bottomMargin = dp(8);
        listPage.addView(search, sp);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.setVerticalScrollBarEnabled(false);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setBackgroundColor(BLACK);
        listView.setCacheColorHint(BLACK);
        adapter = new AppListAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            Row r = adapter.getItem(pos);
            if (r.app != null) requestLaunch(r.app);
            else showLetters();
        });
        listView.setOnItemLongClickListener((parent, view, pos, id) -> {
            Row r = adapter.getItem(pos);
            if (r.app == null) return false;
            appMenu(r.app);
            return true;
        });
        listPage.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private static final class Row {
        final String header;
        final AppEntry app;

        Row(String header, AppEntry app) {
            this.header = header;
            this.app = app;
        }
    }

    private final class AppListAdapter extends BaseAdapter {
        final List<Row> rows = new ArrayList<>();
        final Map<String, Integer> headerPos = new HashMap<>();

        void rebuild(String query) {
            String q = AppEntry.strip(query.trim().toLowerCase(Locale.getDefault()));
            rows.clear();
            headerPos.clear();
            String last = null;
            for (AppEntry a : apps) {
                if (!q.isEmpty() && !a.lower.contains(q)) continue;
                // Las apps ocultas solo aparecen si escribes al menos 3 letras de su nombre.
                if (prefs.isHidden(a.key) && q.length() < 3) continue;
                if (!a.letter.equals(last)) {
                    headerPos.put(a.letter, rows.size());
                    rows.add(new Row(a.letter, null));
                    last = a.letter;
                }
                rows.add(new Row(null, a));
            }
            notifyDataSetChanged();
        }

        AppEntry firstApp() {
            for (Row r : rows) if (r.app != null) return r.app;
            return null;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Row getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return rows.get(i).header != null ? 0 : 1; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = rows.get(i);
            if (r.header != null) {
                FrameLayout box;
                TextView letter;
                if (convert instanceof FrameLayout) {
                    box = (FrameLayout) convert;
                    letter = (TextView) box.getChildAt(0);
                } else {
                    box = new FrameLayout(MainActivity.this);
                    box.setPadding(0, dp(14), 0, dp(6));
                    letter = text("", 24, WHITE);
                    letter.setGravity(Gravity.BOTTOM | Gravity.START);
                    letter.setPadding(dp(6), 0, 0, dp(2));
                    letter.setBackground(outline(BLACK, WHITE, 2));
                    box.addView(letter, new FrameLayout.LayoutParams(dp(48), dp(48)));
                }
                letter.setText(r.header);
                return box;
            }
            TextView t;
            if (convert instanceof TextView) {
                t = (TextView) convert;
            } else {
                t = text("", 26, WHITE);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(2), dp(9), dp(2), dp(9));
            }
            long until = prefs.blockedUntil(r.app.key);
            String name = r.app.label.toLowerCase(Locale.getDefault());
            // Bloqueadas casi invisibles; con pausa en gris: menos llamativas.
            t.setText(until > 0 ? name + "  ⊘ " + remaining(until) : name);
            t.setTextColor(until > 0 ? DIM : prefs.isDistracting(r.app.key) ? GRAY : WHITE);
            return t;
        }
    }

    /** Cuadrícula de letras para saltar rápido, como en Windows Phone. */
    private void showLetters() {
        hideKeyboard();
        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setSelector(android.R.color.transparent);
        grid.setPadding(dp(16), dp(24), dp(16), dp(24));
        grid.setClipToPadding(false);
        final int cell = (screenW - dp(32) - dp(8) * 3) / 4;
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return LETTERS.length(); }
            @Override public Object getItem(int i) { return LETTERS.substring(i, i + 1); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                String l = LETTERS.substring(i, i + 1);
                boolean has = adapter.headerPos.containsKey(l);
                TextView t = text(l, 30, has ? WHITE : DIM);
                t.setGravity(Gravity.BOTTOM | Gravity.START);
                t.setPadding(dp(8), 0, 0, dp(4));
                t.setBackground(outline(BLACK, has ? WHITE : DIM, 2));
                t.setLayoutParams(new GridView.LayoutParams(cell, cell));
                return t;
            }
        });
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Integer target = adapter.headerPos.get(LETTERS.substring(pos, pos + 1));
            if (target == null) return;
            hideOverlay(false);
            listView.setSelection(target);
        });
        showOverlay(grid, null);
    }

    // ------------------------------------------------------------------ pausa anti-impulso

    private void showPause(AppEntry a) {
        hideKeyboard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));

        box.addView(text("respira.", 54, WHITE));
        TextView sub = text("vas a abrir " + a.lower + ".", 20, WHITE);
        sub.setPadding(0, dp(12), 0, 0);
        box.addView(sub);
        int n = prefs.launches(a.key);
        TextView times = text(n == 0 ? "aún no la has abierto hoy."
                : "hoy ya la abriste " + n + (n == 1 ? " vez." : " veces."), 16, GRAY);
        times.setPadding(0, dp(6), 0, 0);
        box.addView(times);
        TextView q = text("¿es una decisión o un impulso?", 16, GRAY);
        q.setPadding(0, dp(2), 0, dp(36));
        box.addView(q);

        final TextView open = button("");
        final TextView no = button("mejor no");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bp.bottomMargin = dp(12);
        box.addView(no, bp);
        box.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        final int[] left = {prefs.pauseSeconds()};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (left[0] > 0) {
                    open.setText("abrir (" + left[0] + ")");
                    open.setEnabled(false);
                    open.setAlpha(0.35f);
                    left[0]--;
                    handler.postDelayed(this, 1000);
                } else {
                    open.setText("abrir igualmente");
                    open.setEnabled(true);
                    open.setAlpha(1f);
                }
            }
        };
        tick.run();
        open.setOnClickListener(v -> {
            handler.removeCallbacks(tick);
            hideOverlay(false);
            launch(a);
        });
        no.setOnClickListener(v -> hideOverlay(true));

        showOverlay(box, () -> {
            handler.removeCallbacks(tick);
            prefs.countAvoided();
            updateClock();
            showStart();
        });
    }

    // ------------------------------------------------------------------ bloqueo por tiempo

    private static final String[] BLOCK_LABELS = {
            "15 minutos", "30 minutos", "1 hora", "2 horas", "4 horas", "8 horas", "hasta mañana (6:00)"};
    private static final long[] BLOCK_MINUTES = {15, 30, 60, 120, 240, 480, -1};
    private static final int[] UNBLOCK_WAIT_OPTIONS = {30, 60, 120, 300, 600, 1800};

    private void chooseBlock(AppEntry a) {
        dialog().setTitle("bloquear " + a.lower).setItems(BLOCK_LABELS, (d, which) -> {
            long until;
            if (BLOCK_MINUTES[which] > 0) {
                until = System.currentTimeMillis() + BLOCK_MINUTES[which] * 60_000;
            } else {
                Calendar c = Calendar.getInstance();
                if (c.get(Calendar.HOUR_OF_DAY) >= 6) c.add(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 6);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                until = c.getTimeInMillis();
            }
            prefs.blockUntil(a.key, until);
            toast(a.lower + " bloqueada hasta las " + timeOf(until));
            refresh();
        }).show();
    }

    /**
     * Pantalla de app bloqueada. Para desbloquear antes de tiempo hay que esperar
     * mirándola los segundos configurados; si sales, la cuenta empieza de cero.
     */
    private void showBlocked(AppEntry a, boolean fromMenu) {
        hideKeyboard();
        final long until = prefs.blockedUntil(a.key);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));

        box.addView(text("bloqueada.", 54, WHITE));
        TextView sub = text(a.lower + " vuelve en " + remaining(until) + " (a las " + timeOf(until) + ").", 20, WHITE);
        sub.setPadding(0, dp(12), 0, 0);
        box.addView(sub);
        final TextView note = text("¿de verdad la necesitas ahora?", 16, GRAY);
        note.setPadding(0, dp(6), 0, dp(36));
        box.addView(note);

        final TextView ok = button(fromMenu ? "mantener bloqueada" : "vale, más tarde");
        final TextView early = button("desbloquear antes");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bp.bottomMargin = dp(12);
        box.addView(ok, bp);
        box.addView(early, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        final int wait = prefs.unblockWaitSeconds();
        final int[] left = {-1};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (left[0] > 0) {
                    early.setText("desbloquear (" + clockText(left[0]) + ")");
                    early.setEnabled(false);
                    early.setAlpha(0.35f);
                    left[0]--;
                    handler.postDelayed(this, 1000);
                } else {
                    left[0] = 0;
                    early.setText(fromMenu ? "desbloquear" : "desbloquear y abrir");
                    early.setEnabled(true);
                    early.setAlpha(1f);
                }
            }
        };
        early.setOnClickListener(v -> {
            if (left[0] < 0) {
                // Primer toque: empieza la espera obligatoria.
                left[0] = wait;
                note.setText("espera " + clockText(wait) + " sin salir de esta pantalla. si sales, se reinicia.");
                tick.run();
            } else if (left[0] == 0) {
                prefs.unblock(a.key);
                hideOverlay(false);
                refresh();
                if (!fromMenu) launch(a);
            }
        });
        ok.setOnClickListener(v -> hideOverlay(true));

        showOverlay(box, () -> {
            handler.removeCallbacks(tick);
            prefs.countAvoided();
            updateClock();
        });
        if (fromMenu) early.performClick();
    }

    private static String clockText(int seconds) {
        return seconds >= 60 ? (seconds / 60) + ":" + String.format(Locale.US, "%02d", seconds % 60) : seconds + " s";
    }

    private String timeOf(long millis) {
        return android.text.format.DateFormat.getTimeFormat(this).format(new Date(millis));
    }

    // ------------------------------------------------------------------ menús

    private void appMenu(AppEntry a) {
        final boolean pinned = prefs.isPinned(a.key);
        final boolean blocked = prefs.blockedUntil(a.key) > 0;
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (pinned) {
            items.add("desanclar de inicio");
            actions.add(() -> { prefs.togglePinned(a.key); refresh(); });
            items.add(prefs.isBlackTile(a.key) ? "color del mosaico: negro → blanco" : "color del mosaico: blanco → negro");
            actions.add(() -> { prefs.toggleBlackTile(a.key); refresh(); });
        } else {
            items.add("anclar a inicio (mosaico blanco)");
            actions.add(() -> pin(a, false));
            items.add("anclar a inicio (mosaico negro)");
            actions.add(() -> pin(a, true));
        }
        addCommonActions(a, blocked, items, actions);
        items.add(prefs.isHidden(a.key) ? "mostrar en la lista" : "ocultar de la lista");
        actions.add(() -> { prefs.toggleHidden(a.key); refresh(); });
        items.add("información de la app");
        actions.add(() -> openAppInfo(a));
        items.add("desinstalar");
        actions.add(() -> uninstall(a));
        showActions(a.lower, items, actions);
    }

    private void tileMenu(AppEntry a) {
        final int size = prefs.tileSize(a.key);
        final boolean blocked = prefs.blockedUntil(a.key) > 0;
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(prefs.isBlackTile(a.key) ? "color: negro → blanco" : "color: blanco → negro");
        actions.add(() -> prefs.toggleBlackTile(a.key));
        items.add("tamaño: " + SIZE_NAMES[size] + " → " + SIZE_NAMES[(size + 1) % SIZE_NAMES.length]);
        actions.add(() -> prefs.setTileSize(a.key, (size + 1) % SIZE_NAMES.length));
        addCommonActions(a, blocked, items, actions);
        items.add("mover antes");
        actions.add(() -> move(a.key, -1));
        items.add("mover después");
        actions.add(() -> move(a.key, 1));
        items.add("desanclar");
        actions.add(() -> prefs.togglePinned(a.key));
        showActions(a.lower, items, actions);
    }

    /** Bloqueo y pausa: iguales en el menú de la lista y en el del mosaico. */
    private void addCommonActions(AppEntry a, boolean blocked, List<String> items, List<Runnable> actions) {
        if (blocked) {
            items.add("bloqueada " + remaining(prefs.blockedUntil(a.key)) + " · desbloquear antes");
            actions.add(() -> showBlocked(a, true));
        } else {
            items.add("bloquear durante…");
            actions.add(() -> chooseBlock(a));
        }
        items.add(prefs.isDistracting(a.key) ? "quitar pausa" : "poner pausa (app distractora)");
        actions.add(() -> prefs.toggleDistracting(a.key));
    }

    private void showActions(String title, List<String> items, List<Runnable> actions) {
        dialog().setTitle(title).setItems(items.toArray(new String[0]), (d, which) -> {
            actions.get(which).run();
            refresh();
        }).show();
    }

    private void pin(AppEntry a, boolean black) {
        if (!prefs.isPinned(a.key)) prefs.togglePinned(a.key);
        if (prefs.isBlackTile(a.key) != black) prefs.toggleBlackTile(a.key);
        toast("anclada a inicio");
    }

    private void move(String key, int delta) {
        List<String> l = prefs.pinned();
        int i = l.indexOf(key);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return;
        Collections.swap(l, i, j);
        prefs.setPinned(l);
    }

    private void showSettings() {
        int secs = prefs.pauseSeconds();
        boolean usage = LiveData.hasUsageAccess(this);
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add("pausa antes de apps distractoras: " + (secs == 0 ? "desactivada" : secs + " s"));
        actions.add(this::choosePause);
        items.add("espera para desbloquear antes: " + clockText(prefs.unblockWaitSeconds()));
        actions.add(this::chooseUnblockWait);
        items.add("mosaicos dinámicos: " + (prefs.liveTiles() ? "activados" : "desactivados"));
        actions.add(() -> {
            prefs.setLiveTiles(!prefs.liveTiles());
            refresh();
        });
        items.add("tiempo de uso en mosaicos: " + (usage ? "✓ permitido" : "dar acceso"));
        actions.add(() -> openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        items.add("uso de hoy");
        actions.add(this::showStats);
        items.add("apps ocultas (" + countHidden() + ")");
        actions.add(this::showHidden);
        items.add("elegir launcher predeterminado");
        actions.add(() -> openSettings(Settings.ACTION_HOME_SETTINGS));
        items.add("ajustes del teléfono");
        actions.add(() -> openSettings(Settings.ACTION_SETTINGS));
        dialog().setTitle("ajustes").setItems(items.toArray(new String[0]),
                (d, which) -> actions.get(which).run()).show();
    }

    private void chooseUnblockWait() {
        String[] labels = new String[UNBLOCK_WAIT_OPTIONS.length];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            int s = UNBLOCK_WAIT_OPTIONS[i];
            labels[i] = s < 60 ? s + " segundos" : (s / 60) + (s == 60 ? " minuto" : " minutos");
            if (s == prefs.unblockWaitSeconds()) checked = i;
        }
        dialog().setTitle("espera para desbloquear").setSingleChoiceItems(labels, checked, (d, which) -> {
            prefs.setUnblockWaitSeconds(UNBLOCK_WAIT_OPTIONS[which]);
            d.dismiss();
        }).show();
    }

    private void choosePause() {
        String[] labels = new String[PAUSE_OPTIONS.length];
        int checked = 0;
        for (int i = 0; i < PAUSE_OPTIONS.length; i++) {
            labels[i] = PAUSE_OPTIONS[i] == 0 ? "sin pausa" : PAUSE_OPTIONS[i] + " segundos";
            if (PAUSE_OPTIONS[i] == prefs.pauseSeconds()) checked = i;
        }
        dialog().setTitle("pausa").setSingleChoiceItems(labels, checked, (d, which) -> {
            prefs.setPauseSeconds(PAUSE_OPTIONS[which]);
            d.dismiss();
        }).show();
    }

    private int countHidden() {
        int n = 0;
        for (String k : prefs.hidden()) if (byKey.containsKey(k)) n++;
        return n;
    }

    private void showHidden() {
        final List<AppEntry> list = new ArrayList<>();
        for (AppEntry a : apps) if (prefs.isHidden(a.key)) list.add(a);
        if (list.isEmpty()) {
            dialog().setTitle("apps ocultas")
                    .setMessage("no hay apps ocultas.\n\nmantén pulsada una app en la lista y elige "
                            + "\"ocultar de la lista\". solo aparecerá si la buscas escribiendo su nombre.")
                    .setPositiveButton("vale", null).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).lower;
        dialog().setTitle("toca para volver a mostrar").setItems(names, (d, which) -> {
            prefs.toggleHidden(list.get(which).key);
            refresh();
        }).show();
    }

    private void showStats() {
        Map<String, Integer> counts = prefs.allLaunches();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        Collections.sort(sorted, (x, y) -> Integer.compare(y.getValue(), x.getValue()));
        StringBuilder sb = new StringBuilder();
        if (LiveData.hasUsageAccess(this)) {
            sb.append("tiempo de pantalla: ").append(LiveData.duration(liveData.screenMs / 60_000)).append('\n');
        }
        sb.append("aperturas: ").append(prefs.totalLaunches()).append('\n');
        sb.append("impulsos evitados: ").append(prefs.avoided()).append("\n\n");
        int shown = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            AppEntry a = byKey.get(e.getKey());
            if (a == null) continue;
            sb.append(e.getValue()).append("  ·  ").append(a.lower).append('\n');
            if (++shown == 15) break;
        }
        if (shown == 0) sb.append("todavía no abriste ninguna app hoy.");
        dialog().setTitle("hoy").setMessage(sb.toString().trim()).setPositiveButton("vale", null).show();
    }

    private void openAppInfo(AppEntry a) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.component.getPackageName()));
        startSafely(i);
    }

    private void uninstall(AppEntry a) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.component.getPackageName()));
        startSafely(i);
    }

    private void openSettings(String action) {
        if (!startSafely(new Intent(action)) && !Settings.ACTION_SETTINGS.equals(action)) {
            startSafely(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean startSafely(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "no disponible en este teléfono", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void refresh() {
        renderStart();
        adapter.rebuild(search.getText().toString());
    }

    // ------------------------------------------------------------------ navegación

    private void showList() {
        if (onList) return;
        onList = true;
        listPage.setVisibility(View.VISIBLE);
        listPage.setTranslationX(screenW);
        listPage.animate().translationX(0).setDuration(220).start();
        startPage.animate().translationX(-screenW * 0.35f).alpha(0f).setDuration(220).start();
        listView.setSelection(0);
    }

    private void showStart() {
        if (!onList) return;
        onList = false;
        hideKeyboard();
        if (search.length() > 0) search.setText("");
        startPage.animate().translationX(0).alpha(1f).setDuration(220).start();
        listPage.animate().translationX(screenW).setDuration(220)
                .withEndAction(() -> {
                    if (!onList) listPage.setVisibility(View.GONE);
                }).start();
    }

    private void showOverlay(View content, Runnable onCancel) {
        overlay.removeAllViews();
        overlay.addView(content, match());
        overlayCancel = onCancel;
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(150).start();
    }

    /** @param cancelled true si el usuario se echó atrás (cuenta como impulso evitado en la pausa). */
    private void hideOverlay(boolean cancelled) {
        if (overlay.getVisibility() != View.VISIBLE) return;
        Runnable cancel = overlayCancel;
        overlayCancel = null;
        overlay.setVisibility(View.GONE);
        overlay.removeAllViews();
        if (cancelled && cancel != null) cancel.run();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        search.clearFocus();
    }

    // ------------------------------------------------------------------ ayudantes de vista

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(light);
        return t;
    }

    private TextView button(String s) {
        TextView b = text(s, 18, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(outline(BLACK, WHITE, 2));
        b.setClickable(true);
        addTilt(b);
        return b;
    }

    private GradientDrawable outline(int fill, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private GradientDrawable solid(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        return g;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable roundOutline() {
        GradientDrawable g = outline(BLACK, WHITE, 2);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** Efecto de "inclinación" al pulsar, como los mosaicos de Windows Phone. */
    private void addTilt(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private LinearLayout.LayoutParams lp(int w, int h, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.bottomMargin = bottom;
        return p;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
