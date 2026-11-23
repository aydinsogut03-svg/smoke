package com.aydinsogut.sigarasayaci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Sigara Sayacı.
 *
 * Kayıt biçimi (SharedPreferences "sigara"):
 *   "t_yyyy-MM-dd" -> o gün içilen sigaraların zaman damgaları (virgülle)
 *   "start_ts"     -> takibin başladığı an (tasarruf hesabı buradan başlar)
 *   "last_ts"      -> son sigaranın zamanı
 *   "best_gap"     -> iki sigara arasındaki en uzun süre (ms)
 *   "limit", "price", "per_pack", "baseline" -> ayarlar
 *   "ach_<id>"     -> kazanılmış başarılar
 */
public class MainActivity extends Activity {

    private static final Locale TR = new Locale("tr", "TR");
    private static final String KEY_PREFIX = "t_";
    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    /** Bir sigaranın ömürden götürdüğü tahmini süre (dk) — yaygın kullanılan tahmin. */
    private static final int LIFE_MIN_PER_CIG = 11;
    /** Bir sigarayı içmek için harcanan ortalama süre (dk). */
    private static final int SMOKE_MIN_PER_CIG = 6;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private TextView tDays, tHours, tMins, tSecs, lastText, healthText;
    private TextView countText, limitText, statusText, todayCostText;
    private TextView avoidedText, lifeGainText, savedText, priceText, achTitle;
    private ProgressBar limitProgress;
    private LinearLayout ringRow, gainList, lossList, weekChart, achGrid;

    private int tick = 0;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            if (++tick % 30 == 0) refresh();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Başarılar ----------

    private static final int K_TIME = 0, K_MONEY = 1, K_AVOID = 2, K_LIMIT = 3;

    private static final class Ach {
        final String id, emoji, title, desc;
        final int kind;
        final double target;

        Ach(String id, int kind, double target, String emoji, String title, String desc) {
            this.id = id; this.kind = kind; this.target = target;
            this.emoji = emoji; this.title = title; this.desc = desc;
        }
    }

    private static final Ach[] ACHS = {
            new Ach("t20m", K_TIME, 20 * MIN, "🌱", "İlk Adım", "20 dk sigarasız"),
            new Ach("t1h", K_TIME, HOUR, "⏱", "1 Saat", "1 saat sigarasız"),
            new Ach("t3h", K_TIME, 3 * HOUR, "💪", "Güçlü", "3 saat sigarasız"),
            new Ach("t8h", K_TIME, 8 * HOUR, "🌬", "Temiz Hava", "8 saat sigarasız"),
            new Ach("t12h", K_TIME, 12 * HOUR, "🌙", "Yarım Gün", "12 saat sigarasız"),
            new Ach("t24h", K_TIME, DAY, "☀", "Tam Gün", "24 saat sigarasız"),
            new Ach("t48h", K_TIME, 2 * DAY, "👃", "2 Gün", "Tat ve koku dönüyor"),
            new Ach("t72h", K_TIME, 3 * DAY, "🌿", "3 Gün", "Nefes rahatlıyor"),
            new Ach("t1w", K_TIME, 7 * DAY, "🏅", "1 Hafta", "7 gün sigarasız"),
            new Ach("t2w", K_TIME, 14 * DAY, "🥈", "2 Hafta", "14 gün sigarasız"),
            new Ach("t1mo", K_TIME, 30 * DAY, "🥇", "1 Ay", "30 gün sigarasız"),
            new Ach("t3mo", K_TIME, 90 * DAY, "🏆", "3 Ay", "90 gün sigarasız"),
            new Ach("a20", K_AVOID, 20, "📦", "Bir Paket", "20 sigara içmedin"),
            new Ach("a100", K_AVOID, 100, "🚭", "Yüzlük", "100 sigara içmedin"),
            new Ach("a500", K_AVOID, 500, "🔥", "Beş Yüz", "500 sigara içmedin"),
            new Ach("a1000", K_AVOID, 1000, "⭐", "Bin", "1000 sigara içmedin"),
            new Ach("m100", K_MONEY, 100, "💰", "İlk 100 ₺", "100 ₺ tasarruf"),
            new Ach("m500", K_MONEY, 500, "💵", "500 ₺", "500 ₺ tasarruf"),
            new Ach("m1000", K_MONEY, 1000, "💎", "1.000 ₺", "1.000 ₺ tasarruf"),
            new Ach("m5000", K_MONEY, 5000, "👑", "5.000 ₺", "5.000 ₺ tasarruf"),
            new Ach("l1", K_LIMIT, 1, "🎯", "Limitte", "1 gün limiti aşmadın"),
            new Ach("l7", K_LIMIT, 7, "🛡", "Disiplin", "7 gün limiti aşmadın"),
            new Ach("l30", K_LIMIT, 30, "🧠", "İrade", "30 gün limiti aşmadın"),
    };

