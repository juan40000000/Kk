package com.juan.monocromo;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Process;
import android.provider.AlarmClock;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Datos para los mosaicos dinámicos: tiempo de uso, próxima alarma y batería. */
final class LiveData {

    /** Lo que muestra la cara trasera de un mosaico. */
    static final class Info {
        final String big;
        final String line;
        final int badge;

        Info(String big, String line, int badge) {
            this.big = big;
            this.line = line;
            this.badge = badge;
        }
    }

    private final Context context;
    private final Set<String> clockPackages = new HashSet<>();
    private String settingsPackage = "";

    /** Milisegundos en primer plano hoy, por paquete. Se rellena en segundo plano. */
    volatile Map<String, Long> usage = new HashMap<>();
    volatile long screenMs;
    volatile String alarm = "";
    volatile String battery = "";

    LiveData(Context c) {
        context = c.getApplicationContext();
        for (ResolveInfo ri : context.getPackageManager().queryIntentActivities(
                new Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)) {
            if (ri.activityInfo != null) clockPackages.add(ri.activityInfo.packageName);
        }
        ResolveInfo s = context.getPackageManager().resolveActivity(new Intent(Settings.ACTION_SETTINGS), 0);
        if (s != null && s.activityInfo != null) settingsPackage = s.activityInfo.packageName;
    }

    static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Recalcula uso, alarma y batería. Puede tardar: llamar fuera del hilo principal. */
    void refresh() {
        alarm = nextAlarm();
        battery = batteryText();
        if (hasUsageAccess(context)) computeUsage();
    }

    private void computeUsage() {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService("usagestats");
        if (usm == null) return;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        long now = System.currentTimeMillis();

        Map<String, Long> total = new HashMap<>();
        Map<String, Long> since = new HashMap<>();
        UsageEvents events;
        try {
            events = usm.queryEvents(start, now);
        } catch (RuntimeException e) {
            return;
        }
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(ev);
            String pkg = ev.getPackageName();
            int type = ev.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                since.put(pkg, ev.getTimeStamp());
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long from = since.remove(pkg);
                if (from != null) total.put(pkg, getOr(total, pkg) + ev.getTimeStamp() - from);
            }
        }
        for (Map.Entry<String, Long> open : since.entrySet()) {
            // Sigue abierta (normalmente es este launcher): cuenta hasta ahora.
            total.put(open.getKey(), getOr(total, open.getKey()) + now - open.getValue());
        }
        long screen = 0;
        for (Map.Entry<String, Long> e : total.entrySet()) {
            if (!e.getKey().equals(context.getPackageName())) screen += e.getValue();
        }
        usage = total;
        screenMs = screen;
    }

    private static long getOr(Map<String, Long> m, String k) {
        Long v = m.get(k);
        return v == null ? 0 : v;
    }

    private String nextAlarm() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
        if (next == null) return "";
        long t = next.getTriggerTime();
        boolean soon = t - System.currentTimeMillis() < 24 * 3600 * 1000L;
        String pattern = android.text.format.DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        return new SimpleDateFormat(soon ? pattern : "EEE " + pattern, new Locale("es")).format(new Date(t));
    }

    private String batteryText() {
        Intent b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "";
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return "";
        boolean charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        return Math.round(level * 100f / scale) + "%" + (charging ? " ⚡" : "");
    }

    /**
     * Qué mostrar detrás del mosaico de una app, o null si no hay nada.
     * Las apps distractoras nunca muestran el contenido de sus notificaciones.
     */
    Info infoFor(String pkg, int opensToday, boolean distracting, boolean showNotifications) {
        NotifListener.Info n = showNotifications ? NotifListener.INFO.get(pkg) : null;
        int badge = n == null || distracting ? 0 : n.count;
        if (n != null && !distracting) {
            String big = n.title.isEmpty() ? n.count + (n.count == 1 ? " nueva" : " nuevas") : n.title;
            String line = n.text + (n.count > 1 ? (n.text.isEmpty() ? "" : "\n") + "+" + (n.count - 1) + " más" : "");
            return new Info(big, line, badge);
        }
        if (clockPackages.contains(pkg) && !alarm.isEmpty()) {
            return new Info("⏰ " + alarm, "próxima alarma", badge);
        }
        if (pkg.equals(settingsPackage) && !battery.isEmpty()) {
            return new Info(battery, "batería", badge);
        }
        Long ms = usage.get(pkg);
        long minutes = ms == null ? 0 : ms / 60_000;
        if (minutes > 0 || opensToday > 0) {
            String big = minutes > 0 ? duration(minutes) : opensToday + (opensToday == 1 ? " vez" : " veces");
            String line = minutes > 0
                    ? "de uso hoy" + (opensToday > 0 ? " · " + opensToday + (opensToday == 1 ? " vez" : " veces") : "")
                    : "abierta hoy";
            return new Info(big, line, badge);
        }
        return badge > 0 ? new Info(badge + (badge == 1 ? " nueva" : " nuevas"), "", badge) : null;
    }

    static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        long h = minutes / 60, m = minutes % 60;
        return h + " h" + (m > 0 ? " " + m + " min" : "");
    }
}
