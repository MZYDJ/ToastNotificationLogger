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
        heading.setText("Toast & Notification History");
        heading.setTextSize(20);
        root.addView(heading);

        status = new TextView(this);
        status.setText("Loading local history…");
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        packageFilter = new EditText(this);
        packageFilter.setSingleLine(true);
        packageFilter.setHint("Package name (exact match)");
        root.addView(packageFilter);

        typeFilter = new Spinner(this);
        List<String> types = new ArrayList<>();
        types.add("All event types");
        for (EventType type : EventType.values()) types.add(type.name());
        typeFilter.setAdapter(spinnerAdapter(types));
        root.addView(typeFilter);

        timeFilter = new Spinner(this);
        List<String> ranges = new ArrayList<>();
        ranges.add("All time");
        ranges.add("Last 24 hours");
        ranges.add("Last 7 days");
        ranges.add("Last 30 days");
        ranges.add("Last 90 days");
        timeFilter.setAdapter(spinnerAdapter(ranges));
        root.addView(timeFilter);

        LinearLayout retentionRow = new LinearLayout(this);
        retentionRow.setOrientation(LinearLayout.HORIZONTAL);
        retentionFilter = new Spinner(this);
        List<String> retentionOptions = new ArrayList<>();
        retentionOptions.add("Keep forever");
        retentionOptions.add("Keep 7 days");
        retentionOptions.add("Keep 30 days");
        retentionOptions.add("Keep 90 days");
        retentionOptions.add("Keep 365 days");
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
        applyRetention.setText("Apply retention");
        applyRetention.setOnClickListener(v -> cleanupRetention(true));
        retentionRow.addView(applyRetention);
        root.addView(retentionRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button apply = new Button(this);
        apply.setText("Filter");
        apply.setOnClickListener(v -> loadEvents());
        actions.addView(apply);

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> loadEvents());
        actions.addView(refresh);

        Button export = new Button(this);
        export.setText("Export CSV");
        export.setOnClickListener(v -> startCsvExport());
        actions.addView(export);

        Button excluded = new Button(this);
        excluded.setText("Excluded apps");
        excluded.setOnClickListener(v -> manageExcludedApps());
        actions.addView(excluded);

        Button clear = new Button(this);
        clear.setText("Clear all");
        clear.setOnClickListener(v -> confirmClear());
        actions.addView(clear);
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

    private ArrayAdapter<String> spinnerAdapter(List<String> values) {
        ArrayAdapter<String> result = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        result.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return result;
    }

    private void loadEvents() {
        status.setText("Loading…");
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
                            ? "(unknown package)"
                            : appLabels.getOrDefault(event.packageName, event.packageName);
                    String summary = firstNonEmpty(event.title, event.text);
                    String row = format.format(new Date(event.timestampMillis))
                            + "  [" + event.type.name() + "]\n"
                            + packageLabel
                            + (summary.isEmpty() ? "" : "\n" + summary);
                    rows.add(row);
                }
                adapter.notifyDataSetChanged();
                status.setText(loaded.size() + " event(s) in this view");
            });
        });
    }

    private void showDetails(int position) {
        if (position < 0 || position >= events.size()) return;
        EventRecord event = events.get(position);
        String details = "Time: " + new Date(event.timestampMillis)
                + "\nType: " + event.type
                + "\nPackage: " + value(event.packageName)
                + "\nApp: " + value(appLabels.get(event.packageName))
                + "\nTitle: " + value(event.title)
                + "\nText: " + value(event.text)
                + "\nNotification key: " + value(event.notificationKey)
                + "\nNotification ID: " + value(event.notificationId)
                + "\nUser ID: " + value(event.userId)
                + "\nRemoval reason: " + value(event.removalReason);
        new AlertDialog.Builder(this)
                .setTitle("Event details")
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
        packageName.setHint("Example: com.example.app");
        String current = excluded.isEmpty()
                ? "No excluded packages"
                : "Currently excluded:\n" + android.text.TextUtils.join("\\n", excluded);
        new AlertDialog.Builder(this)
                .setTitle("Excluded applications")
                .setMessage(current)
                .setView(packageName)
                .setPositiveButton("Exclude", (dialog, which) -> {
                    String value = packageName.getText().toString().trim();
                    if (value.isEmpty()) return;
                    excluded.add(value);
                    preferences.edit().putStringSet(
                            "excluded_packages", new HashSet<>(excluded)).apply();
                    status.setText("Excluded " + value);
                })
                .setNeutralButton("Remove", (dialog, which) -> {
                    String value = packageName.getText().toString().trim();
                    if (value.isEmpty()) return;
                    excluded.remove(value);
                    preferences.edit().putStringSet(
                            "excluded_packages", new HashSet<>(excluded)).apply();
                    status.setText("Removed exclusion for " + value);
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
            if (showStatus) status.setText("Retention disabled; history is kept indefinitely");
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        databaseExecutor.execute(() -> {
            int deleted = database.deleteOlderThan(cutoff);
            mainHandler.post(() -> {
                if (destroyed) return;
                if (showStatus) status.setText("Retention cleanup removed " + deleted + " event(s)");
                loadEvents();
            });
        });
    }

    private void startCsvExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "toast-notification-history.csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        status.setText("Exporting CSV…");
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
                for (EventRecord event : all) {
                    writer.write(csv(Long.toString(event.timestampMillis))); writer.write(',');
                    writer.write(csv(event.type.name())); writer.write(',');
                    writer.write(csv(event.packageName)); writer.write(',');
                    writer.write(csv(resolveAppLabel(event.packageName))); writer.write(',');
                    writer.write(csv(event.title)); writer.write(',');
                    writer.write(csv(event.text)); writer.write(',');
                    writer.write(csv(event.notificationKey)); writer.write(',');
                    writer.write(csv(value(event.notificationId))); writer.write(',');
                    writer.write(csv(value(event.userId))); writer.write(',');
                    writer.write(csv(value(event.removalReason))); writer.write('\n');
                }
            }
            mainHandler.post(() -> {
                if (!destroyed) status.setText("Exported " + all.size() + " event(s)");
            });
        } catch (Exception failure) {
            mainHandler.post(() -> {
                if (!destroyed) status.setText("CSV export failed: " + failure.getMessage());
            });
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear all history?")
                .setMessage("This permanently deletes all locally stored events.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clear", (dialog, which) ->
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
