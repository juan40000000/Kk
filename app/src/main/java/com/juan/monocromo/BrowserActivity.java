package com.juan.monocromo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Calamuchita: navegador minimalista con el motor de Firefox (GeckoView).
 * Barra abajo como Safari, pestañas y favoritos en mosaicos blanco y negro
 * como Windows Phone, y nada más: pensado para entrar, buscar y salir.
 */
public class BrowserActivity extends Activity {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int GRAY = 0xFF8A8A8A;
    private static final int DIM = 0xFF3A3A3A;
    private static final String BLANK = "about:blank";
    private static final String[] ENGINES = {"duckduckgo", "google"};

    private static GeckoRuntime runtime;

    private static final class Tab {
        String url = BLANK;
        String title = "";
        GeckoSession session;
        boolean canBack;
        boolean canForward;
        int progress = 100;
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int current;

    private SharedPreferences prefs;
    private Typeface light;

    private FrameLayout root;
    private GeckoView gecko;
    private ScrollView startPage;
    private LinearLayout favGrid;
    private LinearLayout bar;
    private View progress;
    private EditText address;
    private TextView tabsButton;
    private TextView backButton;
    private FrameLayout overlay;
    private boolean fullScreen;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("calamuchita", Context.MODE_PRIVATE);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);

        if (runtime == null) {
            runtime = GeckoRuntime.create(getApplicationContext());
            runtime.getSettings().setPreferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        root.setFitsSystemWindows(true);
        root.setFocusableInTouchMode(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, match());

        FrameLayout page = new FrameLayout(this);
        gecko = new GeckoView(this);
        page.addView(gecko, match());
        startPage = buildStartPage();
        page.addView(startPage, match());
        column.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        progress = new View(this);
        progress.setBackgroundColor(WHITE);
        progress.setPivotX(0);
        column.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        bar = buildBar();
        column.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(BLACK);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, match());
        setContentView(root);

