package com.juan.notasvoz;

import java.util.ArrayList;
import java.util.List;

public class Note {
    public long id;
    /** Texto de la nota; en una lista es el título (puede estar vacío). */
    public String text;
    public int color;
    public long updated;
    public boolean pinned;
    /** Si tiene elementos, la nota es una lista de tareas. */
    public List<Item> items = new ArrayList<>();

    public static class Item {
        public String text;
        public boolean done;

        public Item(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public Note(long id, String text, int color, long updated) {
        this.id = id;
        this.text = text;
        this.color = color;
        this.updated = updated;
    }

    public boolean isList() {
        return !items.isEmpty();
    }

    public int doneCount() {
        int n = 0;
        for (Item i : items) if (i.done) n++;
        return n;
    }

    public boolean isEmpty() {
        return text.trim().isEmpty() && items.isEmpty();
    }

    /** Todo el contenido como texto plano, para buscar y compartir. */
    public String plain() {
        StringBuilder sb = new StringBuilder(text.trim());
        for (Item i : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(i.done ? "✓ " : "○ ").append(i.text);
        }
        return sb.toString();
    }
}
