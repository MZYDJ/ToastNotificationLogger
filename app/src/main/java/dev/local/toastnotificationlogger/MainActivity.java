package dev.local.toastnotificationlogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local event browser with package/type/time filters and CSV export. */
public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 4102;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final List<EventRecord> events = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();

    private EventDatabase database;
    private ArrayAdapter<String> adapter;
    private TextView status;
    private EditText packageFilter;
    private Spinner typeFilter;
    private Spinner timeFilter;
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
        loadEvents();
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
            mainHandler.post(() -> {
                if (destroyed) return;
                events.clear();
                events.addAll(loaded);
                rows.clear();
                SimpleDateFormat format = new SimpleDateFormat(
                        "MM-dd HH:mm:ss", Locale.getDefault());
                for (EventRecord event : loaded) {
                    String packageLabel = event.packageName == null
                            ? "(unknown package)" : event.packageName;
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
                + "\nApp: " + value(event.appName)
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
                    writer.write(csv(event.appName)); writer.write(',');
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
