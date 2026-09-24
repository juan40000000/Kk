package com.juan.notasvoz;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    public static final String ACTION_DICTATE = "com.juan.notasvoz.DICTATE";
    public static final String ACTION_WRITE = "com.juan.notasvoz.WRITE";
    public static final String PREF_BUBBLE = "bubble_enabled";
    private static final int REQ_PERMS = 43;
    private static final int REQ_EXPORT = 44;
    private static final int REQ_IMPORT = 45;

    private NoteStore store;
    private LinearLayout tiles;
    private TextView appName;
    private TextView count;
    private EditText search;
    private LinearLayout appBar;
    private boolean firstShow = true;
    private long highlightId = -1;
    /** El usuario pidió la burbuja y estamos esperando que conceda permisos. */
    private boolean bubblePending;

    private final Handler live = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<FrameLayout> liveTiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NoteStore(this);
        buildUi();
        if (savedInstanceState == null) handleAction(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAction(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (bubblePending) {
            bubblePending = false;
            enableBubble();
        } else if (prefs().getBoolean(PREF_BUBBLE, false) && !BubbleService.running
                && hasBubblePermissions()) {
            BubbleService.start(this);
        }
        buildAppBar();
        live.removeCallbacks(liveTick);
        live.postDelayed(liveTick, 2500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        live.removeCallbacks(liveTick);
    }

    private void handleAction(Intent intent) {
        if (intent == null) return;
        if (ACTION_DICTATE.equals(intent.getAction())) {
            dictate();
        } else if (ACTION_WRITE.equals(intent.getAction())) {
            openEditor(-1);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(4));

        appName = Metro.text(this, "NOTAS DE VOZ", 14, Metro.SEMIBOLD, store.accent());
        appName.setLetterSpacing(0.08f);
        header.addView(appName);

        TextView title = Metro.text(this, "notas", 64, Metro.LIGHT, Color.WHITE);
        title.setIncludeFontPadding(false);
        title.setPadding(0, dp(2), 0, 0);
        title.setLetterSpacing(-0.03f);
        header.addView(title);

        count = Metro.text(this, "", 16, Metro.LIGHT, Metro.SUBTLE);
        count.setPadding(dp(3), dp(2), 0, dp(6));
        header.addView(count);

        search = new EditText(this);
        search.setHint("buscar");
        search.setHintTextColor(Metro.SUBTLE);
        search.setTextColor(Color.BLACK);
        search.setTypeface(Metro.REGULAR);
        search.setSingleLine(true);
        search.setBackgroundColor(Color.WHITE);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(8);
        header.addView(search, slp);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.VERTICAL);
        tiles.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(tiles);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        appBar = Metro.appBar(this);
        root.addView(appBar);
        buildAppBar();

        setContentView(root);
    }

    private void buildAppBar() {
        appBar.removeAllViews();
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_mic, "dictar", store.accent(),
                v -> dictate()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_add, "nueva", 0,
                v -> openEditor(-1)));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_bubble, "burbuja",
                BubbleService.running ? Metro.APP_BAR_ON : 0, v -> toggleBubble()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_search, "buscar", 0,
                v -> toggleSearch()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_more, "más", 0,
                v -> moreMenu()));
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("notas", MODE_PRIVATE);
    }

    private boolean hasBubblePermissions() {
        return Settings.canDrawOverlays(this)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleBubble() {
        if (BubbleService.running) {
            prefs().edit().putBoolean(PREF_BUBBLE, false).apply();
            BubbleService.stop(this);
            Toast.makeText(this, "burbuja oculta", Toast.LENGTH_SHORT).show();
            buildAppBar();
        } else {
            enableBubble();
        }
    }

    /** Pide lo que falte (dibujar encima, micrófono, notificaciones) y enciende la burbuja. */
    private void enableBubble() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("burbuja flotante")
                    .setMessage("para que el micrófono flote sobre las otras apps, activa "
                            + "\"Mostrar sobre otras apps\" para Notas en la siguiente pantalla "
                            + "y luego vuelve aquí.")
                    .setPositiveButton("activar", (d, w) -> {
                        bubblePending = true;
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    })
                    .setNegativeButton("cancelar", null)
                    .show();
            return;
        }
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
            return;
        }
        prefs().edit().putBoolean(PREF_BUBBLE, true).apply();
        BubbleService.start(this);
        Toast.makeText(this, "toca la burbuja para dictar · arrástrala a la X para quitarla",
                Toast.LENGTH_LONG).show();
        tiles.postDelayed(this::buildAppBar, 300);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            enableBubble();
        } else {
            Toast.makeText(this, "la burbuja necesita el micrófono para grabar",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void toggleSearch() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (search.getVisibility() == View.VISIBLE) {
            search.setText("");
            search.setVisibility(View.GONE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            search.setVisibility(View.VISIBLE);
            search.requestFocus();
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void dictate() {
        Metro.startSpeech(this, "Dicta tu nota");
    }

    private void openEditor(long id) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_ID, id);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_EXPORT && data.getData() != null) {
            exportTo(data.getData());
            return;
        }
        if (requestCode == REQ_IMPORT && data.getData() != null) {
            importFrom(data.getData());
            return;
        }
        if (requestCode != Metro.REQ_SPEECH) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty() || results.get(0).trim().isEmpty()) return;
        Note note = store.createFromSpeech(results.get(0));
        highlightId = note.id;
        Toast.makeText(this, note.isList()
                ? "lista guardada con " + note.items.size() + " elementos"
                : "nota guardada", Toast.LENGTH_SHORT).show();
        // onResume() redibuja los tiles y anima la nota nueva.
    }

    private void refresh() {
        if (tiles == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<Note> all = store.all();
        List<Note> notes = new ArrayList<>();
        for (Note n : all) {
            if (q.isEmpty() || n.plain().toLowerCase(Locale.getDefault()).contains(q)) notes.add(n);
        }

        int total = all.size();
        count.setText(total == 0 ? "sin notas todavía"
                : total == 1 ? "1 nota" : total + " notas");

        tiles.removeAllViews();
        liveTiles.clear();
        if (notes.isEmpty()) {
            TextView empty = Metro.text(this, q.isEmpty()
                            ? "toca el micrófono y di lo que quieras recordar. se guarda al instante.\n\n"
                            + "prueba a decir \"comprar pan, leche y huevos\" para crear una lista."
                            : "no hay notas que coincidan.",
                    22, Metro.LIGHT, Metro.SUBTLE);
            empty.setPadding(dp(4), dp(24), dp(24), 0);
            tiles.addView(empty);
            firstShow = false;
            return;
        }

        int gap = dp(8);
        int width = getResources().getDisplayMetrics().widthPixels - dp(32);
        int square = (width - gap) / 2;

        boolean anyPinned = notes.get(0).pinned;
        boolean inPinned = false;
        LinearLayout pendingRow = null;
        int index = 0;
        for (Note n : notes) {
            if (anyPinned && n.pinned && !inPinned) {
                tiles.addView(sectionLabel("fijadas"));
                inPinned = true;
            } else if (anyPinned && !n.pinned && inPinned) {
                tiles.addView(sectionLabel("todas"));
                inPinned = false;
                pendingRow = null;
            }

            boolean wide = isWide(n);
            FrameLayout tile = makeTile(n, wide);
            if (wide) {
                pendingRow = null;
                int height = n.pinned ? square + square / 3 : square;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
                lp.bottomMargin = gap;
                tiles.addView(tile, lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(square, square);
                if (pendingRow == null) {
                    pendingRow = new LinearLayout(this);
                    pendingRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.bottomMargin = gap;
                    tiles.addView(pendingRow, rlp);
                    pendingRow.addView(tile, lp);
                } else {
                    lp.leftMargin = gap;
                    pendingRow.addView(tile, lp);
                    pendingRow = null;
                }
            }
            if (firstShow) {
                Metro.flipIn(tile, Math.min(index, 10) * 60L);
            } else if (n.id == highlightId) {
                Metro.flipIn(tile, 0);
            }
            index++;
        }
        firstShow = false;
        highlightId = -1;
    }

    private TextView sectionLabel(String s) {
        TextView t = Metro.text(this, s, 20, Metro.LIGHT, Metro.SUBTLE);
        t.setPadding(dp(2), dp(4), 0, dp(6));
        return t;
    }

    private static boolean isWide(Note n) {
        if (n.pinned) return true;
        if (n.isList()) return n.items.size() > 3;
        return n.text.length() > 60 || n.text.contains("\n");
    }

    /**
     * Un tile tiene dos caras. La delantera muestra la nota; la trasera, un resumen
     * (progreso de la lista o la fecha). Las caras se alternan solas como las live tiles.
     */
    private FrameLayout makeTile(Note n, boolean wide) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundColor(n.color);

        View front = tileFront(n, wide);
        View back = tileBack(n);
        tile.addView(front, matchParent());
        tile.addView(back, matchParent());
        back.setVisibility(View.GONE);
        liveTiles.add(tile);

        if (n.pinned) {
            ImageView pin = new ImageView(this);
            pin.setImageResource(R.drawable.ic_pin);
            pin.setAlpha(0.9f);
            int s = dp(18);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END);
            plp.setMargins(0, dp(10), dp(10), 0);
            tile.addView(pin, plp);
        }

        tile.setOnClickListener(v -> openEditor(n.id));
        tile.setOnLongClickListener(v -> {
            tileMenu(n);
            return true;
        });
        Metro.tilt(tile);
        return tile;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private View tileFront(Note n, boolean wide) {
        FrameLayout face = new FrameLayout(this);
        int pad = dp(12);
        face.setPadding(pad, pad, n.pinned ? dp(32) : pad, pad);

        CharSequence content;
        float size;
        Typeface font;
        int maxLines;
        if (n.isList()) {
            content = listPreview(n);
            size = 15;
            font = Metro.REGULAR;
            maxLines = wide ? (n.pinned ? 7 : 5) : 5;
        } else {
            boolean shortNote = !wide && n.text.length() <= 24;
            content = n.text;
            size = shortNote ? 24 : wide ? 19 : 16;
            font = shortNote || wide ? Metro.LIGHT : Metro.REGULAR;
            maxLines = wide ? (n.pinned ? 6 : 4) : shortNote ? 4 : 5;
        }
        TextView body = Metro.text(this, "", size, font, Color.WHITE);
        body.setText(content);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setMaxLines(maxLines);
        body.setLineSpacing(0, 1.05f);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(18);
        face.addView(body, blp);

        String foot = n.isList() ? n.doneCount() + "/" + n.items.size() + " · "
                + Metro.friendlyDate(n.updated) : Metro.friendlyDate(n.updated);
        TextView date = Metro.text(this, foot, 12, Metro.REGULAR, Color.argb(220, 255, 255, 255));
        face.addView(date, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        return face;
    }

    /** Título en negrita y elementos con ○ / ✓; los hechos van tachados. */
    private CharSequence listPreview(Note n) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!n.text.trim().isEmpty()) {
            sb.append(n.text.trim());
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);
        }
        // Primero lo pendiente, que es lo que importa ver de un vistazo.
        List<Note.Item> ordered = new ArrayList<>();
        for (Note.Item i : n.items) if (!i.done) ordered.add(i);
        for (Note.Item i : n.items) if (i.done) ordered.add(i);
        for (Note.Item i : ordered) {
            if (sb.length() > 0) sb.append('\n');
            int start = sb.length();
            sb.append(i.done ? "✓ " : "○ ").append(i.text);
            if (i.done) {
                sb.setSpan(new StrikethroughSpan(), start + 2, sb.length(), 0);
                sb.setSpan(new ForegroundColorSpan(Color.argb(170, 255, 255, 255)), start,
                        sb.length(), 0);
            }
        }
        return sb;
    }

    private View tileBack(Note n) {
        LinearLayout face = new LinearLayout(this);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.BOTTOM);
        int pad = dp(12);
        face.setPadding(pad, pad, pad, pad);
        // Un velo oscuro distingue la cara trasera sin perder el color del tile.
        face.setBackgroundColor(Color.argb(40, 0, 0, 0));

        String big;
        String small;
        if (n.isList()) {
            int done = n.doneCount();
            big = done + "/" + n.items.size();
            small = done == n.items.size() ? "¡todo hecho!"
                    : (n.items.size() - done) + (n.items.size() - done == 1 ? " pendiente" : " pendientes");
        } else {
            String when = Metro.friendlyDate(n.updated);
            int sp = when.lastIndexOf(' ');
            boolean hasTime = sp > 0 && when.substring(sp).contains(":");
            big = hasTime ? when.substring(0, sp) : when;
            int words = n.text.trim().isEmpty() ? 0 : n.text.trim().split("\\s+").length;
            small = (hasTime ? when.substring(sp + 1) + " · " : "")
                    + words + (words == 1 ? " palabra" : " palabras");
        }
        TextView b = Metro.text(this, big, big.length() > 6 ? 28 : 40, Metro.LIGHT, Color.WHITE);
        b.setIncludeFontPadding(false);
        b.setSingleLine(true);
        face.addView(b);
        TextView s = Metro.text(this, small, 14, Metro.REGULAR, Color.WHITE);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        face.addView(s);
        return face;
    }

    // ---- Live tiles ----

    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            flipRandomTile();
            live.postDelayed(this, 2600 + random.nextInt(1800));
        }
    };

    private void flipRandomTile() {
        if (liveTiles.isEmpty()) return;
        FrameLayout tile = liveTiles.get(random.nextInt(liveTiles.size()));
        if (!tile.isShown() || tile.isPressed()) return;
        View front = tile.getChildAt(0);
        View back = tile.getChildAt(1);
        tile.setCameraDistance(8000 * getResources().getDisplayMetrics().density);
        tile.animate().rotationX(90f).setDuration(220)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    boolean showBack = front.getVisibility() == View.VISIBLE;
                    front.setVisibility(showBack ? View.GONE : View.VISIBLE);
                    back.setVisibility(showBack ? View.VISIBLE : View.GONE);
                    tile.setRotationX(-90f);
                    tile.animate().rotationX(0f).setDuration(260)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    // ---- Menús ----

    private void tileMenu(Note n) {
        String[] options = {n.pinned ? "desfijar" : "fijar arriba", "borrar"};
        new AlertDialog.Builder(this)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        n.pinned = !n.pinned;
                        store.save(n);
                        Toast.makeText(this, n.pinned ? "nota fijada" : "nota desfijada",
                                Toast.LENGTH_SHORT).show();
                        highlightId = n.id;
                        refresh();
                    } else {
                        confirmDelete(n);
                    }
                })
                .show();
    }

    private void confirmDelete(Note n) {
        String preview = n.plain();
        new AlertDialog.Builder(this)
                .setTitle("¿borrar nota?")
                .setMessage(preview.length() > 120 ? preview.substring(0, 120) + "…" : preview)
                .setPositiveButton("borrar", (d, w) -> {
                    store.delete(n.id);
                    refresh();
                })
                .setNegativeButton("cancelar", null)
                .show();
    }

    private void moreMenu() {
        String[] options = {"color de énfasis", "guardar copia de seguridad",
                "restaurar copia de seguridad"};
        new AlertDialog.Builder(this)
                .setItems(options, (d, which) -> {
                    if (which == 0) pickAccent();
                    else if (which == 1) startExport();
                    else startImport();
                })
                .show();
    }

    // ---- Copia de seguridad ----

    /** Abre el selector del sistema: se puede guardar en el teléfono o en Google Drive. */
    private void startExport() {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "notas-" + day + ".json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "no se encontró dónde guardar archivos", Toast.LENGTH_LONG).show();
        }
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // Drive y otros proveedores no siempre marcan los .json como application/json.
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "no se encontró dónde abrir archivos", Toast.LENGTH_LONG).show();
        }
    }

    private void exportTo(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("sin acceso");
            out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));
            int n = store.all().size();
            Toast.makeText(this, "copia guardada: " + n + (n == 1 ? " nota" : " notas"),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "no se pudo guardar la copia", Toast.LENGTH_LONG).show();
        }
    }

    private void importFrom(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("sin acceso");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int r;
            while ((r = in.read(chunk)) != -1) buf.write(chunk, 0, r);
            int changed = store.importJson(buf.toString("UTF-8"));
            firstShow = true;
            refresh();
            new AlertDialog.Builder(this)
                    .setTitle("copia restaurada")
                    .setMessage(changed == 0 ? "ya tenías todas estas notas."
                            : changed == 1 ? "se recuperó 1 nota."
                            : "se recuperaron " + changed + " notas.")
                    .setPositiveButton("ok", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "ese archivo no es una copia de Notas válida",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void pickAccent() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("color de énfasis")
                .setView(grid)
                .create();
        int size = dp(56);
        for (int color : Metro.TILE_COLORS) {
            View sw = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            if (color == store.accent()) d.setStroke(dp(3), Color.WHITE);
            sw.setBackground(d);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            sw.setOnClickListener(v -> {
                store.setAccent(color);
                appName.setTextColor(color);
                buildAppBar();
                dialog.dismiss();
            });
            Metro.tilt(sw);
            grid.addView(sw, lp);
        }
        dialog.show();
    }

    private int dp(float v) {
        return Metro.dp(this, v);
    }
}
