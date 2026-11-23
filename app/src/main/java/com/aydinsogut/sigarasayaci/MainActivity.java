package com.aydinsogut.sigarasayaci;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Sigara Sayacı: her dokunuşta bugünün sayısını bir artırır, günlük limit ve
 * son 30 günün geçmişini gösterir. Veriler cihazda SharedPreferences içinde tutulur.
 *
 * Kayıt biçimi:
 *   "t_yyyy-MM-dd" -> o gün içilen her sigaranın zaman damgaları, virgülle ayrılmış
 *   "limit"        -> günlük limit (0 = limit yok)
 *   "price"        -> paket fiyatı (TL, 0 = gösterme)
 *   "per_pack"     -> paketteki sigara sayısı
 *   "last_ts"      -> son sigaranın zamanı
 */
public class MainActivity extends Activity {

    private static final Locale TR = new Locale("tr", "TR");
    private static final String KEY_PREFIX = "t_";

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private TextView dateText, countText, limitText, statusText, sinceText;
    private TextView weekAvgText, monthText, costText, costLabel;
    private ProgressBar limitProgress;
    private LinearLayout historyList;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("sigara", MODE_PRIVATE);

        dateText = findViewById(R.id.dateText);
        countText = findViewById(R.id.countText);
        limitText = findViewById(R.id.limitText);
        statusText = findViewById(R.id.statusText);
        sinceText = findViewById(R.id.sinceText);
        weekAvgText = findViewById(R.id.weekAvgText);
        monthText = findViewById(R.id.monthText);
        costText = findViewById(R.id.costText);
        costLabel = findViewById(R.id.costLabel);
        limitProgress = findViewById(R.id.limitProgress);
        historyList = findViewById(R.id.historyList);

        findViewById(R.id.addButton).setOnClickListener(this::onAdd);
        findViewById(R.id.undoButton).setOnClickListener(v -> onUndo());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    // ---------- Veri ----------

    private String keyFor(Calendar c) {
        return KEY_PREFIX + keyFormat.format(c.getTime());
    }