        restoreTabs();
        if (!handleIntent(getIntent())) switchTo(current);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveTabs();
    }

    @Override
    public void onBackPressed() {
        Tab t = tab();
        if (fullScreen && t.session != null) t.session.exitFullScreen();
        else if (overlay.getVisibility() == View.VISIBLE) overlay.setVisibility(View.GONE);
        else if (address.hasFocus()) stopEditing();
        else if (t.canBack && t.session != null) t.session.goBack();
        else if (!isBlank(t.url)) loadInCurrent(BLANK);
        else super.onBackPressed();
    }

    /** Enlaces abiertos desde otras apps: cada uno en una pestaña nueva. */
    private boolean handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        openInNewTab(intent.getData().toString());
        return true;
    }

    // ------------------------------------------------------------------ pestañas

    private Tab tab() {
        return tabs.get(current);
    }

    private void restoreTabs() {
        String saved = prefs.getString("tabs", "");
        for (String url : saved.split("\n")) {
            if (url.trim().isEmpty()) continue;
            Tab t = new Tab();
            t.url = url.trim();
            tabs.add(t);
        }
        if (tabs.isEmpty()) tabs.add(new Tab());
        current = Math.max(0, Math.min(tabs.size() - 1, prefs.getInt("tab_index", 0)));
    }

    private void saveTabs() {
        StringBuilder sb = new StringBuilder();
        for (Tab t : tabs) sb.append(t.url).append('\n');
        prefs.edit().putString("tabs", sb.toString()).putInt("tab_index", current).apply();
    }

    /** Abre la sesión de Gecko de una pestaña la primera vez que se usa. */
    private GeckoSession ensureSession(Tab t) {
        if (t.session != null) return t.session;
        GeckoSession s = new GeckoSession();
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                t.url = url;
                t.progress = 5;
                if (t == tab()) updateChrome();
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                t.progress = 100;
                if (t == tab()) updateChrome();
            }

            @Override
            public void onProgressChange(GeckoSession session, int value) {
                t.progress = value;
                if (t == tab()) updateProgress();
            }
        });
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                t.title = title == null ? "" : title;
            }

            @Override
            public void onFullScreen(GeckoSession session, boolean full) {
                setFullScreen(full);
            }
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean value) {
                t.canBack = value;
                if (t == tab()) updateChrome();
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean value) {
                t.canForward = value;
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                // Enlaces "en ventana nueva": se abren en una pestaña nueva.
                root.post(() -> openInNewTab(uri));
                return null;
            }
        });
        s.open(runtime);
        t.session = s;
        if (!isBlank(t.url)) s.loadUri(t.url);
        return s;
    }

    private void switchTo(int index) {
        current = index;
        Tab t = tab();
        GeckoSession s = ensureSession(t);
        if (gecko.getSession() != s) {
            gecko.releaseSession();
            gecko.setSession(s);
        }
        overlay.setVisibility(View.GONE);
        updateChrome();
    }

    private void openInNewTab(String url) {
        Tab t = new Tab();
        t.url = url;
        tabs.add(t);
        switchTo(tabs.size() - 1);
    }

    private void newTab() {
        openInNewTab(BLANK);
        startEditing();
    }

    private void closeTab(int index) {
        Tab t = tabs.remove(index);
        if (gecko.getSession() == t.session) gecko.releaseSession();
        if (t.session != null) t.session.close();
        if (tabs.isEmpty()) tabs.add(new Tab());
        if (current >= tabs.size()) current = tabs.size() - 1;
        else if (index < current) current--;
        switchTo(current);
    }

    private void loadInCurrent(String url) {
        Tab t = tab();
        t.url = url;
        t.title = "";
        ensureSession(t).loadUri(url);
        updateChrome();
    }

    // ------------------------------------------------------------------ barra (abajo, como Safari)

    private LinearLayout buildBar() {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setBackgroundColor(BLACK);

        backButton = barButton("‹", 30);
        backButton.setOnClickListener(v -> onBackPressed());
        b.addView(backButton, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(WHITE);
        address.setHintTextColor(GRAY);
        address.setHint("buscar o escribir dirección");
        address.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        address.setGravity(Gravity.CENTER);
        address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setBackground(outline(BLACK, WHITE, 1));
        address.setPadding(dp(10), 0, dp(10), 0);
        address.setOnFocusChangeListener((v, focus) -> {
            address.setGravity(focus ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
            if (focus) {
                String url = tab().url;
                address.setText(isBlank(url) ? "" : url);
                address.selectAll();
            } else {
                updateChrome();
            }
        });
        address.setOnEditorActionListener((v, id, e) -> {
            boolean go = id == EditorInfo.IME_ACTION_GO
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            String text = address.getText().toString().trim();
            if (!text.isEmpty()) loadInCurrent(toUrl(text));
            stopEditing();
            return true;
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        ap.setMargins(dp(4), 0, dp(4), 0);
        b.addView(address, ap);

        tabsButton = barButton("1", 15);
        tabsButton.setBackground(outline(BLACK, WHITE, 2));
        tabsButton.setOnClickListener(v -> showTabs());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(32), dp(32));
        tp.setMargins(dp(6), 0, dp(6), 0);
        b.addView(tabsButton, tp);

        TextView menu = barButton("•••", 14);
        menu.setOnClickListener(v -> showMenu());
        b.addView(menu, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        return b;
    }

    private void updateChrome() {
        Tab t = tab();
        boolean blank = isBlank(t.url);
        startPage.setVisibility(blank ? View.VISIBLE : View.GONE);
        if (blank) renderFavorites();
        if (!address.hasFocus()) address.setText(blank ? "" : displayUrl(t.url));
        tabsButton.setText(String.valueOf(tabs.size()));
        backButton.setAlpha(t.canBack || !blank ? 1f : 0.3f);
        updateProgress();
    }

    private void updateProgress() {
        int p = tab().progress;
        progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
        progress.setScaleX(Math.max(0.03f, p / 100f));
    }

    private void startEditing() {
        address.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }

    private void stopEditing() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        root.requestFocus();
    }

    private void setFullScreen(boolean full) {
        fullScreen = full;
        bar.setVisibility(full ? View.GONE : View.VISIBLE);
        progress.setVisibility(full ? View.GONE : View.INVISIBLE);
        if (full) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void showMenu() {
        final Tab t = tab();
        final boolean blank = isBlank(t.url);
        final boolean fav = !blank && favIndex(t.url) >= 0;
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add("nueva pestaña");
        actions.add(this::newTab);
        if (!blank) {
            items.add("recargar");
            actions.add(() -> ensureSession(t).reload());
            if (t.canForward) {
                items.add("adelante ›");
                actions.add(() -> ensureSession(t).goForward());
            }
            items.add(fav ? "quitar de favoritos" : "añadir a favoritos");
            actions.add(() -> {
                if (fav) removeFavorite(favIndex(t.url));
                else addFavorite(t.title.isEmpty() ? displayUrl(t.url) : t.title, t.url);
            });
            items.add("compartir enlace");
            actions.add(() -> startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.url), "compartir")));
        }
        items.add("buscar con: " + engine());
        actions.add(() -> {
            String next = ENGINES[(Arrays.asList(ENGINES).indexOf(engine()) + 1) % ENGINES.length];
            prefs.edit().putString("engine", next).apply();
            toast("buscador: " + next);
        });
        items.add("cerrar pestaña");
        actions.add(() -> closeTab(current));
        dialog().setTitle(blank ? "calamuchita" : displayUrl(t.url))
                .setItems(items.toArray(new String[0]), (d, w) -> actions.get(w).run()).show();
    }

    // ------------------------------------------------------------------ pestañas en mosaico

    private void showTabs() {
        stopEditing();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(20), dp(12), dp(20));
        scroll.addView(col);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("pestañas", 34, WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = text("listo", 16, WHITE);
        done.setPadding(dp(12), dp(8), dp(4), dp(8));
        done.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        header.addView(done);
        header.setPadding(dp(4), 0, 0, dp(12));
        col.addView(header);

        int gap = dp(8);
        int size = (getResources().getDisplayMetrics().widthPixels - dp(24) - gap) / 2;
        LinearLayout row = null;
        for (int i = 0; i <= tabs.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
                rp.bottomMargin = gap;
                col.addView(row, rp);
            }
            View tile = i < tabs.size() ? tabTile(i) : newTabTile();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            if (i % 2 == 0) p.rightMargin = gap;
            row.addView(tile, p);
        }

        overlay.removeAllViews();
        overlay.addView(scroll, match());
        overlay.setVisibility(View.VISIBLE);
    }

    /** La pestaña actual es blanca; las demás, negras. */
    private View tabTile(int index) {
        Tab t = tabs.get(index);
        boolean active = index == current;
        int fg = active ? BLACK : WHITE;
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(active ? solid(WHITE) : outline(BLACK, WHITE, 2));

        boolean blank = isBlank(t.url);
        TextView title = text(blank ? "nueva pestaña" : (t.title.isEmpty() ? displayUrl(t.url) : t.title), 17, fg);
        title.setMaxLines(4);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        tp.setMargins(dp(10), dp(30), dp(10), 0);
        tile.addView(title, tp);

        TextView domain = text(blank ? "" : displayUrl(t.url), 12, fg);
        domain.setTypeface(Typeface.DEFAULT);
        domain.setSingleLine(true);
        domain.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams dpl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        dpl.setMargins(dp(10), 0, dp(10), dp(8));
        tile.addView(domain, dpl);

        TextView close = text("✕", 18, fg);
        close.setPadding(dp(12), dp(4), dp(10), dp(8));
        close.setOnClickListener(v -> {
            closeTab(index);
            showTabs();
        });
        tile.addView(close, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        tile.setOnClickListener(v -> switchTo(index));
        return tile;
    }

    private View newTabTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(outline(BLACK, DIM, 2));
        tile.addView(text("+", 48, WHITE));
        tile.addView(text("nueva pestaña", 13, GRAY));
        tile.setOnClickListener(v -> newTab());
        return tile;
    }

    // ------------------------------------------------------------------ página de inicio y favoritos

    private ScrollView buildStartPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BLACK);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(48), dp(16), dp(24));
        scroll.addView(col);

        TextView name = text("calamuchita", 42, WHITE);
        col.addView(name);
        TextView sub = text("busca, lee y sal.", 15, GRAY);
        sub.setPadding(dp(2), 0, 0, dp(28));
        col.addView(sub);
        name.setOnClickListener(v -> startEditing());
        sub.setOnClickListener(v -> startEditing());

        TextView favTitle = text("favoritos", 14, GRAY);
        favTitle.setPadding(dp(2), 0, 0, dp(8));
        col.addView(favTitle);
        favGrid = new LinearLayout(this);
        favGrid.setOrientation(LinearLayout.VERTICAL);
        col.addView(favGrid);
        return scroll;
    }

    /** Favoritos guardados como "título|url|negro" por línea. */
    private List<String[]> favorites() {
        String saved = prefs.getString("favs", null);
        if (saved == null) {
            saved = "google|https://www.google.com|0\n"
                    + "wikipedia|https://es.wikipedia.org|1\n"
                    + "mercado libre|https://www.mercadolibre.com.ar|0\n";
        }
        List<String[]> out = new ArrayList<>();
        for (String line : saved.split("\n")) {
            String[] parts = line.split("\\|");
            if (parts.length >= 2) out.add(new String[]{parts[0], parts[1], parts.length > 2 ? parts[2] : "0"});
        }
        return out;
    }

    private void saveFavorites(List<String[]> favs) {
        StringBuilder sb = new StringBuilder();
        for (String[] f : favs) sb.append(f[0].replace("|", " ")).append('|').append(f[1]).append('|').append(f[2]).append('\n');
        prefs.edit().putString("favs", sb.toString()).apply();
    }

    private int favIndex(String url) {
        List<String[]> favs = favorites();
        for (int i = 0; i < favs.size(); i++) if (sameSite(favs.get(i)[1], url)) return i;
        return -1;
    }

    private void addFavorite(String title, String url) {
        List<String[]> favs = favorites();
        favs.add(new String[]{title.toLowerCase(Locale.getDefault()), url, favs.size() % 2 == 0 ? "0" : "1"});
        saveFavorites(favs);
        toast("añadido a favoritos");
        updateChrome();
    }

    private void removeFavorite(int index) {
        List<String[]> favs = favorites();
        if (index < 0 || index >= favs.size()) return;
        favs.remove(index);
        saveFavorites(favs);
        updateChrome();
    }

    private void renderFavorites() {
        favGrid.removeAllViews();
        List<String[]> favs = favorites();
        int gap = dp(8);
        int size = (getResources().getDisplayMetrics().widthPixels - dp(32) - gap * 2) / 3;
        LinearLayout row = null;
        for (int i = 0; i <= favs.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
                rp.bottomMargin = gap;
                favGrid.addView(row, rp);
            }
            View tile = i < favs.size() ? favTile(i, favs.get(i)) : addFavTile();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            if (i % 3 != 2) p.rightMargin = gap;
            row.addView(tile, p);
        }
    }

    private View favTile(int index, String[] fav) {
        boolean black = "1".equals(fav[2]);
        int fg = black ? WHITE : BLACK;
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(black ? outline(BLACK, WHITE, 2) : solid(WHITE));
        String initial = fav[0].isEmpty() ? "·" : fav[0].substring(0, 1).toUpperCase(Locale.getDefault());
        tile.addView(text(initial, 36, fg), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        TextView label = text(fav[0], 12, fg);
        label.setTypeface(Typeface.DEFAULT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(dp(6), 0, dp(6), dp(5));
        tile.addView(label, lp);
        tile.setOnClickListener(v -> loadInCurrent(fav[1]));
        tile.setOnLongClickListener(v -> {
            String[] items = {black ? "hacer blanco" : "hacer negro", "eliminar"};
            dialog().setTitle(fav[0]).setItems(items, (d, w) -> {
                List<String[]> favs = favorites();
                if (index >= favs.size()) return;
                if (w == 0) {
                    favs.get(index)[2] = black ? "0" : "1";
                    saveFavorites(favs);
                    updateChrome();
                } else {
                    removeFavorite(index);
                }
            }).show();
            return true;
        });
        return tile;
    }

    private View addFavTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(outline(BLACK, DIM, 2));
        tile.addView(text("+", 34, WHITE));
        tile.addView(text("añadir", 12, GRAY));
        tile.setOnClickListener(v -> askFavorite());
        return tile;
    }

    private void askFavorite() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = new EditText(this);
        name.setHint("nombre");
        name.setSingleLine(true);
        EditText url = new EditText(this);
        url.setHint("dirección (ej: lanacion.com.ar)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(url);
        dialog().setTitle("nuevo favorito").setView(box)
                .setPositiveButton("añadir", (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) return;
                    String full = toUrl(u);
                    String n = name.getText().toString().trim();
                    addFavorite(n.isEmpty() ? displayUrl(full) : n, full);
                })
                .setNegativeButton("cancelar", null).show();
    }

    // ------------------------------------------------------------------ direcciones

    private String engine() {
        return prefs.getString("engine", ENGINES[0]);
    }

    /** Texto escrito en la barra -> dirección o búsqueda. */
    private String toUrl(String text) {
        boolean looksLikeUrl = !text.contains(" ")
                && (text.contains(".") || text.startsWith("localhost") || text.contains("://") || text.startsWith("about:"));
        if (looksLikeUrl) {
            return text.contains("://") || text.startsWith("about:") ? text : "https://" + text;
        }
        String q;
        try {
            q = URLEncoder.encode(text, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            q = text;
        }
        return "google".equals(engine()) ? "https://www.google.com/search?q=" + q : "https://duckduckgo.com/?q=" + q;
    }

    private static boolean isBlank(String url) {
        return url == null || url.isEmpty() || BLANK.equals(url);
    }

    /** "https://www.ejemplo.com/a" -> "ejemplo.com" (como Safari). */
    private static String displayUrl(String url) {
        Uri u = Uri.parse(url);
        String host = u.getHost();
        if (host == null) return url;
        if (host.startsWith("www.")) host = host.substring(4);
        return "http".equals(u.getScheme()) ? "⚠ " + host : host;
    }

    private static boolean sameSite(String a, String b) {
        return displayUrl(a).equals(displayUrl(b));
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

    private TextView barButton(String s, float sp) {
        TextView b = text(s, sp, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
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

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
