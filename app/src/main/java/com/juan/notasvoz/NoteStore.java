package com.juan.notasvoz;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Guarda las notas en el teléfono como JSON dentro de SharedPreferences. */
public class NoteStore {
    private static final String PREFS = "notas";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_NEXT_COLOR = "next_color";

    private final SharedPreferences prefs;

    public NoteStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Notas ordenadas de la más reciente a la más antigua. */
    public List<Note> all() {
        List<Note> notes = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_NOTES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                notes.add(new Note(o.getLong("id"), o.getString("text"),
                        o.getInt("color"), o.getLong("updated")));
            }
        } catch (JSONException ignored) {
        }
        notes.sort((a, b) -> Long.compare(b.updated, a.updated));
        return notes;
    }

    public Note get(long id) {
        for (Note n : all()) {
            if (n.id == id) return n;
        }
        return null;
    }

    public Note create(String text) {
        long now = System.currentTimeMillis();
        Note note = new Note(now, text, nextTileColor(), now);
        List<Note> notes = all();
        notes.add(0, note);
        write(notes);
        return note;
    }

    public void save(Note note) {
        List<Note> notes = all();
        boolean found = false;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id == note.id) {
                notes.set(i, note);
                found = true;
                break;
            }
        }
        if (!found) notes.add(note);
        write(notes);
    }

    public void delete(long id) {
        List<Note> notes = all();
        notes.removeIf(n -> n.id == id);
        write(notes);
    }

    public int accent() {
        return prefs.getInt(KEY_ACCENT, Metro.CYAN);
    }

    public void setAccent(int color) {
        prefs.edit().putInt(KEY_ACCENT, color).apply();
    }

    /** Cada nota nueva recibe el siguiente color de la paleta, para un inicio multicolor. */
    private int nextTileColor() {
        int i = prefs.getInt(KEY_NEXT_COLOR, 0);
        prefs.edit().putInt(KEY_NEXT_COLOR, (i + 1) % Metro.TILE_COLORS.length).apply();
        return Metro.TILE_COLORS[i % Metro.TILE_COLORS.length];
    }

    private void write(List<Note> notes) {
        JSONArray arr = new JSONArray();
        try {
            for (Note n : notes) {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("text", n.text);
                o.put("color", n.color);
                o.put("updated", n.updated);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply();
    }
}
