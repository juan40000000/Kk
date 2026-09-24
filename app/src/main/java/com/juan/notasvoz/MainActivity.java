package com.juan.notasvoz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_DICTATE = "com.juan.notasvoz.DICTATE";
    public static final String ACTION_WRITE = "com.juan.notasvoz.WRITE";

    private NoteStore store;
    private LinearLayout tiles;
    private TextView appName;
    private TextView count;
    private EditText search;
    private LinearLayout appBar;
    private boolean firstShow = true;
    private long highlightId = -1;

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
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_search, "buscar", 0,
                v -> toggleSearch()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_palette, "color", 0,
                v -> pickAccent()));
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
        if (requestCode != Metro.REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty() || results.get(0).trim().isEmpty()) return;
        Note note = store.create(Metro.capitalize(results.get(0)));
        highlightId = note.id;
        Toast.makeText(this, "nota guardada", Toast.LENGTH_SHORT).show();
        // onResume() redibuja los tiles y anima la nota nueva.
    }

    private void refresh() {
        if (tiles == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        List<Note> all = store.all();
        List<Note> notes = new ArrayList<>();
        for (Note n : all) {
            if (q.isEmpty() || n.text.toLowerCase(Locale.getDefault()).contains(q)) notes.add(n);
        }

        int total = all.size();
        count.setText(total == 0 ? "sin notas todavía"
                : total == 1 ? "1 nota" : total + " notas");

        tiles.removeAllViews();
        if (notes.isEmpty()) {
            TextView empty = Metro.text(this, q.isEmpty()
                            ? "toca el micrófono y di lo que quieras recordar. se guarda al instante."
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

        LinearLayout pendingRow = null;
        int index = 0;
        for (Note n : notes) {
            boolean wide = isWide(n);
            View tile = makeTile(n, wide);
            if (wide) {
                pendingRow = null;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, square);
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

    private static boolean isWide(Note n) {
        return n.text.length() > 60 || n.text.contains("\n");
    }

    private View makeTile(Note n, boolean wide) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundColor(n.color);
        int pad = dp(12);
        tile.setPadding(pad, pad, pad, pad);

        boolean shortNote = !wide && n.text.length() <= 24;
        TextView body = Metro.text(this, n.text,
                shortNote ? 24 : wide ? 19 : 16,
                shortNote || wide ? Metro.LIGHT : Metro.REGULAR, Color.WHITE);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setMaxLines(wide ? 4 : shortNote ? 4 : 5);
        body.setLineSpacing(0, 1.05f);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(18);
        tile.addView(body, blp);

        TextView date = Metro.text(this, Metro.friendlyDate(n.updated), 12, Metro.REGULAR,
                Color.argb(220, 255, 255, 255));
        tile.addView(date, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));

        tile.setOnClickListener(v -> openEditor(n.id));
        tile.setOnLongClickListener(v -> {
            confirmDelete(n);
            return true;
        });
        Metro.tilt(tile);
        return tile;
    }

    private void confirmDelete(Note n) {
        new AlertDialog.Builder(this)
                .setTitle("¿borrar nota?")
                .setMessage(n.text.length() > 120 ? n.text.substring(0, 120) + "…" : n.text)
                .setPositiveButton("borrar", (d, w) -> {
                    store.delete(n.id);
                    refresh();
                })
                .setNegativeButton("cancelar", null)
                .show();
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
