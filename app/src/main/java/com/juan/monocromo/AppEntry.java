package com.juan.monocromo;

import android.content.ComponentName;

import java.text.Normalizer;
import java.util.Locale;

/** Una app instalada que se puede abrir desde el launcher. */
final class AppEntry {
    final String label;
    final String lower;
    final ComponentName component;
    final String key;
    final String letter;

    AppEntry(String label, ComponentName component) {
        this.label = label;
        this.lower = strip(label.toLowerCase(Locale.getDefault()));
        this.component = component;
        this.key = component.flattenToString();
        char c = lower.isEmpty() ? '#' : lower.charAt(0);
        this.letter = (c >= 'a' && c <= 'z') ? String.valueOf(c) : "#";
    }

    String initial() {
        return label.isEmpty() ? "#" : label.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    /** Quita tildes para ordenar y buscar ("Música" -> "musica"). */
    static String strip(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
