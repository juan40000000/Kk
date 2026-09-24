package com.juan.notasvoz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class EditorActivity extends Activity {
    public static final String EXTRA_ID = "id";

    private NoteStore store;
    private Note note;
    private boolean isNew;
    private boolean deleted;

    private LinearLayout root;
    private LinearLayout band;
    private TextView dateLabel;
    private EditText editor;
    private HorizontalScrollView colorStrip;
    private LinearLayout swatches;
    private LinearLayout appBar;

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
            int color = Metro.TILE_COLORS[(int) (now / 1000 % Metro.TILE_COLORS.length)];
            note = new Note(now, "", color, now);
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

    private void persist() {
        if (deleted) return;
        String text = editor.getText().toString().trim();
        if (text.isEmpty()) {
            if (!isNew) store.delete(note.id);
            return;
        }
        if (!text.equals(note.text) || isNew) {
            note.updated = System.currentTimeMillis();
        }
        note.text = text;
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

        TextView title = Metro.text(this, isNew ? "nueva nota" : "nota", 48, Metro.LIGHT, Color.WHITE);
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

        editor = new EditText(this);
        editor.setText(note.text);
        editor.setSelection(editor.getText().length());
        editor.setHint("escribe aquí o toca dictar…");
        editor.setHintTextColor(Metro.SUBTLE);
        editor.setTextColor(Color.WHITE);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        editor.setTypeface(Metro.LIGHT);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackgroundColor(Color.BLACK);
        editor.setPadding(dp(20), dp(16), dp(20), dp(16));
        editor.setLineSpacing(0, 1.1f);
        root.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        appBar = Metro.appBar(this);
        root.addView(appBar);
        buildAppBar();

        setContentView(root);
    }

    private void buildAppBar() {
        appBar.removeAllViews();
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_mic, "dictar", note.color,
                v -> Metro.startSpeech(this, "Sigue dictando")));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_palette, "color", 0,
                v -> colorStrip.setVisibility(colorStrip.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE)));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_share, "compartir", 0,
                v -> share()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_delete, "borrar", 0,
                v -> confirmDelete()));
        appBar.addView(Metro.appBarButton(this, R.drawable.ic_check, "listo", 0,
                v -> finish()));
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
            });
            Metro.tilt(sw);
            swatches.addView(sw, lp);
        }
    }

    private void share() {
        String text = editor.getText().toString().trim();
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
        dateLabel.setText("editada ahora");
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
