package com.juan.monocromo;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private TextView clockTime;
    private TextView clockDate;
    private TextView clockStats;

    private GestureDetector gestures;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 10_000);
        }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
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
        renderStart();
        adapter.rebuild(search.getText().toString());
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
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
        if (overlay.getVisibility() != View.VISIBLE) gestures.onTouchEvent(ev);
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
            if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
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

    private void requestLaunch(AppEntry a) {
        if (prefs.isDistracting(a.key) && prefs.pauseSeconds() > 0) showPause(a);
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
        int gap = dp(8);
        int avail = screenW - dp(24);
        int half = (avail - gap) / 2;

        // Mosaico de reloj: hora, fecha y el uso de hoy.
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.BOTTOM);
        clock.setPadding(dp(14), dp(10), dp(14), dp(12));
        clock.setBackground(outline(BLACK, WHITE, 2));
        clockTime = text("", 60, WHITE);
        clockTime.setIncludeFontPadding(false);
        clockDate = text("", 17, WHITE);
        clockStats = text("", 13, GRAY);
        clockStats.setPadding(0, dp(4), 0, 0);
        clock.addView(clockTime);
        clock.addView(clockDate);
        clock.addView(clockStats);
        clock.setOnClickListener(v -> showStats());
        clock.setOnLongClickListener(v -> {
            showSettings();
            return true;
        });
        addTilt(clock);
        startContent.addView(clock, lp(avail, half, gap));
        updateClock();

        LinearLayout row = null;
        int inRow = 0;
        boolean any = false;
        for (String key : prefs.pinned()) {
            AppEntry a = byKey.get(key);
            if (a == null) continue;
            any = true;
            if (prefs.isWide(key)) {
                row = null;
                inRow = 0;
                startContent.addView(tile(a, true), lp(avail, half, gap));
            } else {
                if (row == null) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    startContent.addView(row, lp(avail, half, gap));
                }
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(half, half);
                if (inRow == 0) p.rightMargin = gap;
                row.addView(tile(a, false), p);
                if (++inRow == 2) {
                    row = null;
                    inRow = 0;
                }
            }
        }

        if (!any) {
            TextView hint = text("desliza a la izquierda para ver tus apps.\n"
                    + "mantén pulsada una app para anclarla aquí.", 16, GRAY);
            hint.setPadding(dp(4), dp(12), dp(4), dp(12));
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
        View spacer = new View(this);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView all = text("todas las apps", 16, WHITE);
        all.setPadding(0, 0, dp(12), 0);
        footer.addView(all);
        TextView arrow = text("→", 22, WHITE);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(roundOutline());
        footer.addView(arrow, new LinearLayout.LayoutParams(dp(44), dp(44)));
        footer.setOnClickListener(v -> showList());
        dots.setClickable(true);
        startContent.addView(footer);
    }

    private View tile(AppEntry a, boolean wide) {
        FrameLayout t = new FrameLayout(this);
        t.setBackgroundColor(WHITE);

        TextView mono = text(a.initial(), wide ? 52 : 48, BLACK);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        t.addView(mono, mp);

        TextView label = text(a.lower, 13, BLACK);
        label.setTypeface(Typeface.DEFAULT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams lpLabel = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lpLabel.setMargins(dp(8), 0, dp(8), dp(6));
        t.addView(label, lpLabel);

        if (prefs.isDistracting(a.key)) {
            // Un pequeño reloj de arena recuerda que esta app tiene pausa.
            TextView mark = text("⧗", 14, BLACK);
            FrameLayout.LayoutParams mk = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            mk.setMargins(0, dp(4), dp(8), 0);
            t.addView(mark, mk);
        }

        t.setOnClickListener(v -> requestLaunch(a));
        t.setOnLongClickListener(v -> {
            tileMenu(a);
            return true;
        });
        addTilt(t);
        return t;
    }

    private void updateClock() {
        if (clockTime == null) return;
        Date now = new Date();
        clockTime.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        clockDate.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("es"))
                .format(now).toLowerCase(Locale.getDefault()));
        int total = prefs.totalLaunches();
        int avoided = prefs.avoided();
        clockStats.setText("hoy: " + total + (total == 1 ? " apertura" : " aperturas")
                + (avoided > 0 ? "  ·  " + avoided + (avoided == 1 ? " impulso evitado" : " impulsos evitados") : ""));
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
            t.setText(r.app.label.toLowerCase(Locale.getDefault()));
            // Las apps con pausa se ven en gris: menos llamativas.
            t.setTextColor(prefs.isDistracting(r.app.key) ? GRAY : WHITE);
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

    // ------------------------------------------------------------------ menús

    private void appMenu(AppEntry a) {
        final boolean pinned = prefs.isPinned(a.key);
        final boolean distracting = prefs.isDistracting(a.key);
        final boolean hidden = prefs.isHidden(a.key);
        String[] items = {
                pinned ? "desanclar de inicio" : "anclar a inicio",
                distracting ? "quitar pausa" : "poner pausa (app distractora)",
                hidden ? "mostrar en la lista" : "ocultar de la lista",
                "información de la app",
                "desinstalar"
        };
        dialog().setTitle(a.lower).setItems(items, (d, which) -> {
            switch (which) {
                case 0: prefs.togglePinned(a.key); renderStart(); break;
                case 1: prefs.toggleDistracting(a.key); refresh(); break;
                case 2: prefs.toggleHidden(a.key); refresh(); break;
                case 3: openAppInfo(a); break;
                case 4: uninstall(a); break;
                default: break;
            }
        }).show();
    }

    private void tileMenu(AppEntry a) {
        final boolean distracting = prefs.isDistracting(a.key);
        String[] items = {
                prefs.isWide(a.key) ? "hacer mediano" : "hacer ancho",
                "mover antes",
                "mover después",
                distracting ? "quitar pausa" : "poner pausa (app distractora)",
                "desanclar"
        };
        dialog().setTitle(a.lower).setItems(items, (d, which) -> {
            switch (which) {
                case 0: prefs.toggleWide(a.key); break;
                case 1: move(a.key, -1); break;
                case 2: move(a.key, 1); break;
                case 3: prefs.toggleDistracting(a.key); break;
                case 4: prefs.togglePinned(a.key); break;
                default: break;
            }
            refresh();
        }).show();
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
        String[] items = {
                "pausa antes de apps distractoras: " + (secs == 0 ? "desactivada" : secs + " s"),
                "uso de hoy",
                "apps ocultas (" + countHidden() + ")",
                "elegir launcher predeterminado",
                "ajustes del teléfono"
        };
        dialog().setTitle("ajustes").setItems(items, (d, which) -> {
            switch (which) {
                case 0: choosePause(); break;
                case 1: showStats(); break;
                case 2: showHidden(); break;
                case 3: openSettings(Settings.ACTION_HOME_SETTINGS); break;
                case 4: openSettings(Settings.ACTION_SETTINGS); break;
                default: break;
            }
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