    private List<Long> stampsFor(Calendar c) {
        List<Long> list = new ArrayList<>();
        String raw = prefs.getString(keyFor(c), "");
        if (raw.isEmpty()) return list;
        for (String s : raw.split(",")) {
            try {
                list.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
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

    // ---------- Eylemler ----------

    private void onAdd(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Calendar today = Calendar.getInstance();
        List<Long> list = stampsFor(today);
        long now = System.currentTimeMillis();
        list.add(now);
        saveStamps(today, list);
        prefs.edit().putLong("last_ts", now).apply();

        int limit = prefs.getInt("limit", 0);
        if (limit > 0 && list.size() == limit) {
            Toast.makeText(this, "Bugünkü limitine ulaştın.", Toast.LENGTH_LONG).show();
        } else if (limit > 0 && list.size() > limit) {
            Toast.makeText(this, "Limiti aştın! (" + list.size() + "/" + limit + ")", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void onUndo() {
        Calendar today = Calendar.getInstance();
        List<Long> list = stampsFor(today);
        if (list.isEmpty()) {
            Toast.makeText(this, "Bugün geri alınacak kayıt yok.", Toast.LENGTH_SHORT).show();
            return;
        }
        list.remove(list.size() - 1);
        saveStamps(today, list);
        prefs.edit().putLong("last_ts", findLastStamp()).apply();
        Toast.makeText(this, "Son kayıt geri alındı.", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private long findLastStamp() {
        for (int i = 0; i < 365; i++) {
            List<Long> l = stampsFor(daysAgo(i));
            if (!l.isEmpty()) return l.get(l.size() - 1);
        }
        return 0;
    }

    // ---------- Ekran ----------

    private void refresh() {
        Calendar today = Calendar.getInstance();
        int count = countFor(today);
        int limit = prefs.getInt("limit", 0);

        dateText.setText(new SimpleDateFormat("d MMMM yyyy, EEEE", TR).format(today.getTime()));
        countText.setText(String.valueOf(count));

        int good = getColor(R.color.good), warn = getColor(R.color.warn), bad = getColor(R.color.bad);
        int muted = getColor(R.color.muted);

        if (limit > 0) {
            limitText.setText("/ " + limit);
            limitProgress.setVisibility(View.VISIBLE);
            limitProgress.setProgress(Math.min(100, count * 100 / limit));
            int color;
            String status;
            if (count < limit) {
                int left = limit - count;
                color = count >= limit * 0.75 ? warn : good;
                status = "Limitine " + left + " sigara kaldı";
            } else if (count == limit) {
                color = warn;
                status = "Bugünkü limitine ulaştın";
            } else {
                color = bad;
                status = "Limiti " + (count - limit) + " sigara aştın";
            }
            limitProgress.setProgressTintList(ColorStateList.valueOf(color));
            statusText.setTextColor(color);
            statusText.setText(status);
            countText.setTextColor(count > limit ? bad : getColor(R.color.text));
        } else {
            limitText.setText("");
            limitProgress.setVisibility(View.GONE);
            statusText.setTextColor(muted);
            statusText.setText("Günlük limit belirlemek için Ayarlar'a dokun");
            countText.setTextColor(getColor(R.color.text));
        }

        long last = prefs.getLong("last_ts", 0);
        if (last > 0) {
            sinceText.setText("Son sigaradan beri: " + formatDuration(System.currentTimeMillis() - last));
        } else {
            sinceText.setText("Henüz kayıt yok");
        }

        // Son 7 günün ortalaması (bugün dahil)
        int weekSum = 0;
        for (int i = 0; i < 7; i++) weekSum += countFor(daysAgo(i));
        weekAvgText.setText(String.format(TR, "%.1f", weekSum / 7f));

        // Bu ay toplamı
        int monthSum = 0;
        int dayOfMonth = today.get(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < dayOfMonth; i++) monthSum += countFor(daysAgo(i));
        monthText.setText(String.valueOf(monthSum));

        // Harcama
        float price = prefs.getFloat("price", 0f);
        int perPack = prefs.getInt("per_pack", 20);
        if (price > 0 && perPack > 0) {
            costText.setText(String.format(TR, "%.0f ₺", count * price / perPack));
            costLabel.setText("Bugün harcanan");
        } else {
            costText.setText(String.valueOf(countFor(daysAgo(1))));
            costLabel.setText("Dün");
        }

        renderHistory(limit);
    }

    private void renderHistory(int limit) {
        historyList.removeAllViews();
        SimpleDateFormat fmt = new SimpleDateFormat("d MMMM, EEE", TR);
        int max = 1;
        int[] counts = new int[30];
        for (int i = 0; i < 30; i++) {
            counts[i] = countFor(daysAgo(i));
            max = Math.max(max, counts[i]);
        }
        if (limit > 0) max = Math.max(max, limit);

        float dp = getResources().getDisplayMetrics().density;
        int textColor = getColor(R.color.text), muted = getColor(R.color.muted);
        int accent = getColor(R.color.accent), bad = getColor(R.color.bad), track = getColor(R.color.track);

        for (int i = 0; i < 30; i++) {
            Calendar c = daysAgo(i);
            int n = counts[i];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp));

            TextView date = new TextView(this);
            date.setText(i == 0 ? "Bugün" : i == 1 ? "Dün" : fmt.format(c.getTime()));
            date.setTextColor(n == 0 ? muted : textColor);
            date.setTextSize(14);
            row.addView(date, new LinearLayout.LayoutParams((int) (120 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));

            // Basit çubuk grafik
            LinearLayout barWrap = new LinearLayout(this);
            barWrap.setBackgroundColor(track);
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, (int) (8 * dp), 1f);
            wrapLp.setMarginEnd((int) (12 * dp));
            View bar = new View(this);
            bar.setBackgroundColor(limit > 0 && n > limit ? bad : accent);
            barWrap.setWeightSum(max);
            barWrap.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, n));
            row.addView(barWrap, wrapLp);

            TextView num = new TextView(this);
            num.setText(String.valueOf(n));
            num.setTextColor(limit > 0 && n > limit ? bad : textColor);
            num.setTextSize(15);
            num.setGravity(Gravity.END);
            num.getPaint().setFakeBoldText(true);
            row.addView(num, new LinearLayout.LayoutParams((int) (32 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));

            historyList.addView(row);
        }
    }

    private String formatDuration(long ms) {
        long min = Math.max(0, ms / 60_000);
        long days = min / (60 * 24);
        long hours = (min / 60) % 24;
        long mins = min % 60;
        if (days > 0) return days + " gün " + hours + " sa";
        if (hours > 0) return hours + " sa " + mins + " dk";
        if (mins > 0) return mins + " dk";
        return "az önce";
    }

    // ---------- Ayarlar ----------

    private void showSettings() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        box.setPadding(pad, (int) (8 * dp), pad, 0);

        EditText limitInput = addField(box, "Günlük limit (0 = limit yok)",
                String.valueOf(prefs.getInt("limit", 0)), InputType.TYPE_CLASS_NUMBER);
        float price = prefs.getFloat("price", 0f);
        EditText priceInput = addField(box, "Paket fiyatı (₺, 0 = gösterme)",
                price > 0 ? String.format(Locale.US, "%.2f", price) : "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText packInput = addField(box, "Paketteki sigara sayısı",
                String.valueOf(prefs.getInt("per_pack", 20)), InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Ayarlar")
                .setView(box)
                .setPositiveButton("Kaydet", (d, w) -> {
                    prefs.edit()
                            .putInt("limit", parseInt(limitInput, 0))
                            .putFloat("price", parseFloat(priceInput))
                            .putInt("per_pack", Math.max(1, parseInt(packInput, 20)))
                            .apply();
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton("Verileri sil", (d, w) -> confirmReset())
                .show();
    }

    private EditText addField(LinearLayout box, String label, String value, int type) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
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
                .setMessage("Bütün sigara kayıtların kalıcı olarak silinecek. Ayarların korunur.")
                .setPositiveButton("Sil", (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    for (String k : prefs.getAll().keySet()) {
                        if (k.startsWith(KEY_PREFIX)) e.remove(k);
                    }
                    e.remove("last_ts").apply();
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