    /** Sigarasız geçen süreye göre vücutta olanlar (genel tahminler). */
    private static final long[] HEALTH_AT = {20 * MIN, 8 * HOUR, DAY, 2 * DAY, 3 * DAY, 14 * DAY, 30 * DAY, 365 * DAY};
    private static final String[] HEALTH_TXT = {
            "Nabız ve tansiyon normale dönmeye başlar",
            "Kandaki karbonmonoksit yarıya iner",
            "Kandaki karbonmonoksit atılır",
            "Tat ve koku duyusu güçlenmeye başlar",
            "Bronşlar gevşer, nefes almak kolaylaşır",
            "Kan dolaşımı iyileşir",
            "Öksürük ve nefes darlığı azalır",
            "Kalp hastalığı riski yarıya iner",
    };

    // ---------- Yaşam döngüsü ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("sigara", MODE_PRIVATE);
        initStart();

        tDays = findViewById(R.id.tDays);
        tHours = findViewById(R.id.tHours);
        tMins = findViewById(R.id.tMins);
        tSecs = findViewById(R.id.tSecs);
        lastText = findViewById(R.id.lastText);
        healthText = findViewById(R.id.healthText);
        countText = findViewById(R.id.countText);
        limitText = findViewById(R.id.limitText);
        statusText = findViewById(R.id.statusText);
        todayCostText = findViewById(R.id.todayCostText);
        avoidedText = findViewById(R.id.avoidedText);
        lifeGainText = findViewById(R.id.lifeGainText);
        savedText = findViewById(R.id.savedText);
        priceText = findViewById(R.id.priceText);
        achTitle = findViewById(R.id.achTitle);
        limitProgress = findViewById(R.id.limitProgress);
        ringRow = findViewById(R.id.ringRow);
        gainList = findViewById(R.id.gainList);
        lossList = findViewById(R.id.lossList);
        weekChart = findViewById(R.id.weekChart);
        achGrid = findViewById(R.id.achGrid);

