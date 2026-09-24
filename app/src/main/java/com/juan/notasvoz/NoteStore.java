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

    /** Primero las fijadas; dentro de cada grupo, de la más reciente a la más antigua. */
    public List<Note> all() {
        List<Note> notes = new ArrayList<>();
        try {
            notes = fromJson(new JSONArray(prefs.getString(KEY_NOTES, "[]")));
        } catch (JSONException ignored) {
        }
        notes.sort((a, b) -> a.pinned != b.pinned ? (a.pinned ? -1 : 1)
                : Long.compare(b.updated, a.updated));
        return notes;
    }

    public Note get(long id) {
        for (Note n : all()) {
            if (n.id == id) return n;
        }
        return null;
    }

    /** Crea una nota a partir de lo dictado; "comprar pan, leche y huevos" se vuelve lista. */
    public Note createFromSpeech(String spoken) {
        long now = System.currentTimeMillis();
        Note note = new Note(now, "", nextTileColor(), now);
        if (Checklist.parse(spoken, note) == null) note.text = Metro.capitalize(spoken);
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

    // ---- Copia de seguridad ----

    public String exportJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("app", "notasvoz");
        o.put("version", 2);
        o.put("exported", System.currentTimeMillis());
        o.put("accent", accent());
        o.put("notes", toJson(all()));
        return o.toString(2);
    }

    /**
     * Restaura una copia: añade las notas que no existen y actualiza las que cambiaron.
     * Devuelve cuántas notas se añadieron o actualizaron.
     */
    public int importJson(String json) throws JSONException {
        String trimmed = json.trim();
        JSONArray arr = trimmed.startsWith("[") ? new JSONArray(trimmed)
                : new JSONObject(trimmed).getJSONArray("notes");
        List<Note> incoming = fromJson(arr);
        List<Note> notes = all();
        int changed = 0;
        for (Note in : incoming) {
            int idx = -1;
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).id == in.id) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                notes.add(in);
                changed++;
            } else if (in.updated > notes.get(idx).updated) {
                notes.set(idx, in);
                changed++;
            }
        }
        write(notes);
        return changed;
    }

    /** Cada nota nueva recibe el siguiente color de la paleta, para un inicio multicolor. */
    public int nextTileColor() {
        int i = prefs.getInt(KEY_NEXT_COLOR, 0);
        prefs.edit().putInt(KEY_NEXT_COLOR, (i + 1) % Metro.TILE_COLORS.length).apply();
        return Metro.TILE_COLORS[i % Metro.TILE_COLORS.length];
    }

    private static List<Note> fromJson(JSONArray arr) throws JSONException {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Note n = new Note(o.getLong("id"), o.optString("text", ""),
                    o.optInt("color", Metro.CYAN), o.optLong("updated", o.getLong("id")));
            n.pinned = o.optBoolean("pinned", false);
            JSONArray items = o.optJSONArray("items");
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    JSONObject it = items.getJSONObject(j);
                    n.items.add(new Note.Item(it.optString("text", ""), it.optBoolean("done")));
                }
            }
            notes.add(n);
        }
        return notes;
    }

    private static JSONArray toJson(List<Note> notes) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Note n : notes) {
            JSONObject o = new JSONObject();
            o.put("id", n.id);
            o.put("text", n.text);
            o.put("color", n.color);
            o.put("updated", n.updated);
            if (n.pinned) o.put("pinned", true);
            if (n.isList()) {
                JSONArray items = new JSONArray();
                for (Note.Item it : n.items) {
                    JSONObject io = new JSONObject();
                    io.put("text", it.text);
                    io.put("done", it.done);
                    items.put(io);
                }
                o.put("items", items);
            }
            arr.put(o);
        }
        return arr;
    }

    private void write(List<Note> notes) {
        try {
            prefs.edit().putString(KEY_NOTES, toJson(notes).toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
