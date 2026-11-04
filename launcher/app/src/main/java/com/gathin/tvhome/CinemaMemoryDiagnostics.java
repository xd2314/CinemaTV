package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit, bounded own-process diagnostics. Missing measurements are never reported as zero. */
final class CinemaMemoryDiagnostics implements AutoCloseable {
    static final int MAX_SAMPLES = 24;
    static final long WINDOW_MS = 120000, INTERVAL_MS = 5000;
    interface SampleReader { JSONObject read(Activity activity, JSONObject resources) throws Exception; }
    private final Activity activity;
    private final Supplier<JSONObject> resources;
    private final SampleReader reader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = () -> take(true);
    private JSONObject record;
    private boolean running, busy, closed, exporting;
    private long deadline;
    private AlertDialog dialog;
    private TextView text;

    CinemaMemoryDiagnostics(Activity activity, Supplier<JSONObject> resources) {
        this(activity, resources, CinemaMemoryDiagnostics::sample);
    }
    CinemaMemoryDiagnostics(Activity activity, Supplier<JSONObject> resources, SampleReader reader) {
        this.activity = activity;
        this.resources = resources;
        this.reader = reader;
        try {
            record = new JSONObject(activity.getSharedPreferences("cinema_memory_v1", 0).getString("record", "{}"));
            if (record.optString("state").equals("RUNNING")) {
                record.put("state", "INTERRUPTED");
                save();
            }
        } catch (Exception ignored) { record = new JSONObject(); }
    }

