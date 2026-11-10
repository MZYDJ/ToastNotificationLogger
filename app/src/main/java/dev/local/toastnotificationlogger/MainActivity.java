package dev.local.toastnotificationlogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.AdapterView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.model.EventType;
import dev.local.toastnotificationlogger.storage.EventDatabase;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local event browser with package/type/time filters and CSV export. */
public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 4102;
    private static final int[] RETENTION_DAYS = {0, 7, 30, 90, 365};
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final List<EventRecord> events = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private final Map<String, String> appLabels = new HashMap<>();

    private EventDatabase database;
    private ArrayAdapter<String> adapter;
    private TextView status;
    private EditText packageFilter;
    private Spinner typeFilter;
    private Spinner timeFilter;
    private Spinner retentionFilter;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new EventDatabase(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);

        TextView heading = new TextView(this);
        heading.setText("Toast 与通知历史记录");
        heading.setTextSize(20);
        root.addView(heading);

        status = new TextView(this);
        status.setText("正在加载本地记录…");
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        packageFilter = new EditText(this);
        packageFilter.setSingleLine(true);
        packageFilter.setHint("按应用包名筛选（精确匹配）");
        root.addView(packageFilter);

        typeFilter = new Spinner(this);
        List<String> types = new ArrayList<>();
        types.add("全部事件类型");
        for (EventType type : EventType.values()) types.add(eventTypeLabel(type));
        typeFilter.setAdapter(spinnerAdapter(types));
        root.addView(typeFilter);

        timeFilter = new Spinner(this);
        List<String> ranges = new ArrayList<>();
        ranges.add("全部时间");
        ranges.add("最近 24 小时");
        ranges.add("最近 7 天");
        ranges.add("最近 30 天");
        ranges.add("最近 90 天");
        timeFilter.setAdapter(spinnerAdapter(ranges));
        root.addView(timeFilter);

        LinearLayout retentionRow = new LinearLayout(this);
        retentionRow.setOrientation(LinearLayout.HORIZONTAL);
        retentionFilter = new Spinner(this);
        List<String> retentionOptions = new ArrayList<>();
        retentionOptions.add("永久保留");
        retentionOptions.add("保留 7 天");
        retentionOptions.add("保留 30 天");
        retentionOptions.add("保留 90 天");
        retentionOptions.add("保留 365 天");
        retentionFilter.setAdapter(spinnerAdapter(retentionOptions));
        SharedPreferences preferences = getSharedPreferences("logger_config", MODE_PRIVATE);
        int savedDays = preferences.getInt("retention_days", 0);
        retentionFilter.setSelection(retentionIndex(savedDays));
        retentionFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                 int position, long id) {
                preferences.edit().putInt("retention_days", RETENTION_DAYS[position]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        retentionRow.addView(retentionFilter, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button applyRetention = new Button(this);
        applyRetention.setText("应用期限");
        applyRetention.setOnClickListener(v -> cleanupRetention(true));
        retentionRow.addView(applyRetention);
        root.addView(retentionRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actionRowOne = new LinearLayout(this);
        actionRowOne.setOrientation(LinearLayout.HORIZONTAL);
        Button apply = new Button(this);
        apply.setText("筛选");
        apply.setOnClickListener(v -> loadEvents());
        addWeightedButton(actionRowOne, apply);
        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> loadEvents());
        addWeightedButton(actionRowOne, refresh);
        Button export = new Button(this);
        export.setText("导出 CSV");
        export.setOnClickListener(v -> startCsvExport());
        addWeightedButton(actionRowOne, export);
        actions.addView(actionRowOne);

        LinearLayout actionRowTwo = new LinearLayout(this);
        actionRowTwo.setOrientation(LinearLayout.HORIZONTAL);
        Button excluded = new Button(this);
        excluded.setText("排除应用");
        excluded.setOnClickListener(v -> manageExcludedApps());
        addWeightedButton(actionRowTwo, excluded);
        Button clear = new Button(this);
        clear.setText("清空记录");
        clear.setOnClickListener(v -> confirmClear());
        addWeightedButton(actionRowTwo, clear);
        actions.addView(actionRowTwo);
        root.addView(actions);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetails(position));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        if (savedDays > 0) cleanupRetention(false); else loadEvents();
    }

    private void addWeightedButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setMinWidth(0);
        row.addView(button, params);
    }

    private static String eventTypeLabel(EventType type) {
        switch (type) {
            case TOAST_TEXT_REQUESTED: return "文本 Toast";
            case TOAST_CUSTOM_REQUESTED: return "自定义 Toast";
            case NOTIFICATION_POSTED: return "通知发布";
            case NOTIFICATION_UPDATED: return "通知更新";
            case NOTIFICATION_REMOVED: return "通知撤销";
            default: return type.name();
        }
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> values) {
        ArrayAdapter<String> result = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return result;
    }

    private void loadEvents() {
        status.setText("正在加载…");
        String packageName = packageFilter.getText().toString().trim();
        if (packageName.isEmpty()) packageName = null;
        final String selectedPackage = packageName;
        int selectedType = typeFilter.getSelectedItemPosition();
        final EventType eventType = selectedType == 0
                ? null : EventType.values()[selectedType - 1];
        int selectedRange = timeFilter.getSelectedItemPosition();
        long now = System.currentTimeMillis();
        long duration;
        switch (selectedRange) {
            case 1: duration = 24L * 60 * 60 * 1000; break;
            case 2: duration = 7L * 24 * 60 * 60 * 1000; break;
            case 3: duration = 30L * 24 * 60 * 60 * 1000; break;
            case 4: duration = 90L * 24 * 60 * 60 * 1000; break;
            default: duration = Long.MAX_VALUE; break;
        }
        final long from = duration == Long.MAX_VALUE ? 0L : Math.max(0L, now - duration);
        databaseExecutor.execute(() -> {
            List<EventRecord> loaded = database.queryRecent(
                    5000, selectedPackage, eventType, from, now);
            Map<String, String> labels = new HashMap<>();
            for (EventRecord event : loaded) {
                if (event.packageName != null && !labels.containsKey(event.packageName)) {
                    labels.put(event.packageName, resolveAppLabel(event.packageName));
                }
            }
            mainHandler.post(() -> {
                if (destroyed) return;
                events.clear();
                events.addAll(loaded);
                appLabels.clear();
                appLabels.putAll(labels);
                rows.clear();
                SimpleDateFormat format = new SimpleDateFormat(
                        "MM-dd HH:mm:ss", Locale.getDefault());
                for (EventRecord event : loaded) {
                    String packageLabel = event.packageName == null
                            ? "未知应用"
                            : appLabels.getOrDefault(event.packageName, event.packageName);
                    String summary = firstNonEmpty(event.title, event.text);
                    String row = format.format(new Date(event.timestampMillis))
                            + "  [" + eventTypeLabel(event.type) + "]\n"
                            + packageLabel
                            + (summary.isEmpty() ? "" : "\n" + summary);
                    rows.add(row);
                }
                adapter.notifyDataSetChanged();
                status.setText("当前显示 " + loaded.size() + " 条记录");
            });
        });
    }

    private void showDetails(int position) {
        if (position < 0 || position >= events.size()) return;
        EventRecord event = events.get(position);
        String details = "时间：" + new Date(event.timestampMillis)
                + "\n事件类型：" + eventTypeLabel(event.type)
                + "\n包名：" + value(event.packageName)
                + "\n应用：" + value(appLabels.get(event.packageName))
                + "\n标题：" + value(event.title)
                + "\n内容：" + value(event.text)
                + "\n通知键：" + value(event.notificationKey)
                + "\n通知 ID：" + value(event.notificationId)
                + "\n用户 ID：" + value(event.userId)
                + "\n撤销原因：" + value(event.removalReason);
        new AlertDialog.Builder(this)
                .setTitle("事件详情")
                .setMessage(details)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void manageExcludedApps() {
        SharedPreferences preferences = getSharedPreferences("logger_config", MODE_PRIVATE);
        Set<String> excluded = new HashSet<>(preferences.getStringSet(
                "excluded_packages", Collections.emptySet()));
        EditText packageName = new EditText(this);
        packageName.setSingleLine(true);
        packageName.setHint("输入包名，例如 com.example.app");
        String current = excluded.isEmpty()
                ? "当前没有排除任何应用"
                : "当前已排除的应用：\n" + android.text.TextUtils.join("\n", excluded);
        new AlertDialog.Builder(this)
                .setTitle("管理排除应用")
                .setMessage(current)
                .setView(packageName)
                .setPositiveButton("添加排除", (dialog, which) -> {
                    String value = packageName.getText().toString().trim();
                    if (value.isEmpty()) return;
                    excluded.add(value);
                    preferences.edit().putStringSet(
                            "excluded_packages", new HashSet<>(excluded)).apply();
                    status.setText("已排除应用：" + value);
                })
                .setNeutralButton("移除排除", (dialog, which) -> {
                    String value = packageName.getText().toString().trim();
                    if (value.isEmpty()) return;
                    excluded.remove(value);
                    preferences.edit().putStringSet(
                            "excluded_packages", new HashSet<>(excluded)).apply();
                    status.setText("已取消排除：" + value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int retentionIndex(int days) {
        for (int i = 0; i < RETENTION_DAYS.length; i++) {
            if (RETENTION_DAYS[i] == days) return i;
        }
        return 0;
    }

    private void cleanupRetention(boolean showStatus) {
        int days = RETENTION_DAYS[retentionFilter.getSelectedItemPosition()];
        if (days == 0) {
            if (showStatus) status.setText("已设置为永久保留记录");
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        databaseExecutor.execute(() -> {
            int deleted = database.deleteOlderThan(cutoff);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (showStatus) status.setText("清理完成，已删除 " + deleted + " 条过期记录");
                loadEvents();
            });
        });
    }

    private void startCsvExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Toast与通知历史记录.csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        status.setText("正在导出 CSV…");
        databaseExecutor.execute(() -> exportCsv(destination));
    }

    private void exportCsv(Uri destination) {
        try {
            List<EventRecord> all = database.queryRecent(
                    100000, null, null, 0L, Long.MAX_VALUE);
            OutputStream stream = getContentResolver().openOutputStream(destination);
            if (stream == null) throw new IllegalStateException("Unable to open export destination");
            try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                writer.write("timestamp_ms,event_type,package_name,app_name,title,text,"
                        + "notification_key,notification_id,user_id,removal_reason\n");
                Map<String, String> exportLabels = new HashMap<>();
                for (EventRecord event : all) {
                    String packageName = event.packageName;
                    String appLabel = "";
                    if (packageName != null) {
                        if (!exportLabels.containsKey(packageName)) {
                            exportLabels.put(packageName, resolveAppLabel(packageName));
                        }
                        appLabel = exportLabels.get(packageName);
                    }
                    writer.write(csv(Long.toString(event.timestampMillis))); writer.write(',');
                    writer.write(csv(event.type.name())); writer.write(',');
                    writer.write(csv(event.packageName)); writer.write(',');
                    writer.write(csv(appLabel)); writer.write(',');
                    writer.write(csv(event.title)); writer.write(',');
                    writer.write(csv(event.text)); writer.write(',');
                    writer.write(csv(event.notificationKey)); writer.write(',');
                    writer.write(csv(value(event.notificationId))); writer.write(',');
                    writer.write(csv(value(event.userId))); writer.write(',');
                    writer.write(csv(value(event.removalReason))); writer.write('\n');
                }
            }
            mainHandler.post(() -> {
                if (!destroyed) status.setText("已导出 " + all.size() + " 条记录");
            });
        } catch (Exception failure) {
            mainHandler.post(() -> {
                if (!destroyed) status.setText("CSV 导出失败：" + failure.getMessage());
            });
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("确定清空全部记录？")
                .setMessage("此操作将永久删除本机保存的全部事件记录，且无法恢复。")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("确认清空", (dialog, which) ->
                        databaseExecutor.execute(() -> {
                            database.deleteAll();
                            mainHandler.post(this::loadEvents);
                        }))
                .show();
    }

    private String resolveAppLabel(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return packageName;
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty()) {
            char first = safe.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                safe = "'" + safe;
            }
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) return first;
        return second == null ? "" : second;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (database != null) {
            EventDatabase closingDatabase = database;
            databaseExecutor.execute(closingDatabase::close);
        }
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
