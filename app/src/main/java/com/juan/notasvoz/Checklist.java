package com.juan.notasvoz;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Convierte frases dictadas como "comprar pan, leche y huevos" en listas de tareas.
 */
public final class Checklist {
    /** Palabras con las que empieza una lista; las más largas primero. */
    private static final String[][] TRIGGERS = {
            {"lista de la compra", "Lista de la compra"},
            {"lista de compras", "Lista de compras"},
            {"lista de tareas", "Tareas"},
            {"lista de", "Lista"},
            {"lista", "Lista"},
            {"tengo que comprar", "Comprar"},
            {"hay que comprar", "Comprar"},
            {"comprar", "Comprar"},
            {"cosas que hacer", "Cosas que hacer"},
            {"cosas por hacer", "Cosas por hacer"},
            {"tareas", "Tareas"},
            {"pendientes", "Pendientes"},
    };

    /** Separadores de elementos: comas, punto y coma, "y", "e", o decir "coma"/"siguiente". */
    private static final String SPLIT =
            "(?i)\\s*[,;]\\s*|\\s+(?:y|e|coma|siguiente)\\s+|\\.\\s+|\\n+";

    private Checklist() {
    }

    /** Devuelve una nota-lista si la frase empieza con una palabra de lista, o null. */
    public static Note parse(String spoken, Note into) {
        String s = spoken.trim();
        String lower = s.toLowerCase(Locale.getDefault());
        for (String[] t : TRIGGERS) {
            String trig = t[0];
            if (!lower.startsWith(trig)) continue;
            if (lower.length() > trig.length()
                    && Character.isLetterOrDigit(lower.charAt(trig.length()))) continue;
            List<Note.Item> items = splitItems(s.substring(trig.length()));
            if (items.isEmpty()) return null;
            into.text = t[1];
            into.items = items;
            return into;
        }
        return null;
    }

    public static List<Note.Item> splitItems(String text) {
        List<Note.Item> items = new ArrayList<>();
        String rest = text.replaceFirst("^[\\s:,.\\-]+", "");
        for (String part : rest.split(SPLIT)) {
            String p = part.trim().replaceAll("[.,;:]+$", "").trim();
            if (!p.isEmpty()) items.add(new Note.Item(Metro.capitalize(p), false));
        }
        return items;
    }
}