    static JSONObject sample(Activity activity, JSONObject resources) throws Exception {
        Runtime runtime = Runtime.getRuntime();
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) throw new IllegalStateException("Memory service unavailable");
        ActivityManager.MemoryInfo system = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(system);
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        return new JSONObject().put("at", System.currentTimeMillis()).put("sdk", Build.VERSION.SDK_INT)
            .put("versionCode", activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode)
            .put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory()).put("javaMaxBytes", runtime.maxMemory())
            .put("memoryClassMiB", manager.getMemoryClass()).put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize())
            .put("pssKiB", memory.getTotalPss()).put("systemTotalBytes", system.totalMem)
            .put("systemAvailableBytes", system.availMem).put("lowMemory", system.lowMemory).put("resources", resources);
    }

    void show() {
        if (closed || dialog != null && dialog.isShowing()) return;
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(24, 16, 24, 16);
        box.setOrientation(LinearLayout.VERTICAL);
        text = new TextView(activity);
        text.setTextSize(20);
        text.setTextColor(0xffeeeeee);
        text.setFocusable(true);
        box.addView(text);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);
        dialog = new AlertDialog.Builder(activity).setTitle("Cinema TV · 記憶體檢查").setView(scroll)
            .setNegativeButton("關閉", null).setNeutralButton("匯出紀錄", null)
            .setPositiveButton(running ? "停止記錄" : "記錄 2 分鐘", null).create();
        dialog.setOnDismissListener(d -> { dialog = null; text = null; });
        dialog.show();
        dialog.getButton(-2).requestFocus();
        dialog.getButton(-1).setOnClickListener(v -> { if (running) finish("STOPPED"); else start(); render(); });
        dialog.getButton(-3).setOnClickListener(v -> export());
        render();
        if (!running) take(false);
    }

    boolean start() {
        if (closed || running || busy || exporting) return false;
        try {
            record = new JSONObject().put("schema", 2).put("state", "RUNNING")
                .put("startedAt", System.currentTimeMillis()).put("samples", new JSONArray())
                .put("validPssSamples", 0).put("failedReads", 0);
            running = true;
            deadline = SystemClock.elapsedRealtime() + WINDOW_MS;
            save();
            take(true);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private void take(boolean observation) {
        if (closed || busy) return;
        if (observation && (!running || SystemClock.elapsedRealtime() >= deadline
                || record.optJSONArray("samples").length() >= MAX_SAMPLES)) {
            finish("COMPLETED_WINDOW");
            return;
        }
        busy = true;
        render();
        JSONObject cache;
        try { cache = resources.get(); } catch (RuntimeException ignored) { cache = new JSONObject(); }
        final JSONObject cacheSnapshot = cache;
        worker.execute(() -> {
            JSONObject snapshot;
            try {
                snapshot = reader.read(activity, cacheSnapshot);
                if (snapshot == null) throw new IllegalStateException("No memory data");
                snapshot.put("readStatus", positive(snapshot, "pssKiB") ? "OK" : "PARTIAL");
            } catch (Exception ignored) {
                snapshot = new JSONObject();
                try { snapshot.put("at", System.currentTimeMillis()).put("readStatus", "FAILED").put("error", "MEMORY_READ_UNAVAILABLE"); }
                catch (Exception impossible) { /* JSONObject primitives remain safe. */ }
            }
            final JSONObject result = snapshot;
            main.post(() -> {
                busy = false;
                if (closed || observation && !running) return;
                if (observation && SystemClock.elapsedRealtime() > deadline) { finish("COMPLETED_WINDOW"); return; }
                try {
                    record.put("latest", result);
                    if (observation) {
                        record.getJSONArray("samples").put(result);
                        if (positive(result, "pssKiB")) {
                            record.put("validPssSamples", record.optInt("validPssSamples") + 1);
                            record.put("peakPssKiB", Math.max(record.optLong("peakPssKiB"), result.getLong("pssKiB")));
                        }
                        if (result.optString("readStatus").equals("FAILED")) record.put("failedReads", record.optInt("failedReads") + 1);
                    }
                    save();
                } catch (Exception ignored) { /* Keep any existing persisted record. */ }
                render();
                if (observation && running) main.postDelayed(tick, Math.min(INTERVAL_MS, Math.max(0, deadline - SystemClock.elapsedRealtime())));
            });
        });
    }

    private void finish(String state) {
        running = false;
        main.removeCallbacks(tick);
        try { record.put("state", state).put("finishedAt", System.currentTimeMillis()); save(); }
        catch (Exception ignored) { }
        render();
    }
    private void save() { activity.getSharedPreferences("cinema_memory_v1", 0).edit().putString("record", record.toString()).apply(); }
    private static boolean positive(JSONObject value, String key) { return value.opt(key) instanceof Number && value.optDouble(key) > 0; }
    private static String mib(JSONObject value, String key, float unit) {
        return positive(value, key) ? String.format(Locale.ROOT, "%.1f MiB", value.optDouble(key) / unit) : "未知";
    }
    static String description(JSONObject record) {
        JSONArray samples = record.optJSONArray("samples");
        String header = "Android API " + Build.VERSION.SDK_INT + " · 狀態：" + record.optString("state", "SNAPSHOT")
            + " · " + (samples == null ? 0 : samples.length()) + " 次抽樣\n";
        JSONObject latest = record.optJSONObject("latest");
        if (latest == null) return header + "尚未取得讀值。開啟此頁只讀取本 App 狀態；不會自動開始記錄。";
        boolean failed = latest.optString("readStatus").equals("FAILED") || latest.length() == 0;
        String current = failed ? "讀取失敗，本次數值未知。可重新開啟此頁讀取；既有有效紀錄保留。\n" : "";
        return header + current
            + "App PSS：" + mib(latest, "pssKiB", 1024f)
            + "\n抽樣最高 PSS：" + (positive(record, "peakPssKiB") ? mib(record, "peakPssKiB", 1024f) : "尚無有效樣本")
            + "\nJava 使用／上限：" + mib(latest, "javaUsedBytes", 1048576f) + " / " + mib(latest, "javaMaxBytes", 1048576f)
            + "\nApp memoryClass：" + (positive(latest, "memoryClassMiB") ? latest.optLong("memoryClassMiB") + " MiB" : "未知")
            + "\nNative heap：" + mib(latest, "nativeAllocatedBytes", 1048576f)
            + "\n系統可用／總量：" + mib(latest, "systemAvailableBytes", 1048576f) + " / " + mib(latest, "systemTotalBytes", 1048576f)
            + "\n\n每 5 秒抽樣，最多 24 次／120 秒，不重設期限。關閉視窗可繼續正常操作；App 結束即停止，不自動續跑。抽樣峰值不代表所有瞬間峰值。PSS、Java 與 Native 不可直接相加。"
            + "\n\n快取：" + (latest.optJSONObject("resources") == null ? "未知" : latest.optJSONObject("resources"));
    }
    private void render() {
        if (dialog != null) {
            dialog.getButton(-1).setText(running ? "停止記錄" : "記錄 2 分鐘");
            dialog.getButton(-1).setEnabled(running || !busy && !exporting);
            dialog.getButton(-3).setEnabled(Build.VERSION.SDK_INT >= 29 && !exporting);
        }
        if (text != null) text.setText(description(record));
    }
    private void export() {
        if (closed || exporting) return;
        exporting = true;
        render();
        final String json = record.toString();
        worker.execute(() -> {
            boolean ok = false;
            try {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                CinemaVerifiedExport.downloads(activity, "memory-report.json", "application/json", out -> {
                    out.write(bytes);
                    return new CinemaVerifiedExport.Proof(bytes.length, BootWallpaperBackup.hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                });
                ok = true;
            } catch (Exception ignored) { }
            boolean success = ok;
            main.post(() -> {
                exporting = false;
                if (!closed) {
                    render();
                    Toast.makeText(activity, success ? "已儲存：下載 / CinemaTV" : "紀錄儲存未完成", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    JSONObject record() { try { return new JSONObject(record.toString()); } catch (Exception ignored) { return new JSONObject(); } }
    @Override public void close() {
        if (closed) return;
        if (running) finish("INTERRUPTED");
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
        worker.shutdownNow();
    }
}
