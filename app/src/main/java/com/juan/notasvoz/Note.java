package com.juan.notasvoz;

public class Note {
    public long id;
    public String text;
    public int color;
    public long updated;

    public Note(long id, String text, int color, long updated) {
        this.id = id;
        this.text = text;
        this.color = color;
        this.updated = updated;
    }
}