        findViewById(R.id.addButton).setOnClickListener(this::onAdd);
        findViewById(R.id.undoButton).setOnClickListener(v -> onUndo());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSettings());
        findViewById(R.id.moneyCard).setOnClickListener(v -> showPriceDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    /** İlk açılışta takibin başlangıcını belirler (eski kayıtlar varsa en eskisinden). */
    private void initStart() {
        if (prefs.contains("start_ts")) return;
        long start = System.currentTimeMillis();
        for (String k : prefs.getAll().keySet()) {
            if (!k.startsWith(KEY_PREFIX)) continue;
            for (long t : parse(prefs.getString(k, ""))) start = Math.min(start, t);
        }
        prefs.edit().putLong("start_ts", start).apply();
    }

    // ---------- Veri ----------

    private String keyFor(Calendar c) {
        return KEY_PREFIX + keyFormat.format(c.getTime());
    }

    private static List<Long> parse(String raw) {
        List<Long> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        for (String s : raw.split(",")) {
            try {
                list.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private List<Long> stampsFor(Calendar c) {
        return parse(prefs.getString(keyFor(c), ""));
    }

    private void saveStamps(Calendar c, List<Long> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        SharedPreferences.Editor e = prefs.edit();
        if (list.isEmpty()) e.remove(keyFor(c));
        else e.putString(keyFor(c), sb.toString());
        e.apply();
    }

    private int countFor(Calendar c) {
        return stampsFor(c).size();
    }

    private Calendar daysAgo(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return c;
    }

    private int totalSmoked() {
        int sum = 0;
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith(KEY_PREFIX)) sum += parse(prefs.getString(k, "")).size();
        }
        return sum;
    }

    private float price() {
        float p = prefs.getFloat("price", 100f);
        return p > 0 ? p : 100f;
    }

    private int perPack() {
        return Math.max(1, prefs.getInt("per_pack", 20));
    }

    private int baseline() {
        return Math.max(1, prefs.getInt("baseline", 20));
    }

    private float pricePerCig() {
        return price() / perPack();
    }

    private long lastOrStart() {
        long last = prefs.getLong("last_ts", 0);
        return last > 0 ? last : prefs.getLong("start_ts", System.currentTimeMillis());
    }

    private long currentGap() {
        return Math.max(0, System.currentTimeMillis() - lastOrStart());
    }

    private long bestGap() {
        return Math.max(prefs.getLong("best_gap", 0), currentGap());
    }

    /** Başlangıçtan beri, eski alışkanlığa göre içilmeyen sigara sayısı. */
    private double avoided(int total) {
        double days = (System.currentTimeMillis() - prefs.getLong("start_ts", System.currentTimeMillis())) / (double) DAY;
        return Math.max(0, days * baseline() - total);
    }

    /** Başlangıçtan sonraki, bugün hariç, limiti aşmadan geçen gün sayısı. */
    private int daysWithinLimit() {
        int limit = prefs.getInt("limit", 0);
        if (limit <= 0) return 0;
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(prefs.getLong("start_ts", System.currentTimeMillis()));
        int n = 0;
        for (int i = 1; i < 400; i++) {
            Calendar c = daysAgo(i);
            if (c.before(start) && !sameDay(c, start)) break;
            if (countFor(c) <= limit) n++;
        }
        return n;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---------- Eylemler ----------

    private void onAdd(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        long now = System.currentTimeMillis();
        long gap = now - lastOrStart();
        Calendar today = Calendar.getInstance();
        List<Long> list = stampsFor(today);
        list.add(now);
        saveStamps(today, list);
        prefs.edit()
                .putLong("best_gap", Math.max(prefs.getLong("best_gap", 0), gap))
                .putLong("last_ts", now)
                .apply();

        int limit = prefs.getInt("limit", 0);
        if (limit > 0 && list.size() == limit) {
            toast("Bugünkü limitine ulaştın.");
        } else if (limit > 0 && list.size() > limit) {
            toast("Limiti aştın! (" + list.size() + "/" + limit + ")");
        } else if (gap >= HOUR) {
            toast("Son sigaradan beri " + shortDuration(gap) + " dayandın 👏");
        }
        refresh();
    }

    private void onUndo() {
        Calendar today = Calendar.getInstance();
        List<Long> list = stampsFor(today);
        if (list.isEmpty()) {
            toast("Bugün geri alınacak kayıt yok.");
            return;
        }
        list.remove(list.size() - 1);
        saveStamps(today, list);
        prefs.edit().putLong("last_ts", findLastStamp()).apply();
        toast("Son kayıt geri alındı.");
        refresh();
    }

    private long findLastStamp() {
        long last = 0;
        for (String k : prefs.getAll().keySet()) {
            if (!k.startsWith(KEY_PREFIX)) continue;
            for (long t : parse(prefs.getString(k, ""))) last = Math.max(last, t);
        }
        return last;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- Ekran ----------

    private void updateTimer() {
        long gap = currentGap();
        tDays.setText(String.valueOf(gap / DAY));
        tHours.setText(two((gap % DAY) / HOUR));
        tMins.setText(two((gap % HOUR) / MIN));
        tSecs.setText(two((gap % MIN) / 1000));
    }

    private static String two(long n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private void refresh() {
        updateTimer();
        Calendar today = Calendar.getInstance();
        int count = countFor(today);
        int limit = prefs.getInt("limit", 0);
        int total = totalSmoked();
        double avoided = avoided(total);
        float ppc = pricePerCig();
        double saved = avoided * ppc;
        long gap = currentGap();

        // Son sigara bilgisi
        long last = prefs.getLong("last_ts", 0);
        if (last > 0) {
            Calendar lc = Calendar.getInstance();
            lc.setTimeInMillis(last);
            String day = sameDay(lc, today) ? "bugün"
                    : sameDay(lc, daysAgo(1)) ? "dün"
                    : new SimpleDateFormat("d MMMM", TR).format(lc.getTime());
            lastText.setText("Son sigara: " + day + " " + new SimpleDateFormat("HH:mm", TR).format(lc.getTime()));
        } else {
            lastText.setText("Henüz sigara kaydı yok");
        }

        // Sağlık bilgisi
        String reached = null, next = null;
        long nextAt = 0;
        for (int i = 0; i < HEALTH_AT.length; i++) {
            if (gap >= HEALTH_AT[i]) reached = HEALTH_TXT[i];
            else { next = HEALTH_TXT[i]; nextAt = HEALTH_AT[i]; break; }
        }
        StringBuilder h = new StringBuilder();
        if (reached != null) h.append("✓ ").append(reached);
        if (next != null) {
            if (h.length() > 0) h.append("\n");
            h.append(shortDuration(nextAt - gap)).append(" sonra: ").append(next);
        }
        healthText.setText(h.toString());

        // Bugün
        int good = getColor(R.color.good), warn = getColor(R.color.warn), bad = getColor(R.color.bad);
        countText.setText(String.valueOf(count));
        if (limit > 0) {
            limitText.setText("/ " + limit);
            limitProgress.setVisibility(View.VISIBLE);
            limitProgress.setProgress(Math.min(100, count * 100 / limit));
            int color;
            String status;
            if (count < limit) {
                color = count >= limit * 0.75 ? warn : good;
                status = "Limitine " + (limit - count) + " kaldı";
            } else if (count == limit) {
                color = warn;
                status = "Limitine ulaştın";
            } else {
                color = bad;
                status = "Limiti " + (count - limit) + " aştın";
            }
            limitProgress.setProgressTintList(ColorStateList.valueOf(color));
            statusText.setTextColor(color);
            statusText.setText(status);
            countText.setTextColor(count > limit ? bad : getColor(R.color.text));
        } else {
            limitText.setText("");
            limitProgress.setVisibility(View.GONE);
            statusText.setTextColor(getColor(R.color.muted));
            statusText.setText("Limit için ⚙ Ayarlar");
            countText.setTextColor(getColor(R.color.text));
        }
        int month = 0;
        for (int i = 0; i < today.get(Calendar.DAY_OF_MONTH); i++) month += countFor(daysAgo(i));
        todayCostText.setText("Bugün " + money(count * ppc) + " · Bu ay " + money(month * ppc));

        // Kartlar
        avoidedText.setText(String.valueOf((long) avoided));
        lifeGainText.setText(shortDuration((long) (avoided * LIFE_MIN_PER_CIG * MIN)));
        savedText.setText(money(saved));
        priceText.setText(String.format(TR, "Paket %s · %d adet · ✎ değiştir", money(price()), perPack()));

        renderGainLoss(total, avoided, saved, ppc);
        renderWeek(limit);
        renderAchievements(avoided, saved);
    }

    private void renderGainLoss(int total, double avoided, double saved, float ppc) {
        gainList.removeAllViews();
        lossList.removeAllViews();
        header(gainList, "✅ Kazandık", getColor(R.color.good));
        row(gainList, "Tasarruf", money(saved));
        row(gainList, "İçilmeyen sigara", String.valueOf((long) avoided));
        row(gainList, "Geri kazanılan ömür", "≈ " + shortDuration((long) (avoided * LIFE_MIN_PER_CIG * MIN)));
        row(gainList, "En uzun ara", shortDuration(bestGap()));

        header(lossList, "❌ Kaybettik", getColor(R.color.bad));
        row(lossList, "Harcanan para", money(total * ppc));
        row(lossList, "İçilen sigara", String.valueOf(total));
        row(lossList, "Kaybedilen ömür", "≈ " + shortDuration((long) total * LIFE_MIN_PER_CIG * MIN));
        row(lossList, "Sigaraya giden zaman", shortDuration((long) total * SMOKE_MIN_PER_CIG * MIN));
    }

    private void header(LinearLayout box, String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);
    }

    private void row(LinearLayout box, String label, String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.text));
        v.setTextSize(17);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(10), 0, 0);
        box.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(R.color.muted));
        l.setTextSize(12);
        box.addView(l);
    }

    private void renderWeek(int limit) {
        weekChart.removeAllViews();
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", TR);
        int[] counts = new int[7];
        int max = Math.max(1, limit);
        for (int i = 0; i < 7; i++) {
            counts[i] = countFor(daysAgo(6 - i));
            max = Math.max(max, counts[i]);
        }
        int maxBar = dp(80);
        for (int i = 0; i < 7; i++) {
            int n = counts[i];
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            TextView num = new TextView(this);
            num.setText(String.valueOf(n));
            num.setTextSize(12);
            num.setTextColor(getColor(R.color.text));
            col.addView(num);

            View bar = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(6));
            int color = limit > 0 && n > limit ? getColor(R.color.bad) : getColor(R.color.accent);
            g.setColor(i == 6 ? color : (color & 0x00FFFFFF) | 0x99000000);
            bar.setBackground(g);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(18), Math.max(dp(3), maxBar * n / max));
            blp.topMargin = dp(2);
            col.addView(bar, blp);

            TextView label = new TextView(this);
            label.setText(i == 6 ? "Bugün" : dayFmt.format(daysAgo(6 - i).getTime()));
            label.setTextSize(11);
            label.setTextColor(getColor(R.color.muted));
            label.setPadding(0, dp(4), 0, 0);
            col.addView(label);

            weekChart.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private double valueFor(int kind, double avoided, double saved, int limitDays) {
        switch (kind) {
            case K_TIME: return bestGap();
            case K_MONEY: return saved;
            case K_AVOID: return avoided;
            default: return limitDays;
        }
    }

    private void renderAchievements(double avoided, double saved) {
        int limitDays = daysWithinLimit();
        SharedPreferences.Editor e = prefs.edit();
        int unlockedCount = 0;
        boolean[] unlocked = new boolean[ACHS.length];
        float[] prog = new float[ACHS.length];
        for (int i = 0; i < ACHS.length; i++) {
            Ach a = ACHS[i];
            double v = valueFor(a.kind, avoided, saved, limitDays);
            prog[i] = (float) Math.min(1, v / a.target);
            boolean was = prefs.getBoolean("ach_" + a.id, false);
            unlocked[i] = was || v >= a.target;
            if (unlocked[i] && !was) {
                e.putBoolean("ach_" + a.id, true);
                Toast.makeText(this, "🏆 Yeni başarı: " + a.emoji + " " + a.title, Toast.LENGTH_LONG).show();
            }
            if (unlocked[i]) unlockedCount++;
        }
        e.apply();
        achTitle.setText("Başarılar  " + unlockedCount + "/" + ACHS.length);

        // Sıradaki hedef halkaları: süre, para, içilmeyen sigara
        ringRow.removeAllViews();
        int[] kinds = {K_TIME, K_MONEY, K_AVOID};
        for (int k = 0; k < kinds.length; k++) {
            int idx = -1;
            for (int i = 0; i < ACHS.length; i++) {
                if (ACHS[i].kind == kinds[k] && !unlocked[i]) { idx = i; break; }
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackgroundResource(R.drawable.card_bg);
            card.setPadding(dp(8), dp(12), dp(8), dp(12));

            FrameLayout ringBox = new FrameLayout(this);
            RingView ring = new RingView(this, getColor(R.color.track), getColor(R.color.accent));
            ring.setProgress(idx >= 0 ? prog[idx] : 1f);
            ringBox.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64)));
            TextView emo = new TextView(this);
            emo.setText(idx >= 0 ? ACHS[idx].emoji : "🏆");
            emo.setTextSize(24);
            emo.setGravity(Gravity.CENTER);
            ringBox.addView(emo, new FrameLayout.LayoutParams(dp(64), dp(64)));
            card.addView(ringBox);

            TextView pct = new TextView(this);
            pct.setText(idx >= 0 ? "%" + (int) (prog[idx] * 100) : "Tamam");
            pct.setTextColor(getColor(R.color.accent));
            pct.setTypeface(Typeface.DEFAULT_BOLD);
            pct.setTextSize(14);
            card.addView(pct);

            TextView desc = new TextView(this);
            desc.setText(idx >= 0 ? ACHS[idx].desc : "Hepsi kazanıldı");
            desc.setTextColor(getColor(R.color.text));
            desc.setTextSize(12);
            desc.setGravity(Gravity.CENTER);
            card.addView(desc);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (k > 0) lp.setMarginStart(dp(5));
            if (k < kinds.length - 1) lp.setMarginEnd(dp(5));
            ringRow.addView(card, lp);
        }

        // Başarı ızgarası (3 sütun)
        achGrid.removeAllViews();
        LinearLayout rowView = null;
        for (int i = 0; i < ACHS.length; i++) {
            if (i % 3 == 0) {
                rowView = new LinearLayout(this);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = dp(10);
                achGrid.addView(rowView, rlp);
            }
            rowView.addView(badge(ACHS[i], unlocked[i], prog[i], i % 3));
        }
        // Son satırı hizalamak için boşluk
        int rem = ACHS.length % 3;
        if (rem != 0 && rowView != null) {
            for (int j = rem; j < 3; j++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1f);
                sp.setMarginStart(dp(5));
                rowView.addView(spacer, sp);
            }
        }
    }

    private View badge(Ach a, boolean unlocked, float prog, int col) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(6), dp(12), dp(6), dp(12));

        TextView emo = new TextView(this);
        emo.setText(a.emoji);
        emo.setTextSize(30);
        card.addView(emo);

        TextView title = new TextView(this);
        title.setText(a.title);
        title.setTextColor(getColor(R.color.text));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(unlocked ? a.desc : a.desc + "\n%" + (int) (prog * 100));
        desc.setTextColor(getColor(R.color.muted));
        desc.setTextSize(11);
        desc.setGravity(Gravity.CENTER);
        card.addView(desc);

        if (!unlocked) card.setAlpha(0.45f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (col > 0) lp.setMarginStart(dp(5));
        if (col < 2) lp.setMarginEnd(dp(5));
        card.setLayoutParams(lp);
        return card;
    }

    // ---------- Biçimlendirme ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String money(double v) {
        return String.format(TR, "%,.2f ₺", v);
    }

    private static String shortDuration(long ms) {
        long min = Math.max(0, ms / MIN);
        long days = min / (60 * 24);
        long hours = (min / 60) % 24;
        long mins = min % 60;
        if (days > 0) return days + " gün " + hours + " sa";
        if (hours > 0) return hours + " sa " + mins + " dk";
        if (mins > 0) return mins + " dk";
        return "< 1 dk";
    }

    // ---------- Ayarlar ----------

    private void showPriceDialog() {
        LinearLayout box = dialogBox();
        EditText priceInput = addField(box, "Paket fiyatı (₺)",
                String.format(Locale.US, "%.2f", price()),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText packInput = addField(box, "Paketteki sigara sayısı",
                String.valueOf(perPack()), InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Sigara fiyatı")
                .setView(box)
                .setPositiveButton("Kaydet", (d, w) -> {
                    float p = parseFloat(priceInput);
                    prefs.edit()
                            .putFloat("price", p > 0 ? p : price())
                            .putInt("per_pack", Math.max(1, parseInt(packInput, 20)))
                            .apply();
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void showSettings() {
        LinearLayout box = dialogBox();
        EditText limitInput = addField(box, "Günlük limit (0 = limit yok)",
                String.valueOf(prefs.getInt("limit", 0)), InputType.TYPE_CLASS_NUMBER);
        EditText baseInput = addField(box, "Eskiden günde kaç sigara içiyordun? (tasarruf buna göre hesaplanır)",
                String.valueOf(baseline()), InputType.TYPE_CLASS_NUMBER);
        EditText priceInput = addField(box, "Paket fiyatı (₺)",
                String.format(Locale.US, "%.2f", price()),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText packInput = addField(box, "Paketteki sigara sayısı",
                String.valueOf(perPack()), InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Ayarlar")
                .setView(box)
                .setPositiveButton("Kaydet", (d, w) -> {
                    float p = parseFloat(priceInput);
                    prefs.edit()
                            .putInt("limit", parseInt(limitInput, 0))
                            .putInt("baseline", Math.max(1, parseInt(baseInput, 20)))
                            .putFloat("price", p > 0 ? p : price())
                            .putInt("per_pack", Math.max(1, parseInt(packInput, 20)))
                            .apply();
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton("Verileri sil", (d, w) -> confirmReset())
                .show();
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        return box;
    }

    private EditText addField(LinearLayout box, String label, String value, int type) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setPadding(0, dp(12), 0, 0);
        box.addView(tv);
        EditText et = new EditText(this);
        et.setInputType(type);
        et.setText(value);
        et.setSelectAllOnFocus(true);
        box.addView(et);
        return et;
    }

    private int parseInt(EditText et, int def) {
        try {
            return Math.max(0, Integer.parseInt(et.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private float parseFloat(EditText et) {
        try {
            return Math.max(0f, Float.parseFloat(et.getText().toString().trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Tüm veriler silinsin mi?")
                .setMessage("Bütün sigara kayıtların, başarıların ve tasarruf hesabın sıfırlanacak. Ayarların korunur.")
                .setPositiveButton("Sil", (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    for (String k : prefs.getAll().keySet()) {
                        if (k.startsWith(KEY_PREFIX) || k.startsWith("ach_")) e.remove(k);
                    }
                    e.remove("last_ts").remove("best_gap")
                            .putLong("start_ts", System.currentTimeMillis())
                            .apply();
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
