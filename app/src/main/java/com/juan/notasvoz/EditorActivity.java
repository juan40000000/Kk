package com.juan.notasvoz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends Activity {
    public static final String EXTRA_ID = "id";

    private NoteStore store;
    private Note note;
    private boolean isNew;
    private boolean deleted;

    private LinearLayout root;
    private LinearLayout band;
    private TextView title;
    private TextView dateLabel;
    private EditText editor;
    private LinearLayout itemsBox;
    private View addItemRow;
    private HorizontalScrollView colorStrip;
    private LinearLayout swatches;
    private LinearLayout appBar;
    private final List<Row> rows = new ArrayList<>();

    /** Una fila de la lista: casilla + texto editable. */
    private static class Row {
        Note.Item item;
        View view;
        FrameLayout box;
        ImageView check;
        EditText text;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NoteStore(this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (savedInstanceState != null) id = savedInstanceState.getLong(EXTRA_ID, id);
        note = id == -1 ? null : store.get(id);
        isNew = note == null;
        if (isNew) {
            long now = System.currentTimeMillis();
            note = new Note(now, "", store.nextTileColor(), now);
        }

        buildUi();
        if (savedInstanceState == null) Metro.turnstileIn(root);

        if (isNew) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            editor.requestFocus();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        persist();
        if (!isNew) out.putLong(EXTRA_ID, note.id);
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    /** Pasa lo que hay en pantalla a la nota. */
    private void collect() {
        note.text = editor.getText().toString().trim();
        List<Note.Item> items = new ArrayList<>();
        for (Row r : rows) {
            String t = r.text.getText().toString().trim();
            if (!t.isEmpty()) items.add(new Note.Item(t, r.item.done));
        }
        note.items = items;
    }

    private void persist() {
        if (deleted) return;
        String before = isNew ? "" : note.plain();
        boolean wasList = note.isList() || !rows.isEmpty();
        collect();
        if (note.isEmpty()) {
            if (!isNew) store.delete(note.id);
            return;
        }
        if (isNew || !note.plain().equals(before) || wasList != note.isList()) {
            note.updated = System.currentTimeMillis();
        }
        store.save(note);
        isNew = false;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        band = new LinearLayout(this);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setPadding(dp(20), dp(16), dp(20), dp(14));
        band.setBackgroundColor(note.color);

        TextView small = Metro.text(this, "NOTAS DE VOZ", 14, Metro.SEMIBOLD, Color.WHITE);
        small.setLetterSpacing(0.08f);
        band.addView(small);

        title = Metro.text(this, "", 48, Metro.LIGHT, Color.WHITE);
        title.setIncludeFontPadding(false);
        title.setPadding(0, dp(4), 0, dp(2));
        band.addView(title);

        dateLabel = Metro.text(this, isNew ? "sin guardar" : Metro.friendlyDate(note.updated),
                14, Metro.REGULAR, Color.argb(220, 255, 255, 255));
        band.addView(dateLabel);
        root.addView(band);

        colorStrip = new HorizontalScrollView(this);
        colorStrip.setHorizontalScrollBarEnabled(false);
        colorStrip.setVisibility(View.GONE);
        swatches = new LinearLayout(this);
        swatches.setPadding(dp(16), dp(12), dp(16), dp(4));
        colorStrip.addView(swatches);
        root.addView(colorStrip);
        buildSwatches();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(24));
        scroll.addView(content);

        editor = new EditText(this);
        editor.setText(note.text);
        editor.setSelection(editor.getText().length());
        editor.setHintTextColor(Metro.SUBTLE);
        editor.setTextColor(Color.WHITE);
        editor.setTypeface(Metro.LIGHT);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackgroundColor(Color.BLACK);
        editor.setPadding(dp(20), dp(16), dp(20), dp(12));
        editor.setLineSpacing(0, 1.1f);
        content.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        itemsBox = new LinearLayout(this);
        itemsBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(itemsBox);

        addItemRow = makeAddItemRow();
        content.addView(addItemRow);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        appBar = Metro.appBar(this);
        root.addView(appBar);

        for (Note.Item it : note.items) addRow(new Note.Item(it.text, it.done), -1, false);
        applyMode();
        setContentView(root);
    }

    /** Ajusta título, textos y botones según sea nota normal o lista. */
    private void applyMode() {
        boolean list = !rows.isEmpty();
        title.setText(list ? "lista" : isNew ? "nueva nota" : "nota");
        if (list) {
            editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            editor.setHint("título de la lista");
            editor.setMinLines(1);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            editor.setHint("escribe aquí o toca dictar…\n\ndi \"comprar pan, leche y huevos\" "
                    + "y se convierte en lista");
            editor.setMinLines(8);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        addItemRow.setVisibility(list ? View.VISIBLE : View.GONE);
        buildAppBar();
    }

    private void buildAppBar() {
        appBar.removeAllViews();
        boolean list = !rows.isEmpty();
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_mic, "dictar", note.color,
                v -> Metro.startSpeech(this, list ? "Dicta elementos" : "Sigue dictando")));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_list, list ? "a texto" : "lista", 0,
                v -> toggleList()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_pin,
                note.pinned ? "desfijar" : "fijar", note.pinned ? Metro.APP_BAR_ON : 0,
                v -> {
                    note.pinned = !note.pinned;
                    Toast.makeText(this, note.pinned ? "fijada arriba" : "ya no está fijada",
                            Toast.LENGTH_SHORT).show();
                    buildAppBar();
                }));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_palette, "color", 0,
                v -> colorStrip.setVisibility(colorStrip.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE)));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_more, "más", 0,
                v -> moreMenu()));
    }

    private void moreMenu() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"compartir", "borrar"}, (d, which) -> {
                    if (which == 0) share();
                    else confirmDelete();
                })
                .show();
    }

    // ---- Listas ----

    private void toggleList() {
        if (rows.isEmpty()) {
            // Texto → lista: cada línea o frase separada por comas/"y" se vuelve un elemento.
            String text = editor.getText().toString().trim();
            List<Note.Item> items = new ArrayList<>();
            String titleText = "";
            if (!text.isEmpty()) {
                Note probe = new Note(0, "", 0, 0);
                if (Checklist.parse(text, probe) != null) {
                    titleText = probe.text;
                    items = probe.items;
                } else {
                    items = Checklist.splitItems(text);
                }
            }
            editor.setText(titleText);
            if (items.isEmpty()) items.add(new Note.Item("", false));
            for (Note.Item it : items) addRow(it, -1, false);
            applyMode();
            rows.get(rows.size() - 1).text.requestFocus();
        } else {
            // Lista → texto: título y elementos, uno por línea.
            StringBuilder sb = new StringBuilder(editor.getText().toString().trim());
            for (Row r : rows) {
                String t = r.text.getText().toString().trim();
                if (t.isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
            }
            rows.clear();
            itemsBox.removeAllViews();
            editor.setText(sb.toString());
            applyMode();
            editor.setSelection(editor.getText().length());
        }
    }

    private Row addRow(Note.Item item, int index, boolean focus) {
        Row r = new Row();
        r.item = item;

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(20), 0, dp(8), 0);

        r.box = new FrameLayout(this);
        r.check = new ImageView(this);
        r.check.setImageResource(R.drawable.ic_check);
        int pad = dp(3);
        r.check.setPadding(pad, pad, pad, pad);
        r.box.addView(r.check, new FrameLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(26), dp(26));
        blp.rightMargin = dp(14);
        line.addView(r.box, blp);
        r.box.setOnClickListener(v -> {
            r.item.done = !r.item.done;
            styleRow(r);
            r.box.animate().scaleX(1.2f).scaleY(1.2f).setDuration(90)
                    .withEndAction(() -> r.box.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
        });

        r.text = new EditText(this);
        r.text.setText(item.text);
        r.text.setTextColor(Color.WHITE);
        r.text.setHint("elemento");
        r.text.setHintTextColor(Metro.SUBTLE);
        r.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        r.text.setTypeface(Metro.LIGHT);
        r.text.setBackgroundColor(Color.TRANSPARENT);
        r.text.setPadding(0, dp(10), 0, dp(10));
        r.text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        r.text.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        // "Siguiente" en el teclado crea otro elemento debajo.
        r.text.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) return false;
            addRow(new Note.Item("", false), rows.indexOf(r) + 1, true);
            return true;
        });
        line.addView(r.text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView remove = new ImageView(this);
        remove.setImageResource(R.drawable.ic_close);
        remove.setAlpha(0.5f);
        int rp = dp(10);
        remove.setPadding(rp, rp, rp, rp);
        remove.setOnClickListener(v -> removeRow(r));
        line.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(40)));

        r.view = line;
        if (index < 0 || index > rows.size()) index = rows.size();
        rows.add(index, r);
        itemsBox.addView(line, index);
        styleRow(r);
        if (focus) r.text.requestFocus();
        return r;
    }

    private void removeRow(Row r) {
        int idx = rows.indexOf(r);
        rows.remove(r);
        itemsBox.removeView(r.view);
        if (rows.isEmpty()) {
            applyMode();
        } else {
            rows.get(Math.max(0, idx - 1)).text.requestFocus();
        }
    }

    private void styleRow(Row r) {
        GradientDrawable d = new GradientDrawable();
        if (r.item.done) {
            d.setColor(note.color);
        } else {
            d.setColor(Color.TRANSPARENT);
            d.setStroke(dp(2), Color.WHITE);
        }
        r.box.setBackground(d);
        r.check.setVisibility(r.item.done ? View.VISIBLE : View.INVISIBLE);
        r.text.setTextColor(r.item.done ? Metro.SUBTLE : Color.WHITE);
        int flags = r.text.getPaintFlags();
        r.text.setPaintFlags(r.item.done ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private View makeAddItemRow() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(20), dp(10), dp(20), dp(10));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_add);
        plus.setAlpha(0.7f);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(26), dp(26));
        plp.rightMargin = dp(14);
        line.addView(plus, plp);
        line.addView(Metro.text(this, "añadir elemento", 20, Metro.LIGHT, Metro.SUBTLE));
        line.setOnClickListener(v -> addRow(new Note.Item("", false), -1, true));
        return line;
    }

    private void buildSwatches() {
        swatches.removeAllViews();
        int size = dp(40);
        for (int color : Metro.TILE_COLORS) {
            View sw = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            if (color == note.color) d.setStroke(dp(3), Color.WHITE);
            sw.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = dp(8);
            sw.setOnClickListener(v -> {
                note.color = color;
                band.setBackgroundColor(color);
                buildSwatches();
                buildAppBar();
                for (Row r : rows) styleRow(r);
            });
            Metro.tilt(sw);
            swatches.addView(sw, lp);
        }
    }

    private void share() {
        collect();
        String text = note.plain();
        if (text.isEmpty()) {
            Toast.makeText(this, "la nota está vacía", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "compartir nota"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("¿borrar nota?")
                .setMessage("esta nota se eliminará para siempre.")
                .setPositiveButton("borrar", (d, w) -> {
                    deleted = true;
                    if (!isNew) store.delete(note.id);
                    finish();
                })
                .setNegativeButton("cancelar", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != Metro.REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String spoken = results.get(0).trim();
        if (spoken.isEmpty()) return;
        dateLabel.setText("editada ahora");

        if (!rows.isEmpty()) {
            // En una lista, lo dictado se añade como elementos nuevos.
            List<Note.Item> items = Checklist.splitItems(spoken);
            // Si la última fila está vacía, se reutiliza.
            Row last = rows.get(rows.size() - 1);
            if (last.text.getText().toString().trim().isEmpty() && !items.isEmpty()) {
                last.text.setText(items.remove(0).text);
            }
            for (Note.Item it : items) addRow(it, -1, false);
            return;
        }

        // Una nota vacía dictada como "comprar ..." se convierte directamente en lista.
        if (editor.getText().toString().trim().isEmpty()) {
            Note probe = new Note(0, "", 0, 0);
            if (Checklist.parse(spoken, probe) != null) {
                editor.setText(probe.text);
                for (Note.Item it : probe.items) addRow(it, -1, false);
                applyMode();
                return;
            }
        }

        String current = editor.getText().toString();
        int at = Math.max(0, editor.getSelectionStart());
        String before = current.substring(0, at);
        String after = current.substring(at);
        String trimmedBefore = before.trim();
        boolean startSentence = trimmedBefore.isEmpty() || trimmedBefore.endsWith(".")
                || trimmedBefore.endsWith("?") || trimmedBefore.endsWith("!");
        if (startSentence) spoken = Metro.capitalize(spoken);
        String sep = before.isEmpty() || before.endsWith(" ") || before.endsWith("\n") ? "" : " ";
        editor.setText(before + sep + spoken + after);
        editor.setSelection(before.length() + sep.length() + spoken.length());
    }

    @Override
    public void finish() {
        persist();
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private int dp(float v) {
        return Metro.dp(this, v);
    }
}
