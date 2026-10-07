package com.juan.monocromo;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ajustes persistentes: inicio, apps distractoras, ocultas y contadores del día. */
final class Prefs {
    private static final String COUNT = "c:";
    private final SharedPreferences p;
    private final SharedPreferences counts;

    Prefs(Context c) {
        p = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        counts = c.getSharedPreferences("counts", Context.MODE_PRIVATE);
    }

    // ---- inicio ----

    List<String> pinned() {
        String s = p.getString("pinned", "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    void setPinned(List<String> keys) {
        p.edit().putString("pinned", android.text.TextUtils.join("\n", keys)).apply();
    }

    boolean isPinned(String key) {
        return pinned().contains(key);
    }

    void togglePinned(String key) {
        List<String> l = pinned();
        if (!l.remove(key)) l.add(key);
        setPinned(l);
    }

    boolean isWide(String key) {
        return set("wide").contains(key);
    }

    void toggleWide(String key) {
        toggle("wide", key);
    }

    // ---- anti-adicción ----

    boolean isDistracting(String key) {
        return set("distracting").contains(key);
    }

    void toggleDistracting(String key) {
        toggle("distracting", key);
    }

    boolean isHidden(String key) {
        return set("hidden").contains(key);
    }

    Set<String> hidden() {
        return set("hidden");
    }

    void toggleHidden(String key) {
        toggle("hidden", key);
    }

    int pauseSeconds() {
        return p.getInt("pause", 10);
    }

    void setPauseSeconds(int s) {
        p.edit().putInt("pause", s).apply();
    }

    // ---- contadores de hoy ----

    private void rollDay() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (!today.equals(counts.getString("_day", ""))) {
            counts.edit().clear().putString("_day", today).apply();
        }
    }

    void countLaunch(String key) {
        rollDay();
        counts.edit()
                .putInt(COUNT + key, counts.getInt(COUNT + key, 0) + 1)
                .putInt("_total", counts.getInt("_total", 0) + 1)
                .apply();
    }

    void countAvoided() {
        rollDay();
        counts.edit().putInt("_avoided", counts.getInt("_avoided", 0) + 1).apply();
    }

    int launches(String key) {
        rollDay();
        return counts.getInt(COUNT + key, 0);
    }

    int totalLaunches() {
        rollDay();
        return counts.getInt("_total", 0);
    }

    int avoided() {
        rollDay();
        return counts.getInt("_avoided", 0);
    }

    Map<String, Integer> allLaunches() {
        rollDay();
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, ?> e : counts.getAll().entrySet()) {
            if (e.getKey().startsWith(COUNT) && e.getValue() instanceof Integer) {
                out.put(e.getKey().substring(COUNT.length()), (Integer) e.getValue());
            }
        }
        return out;
    }

    // ---- util ----

    private Set<String> set(String name) {
        return new HashSet<>(p.getStringSet(name, new HashSet<>()));
    }

    private void toggle(String name, String key) {
        Set<String> s = set(name);
        if (!s.remove(key)) s.add(key);
        p.edit().putStringSet(name, s).apply();
    }
}
