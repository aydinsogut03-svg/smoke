package com.aydinsogut.sigarasayaci;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ana ekran widget'ı: uygulamayı açmadan "+1 İçtim" kaydı. */
public class SmokeWidget extends AppWidgetProvider {

    static final String ACTION_ADD = "com.aydinsogut.sigarasayaci.WIDGET_ADD";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_ADD.equals(intent.getAction())) {
            int count = addCigarette(ctx);
            SharedPreferences p = prefs(ctx);
            int limit = p.getInt("limit", 0);
            String msg = "+1 kaydedildi · bugün " + count + (limit > 0 ? " / " + limit : "");
            if (limit > 0 && count > limit) msg += " (limit aşıldı!)";
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            updateAll(ctx);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("sigara", Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        return "t_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int countOf(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        return raw.split(",").length;
    }

    /** Uygulamadaki "+1 İçtim" ile aynı kaydı yapar; bugünkü sayıyı döner. */
    static int addCigarette(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        if (!p.contains("start_ts")) p.edit().putLong("start_ts", now).apply();
        long last = p.getLong("last_ts", 0);
        long gap = now - (last > 0 ? last : p.getLong("start_ts", now));
        String key = todayKey();
        String raw = p.getString(key, "");
        String updated = raw.isEmpty() ? String.valueOf(now) : raw + "," + now;
        p.edit()
                .putString(key, updated)
                .putLong("last_ts", now)
                .putLong("best_gap", Math.max(p.getLong("best_gap", 0), gap))
                .apply();
        return countOf(updated);
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SmokeWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews rv = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    private static RemoteViews build(Context ctx) {
        SharedPreferences p = prefs(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_smoke);

        int count = countOf(p.getString(todayKey(), ""));
        int limit = p.getInt("limit", 0);
        rv.setTextViewText(R.id.wCount, String.valueOf(count));
        rv.setTextViewText(R.id.wLimit, limit > 0 ? "/ " + limit : "");
        rv.setTextColor(R.id.wCount, ctx.getColor(limit > 0 && count > limit ? R.color.bad : R.color.text));

        long last = p.getLong("last_ts", 0);
        if (last > 0) {
            long base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - last);
            rv.setChronometer(R.id.wSince, base, null, true);
            rv.setTextViewText(R.id.wSinceLabel, "son sigaradan beri");
        } else {
            rv.setChronometer(R.id.wSince, SystemClock.elapsedRealtime(), null, false);
            rv.setTextViewText(R.id.wSinceLabel, "henüz kayıt yok");
        }

        Intent add = new Intent(ctx, SmokeWidget.class).setAction(ACTION_ADD);
        rv.setOnClickPendingIntent(R.id.wAdd, PendingIntent.getBroadcast(ctx, 1, add,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.wInfo, PendingIntent.getActivity(ctx, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return rv;
    }
}
