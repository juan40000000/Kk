package com.juan.monocromo;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lee las notificaciones activas para mostrarlas en los mosaicos dinámicos.
 * Solo funciona si el usuario le da acceso en los ajustes del teléfono.
 */
public class NotifListener extends NotificationListenerService {

    static final class Info {
        int count;
        String title;
        String text;
        long time;
    }

    /** Notificaciones por paquete. Se lee desde la pantalla de inicio. */
    static final Map<String, Info> INFO = new ConcurrentHashMap<>();
    static volatile Runnable onChange;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean isEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        String me = new ComponentName(c, NotifListener.class).flattenToString();
        for (String s : flat.split(":")) if (s.equals(me)) return true;
        return false;
    }

    @Override
    public void onListenerConnected() {
        rebuild();
    }

    @Override
    public void onListenerDisconnected() {
        INFO.clear();
        notifyChange();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        rebuild();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        rebuild();
    }

    private void rebuild() {
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (RuntimeException e) {
            return;
        }
        Map<String, Info> fresh = new HashMap<>();
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                Notification n = sbn.getNotification();
                if (n == null || sbn.isOngoing() || !sbn.isClearable()) continue;
                if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                Info i = fresh.get(sbn.getPackageName());
                if (i == null) {
                    i = new Info();
                    fresh.put(sbn.getPackageName(), i);
                }
                i.count++;
                if (sbn.getPostTime() >= i.time) {
                    Bundle extras = n.extras;
                    i.time = sbn.getPostTime();
                    i.title = str(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE));
                    i.text = str(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT));
                }
            }
        }
        INFO.clear();
        INFO.putAll(fresh);
        notifyChange();
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static void notifyChange() {
        Runnable r = onChange;
        if (r != null) MAIN.post(r);
    }
}
