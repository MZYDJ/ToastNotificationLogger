package dev.local.toastnotificationlogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import dev.local.toastnotificationlogger.model.EventRecord;
import dev.local.toastnotificationlogger.storage.EventDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal local event browser. Database access is kept off the UI thread. */
public final class MainActivity extends Activity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final List<EventRecord> events = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();

    private EventDatabase database;
    private ArrayAdapter<String> adapter;
    private TextView status;
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

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> loadEvents());
        actions.addView(refresh);

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

    private void loadEvents() {
        status.setText("Loading…");
        databaseExecutor.execute(() -> {
            List<EventRecord> loaded = database.queryRecent(
                    1000, null, null, 0L, System.currentTimeMillis());
            mainHandler.post(() -> {
                if (destroyed) return;
                events.clear();
                events.addAll(loaded);
                rows.clear();
                SimpleDateFormat format = new SimpleDateFormat(
                        "MM-dd HH:mm:ss", Locale.getDefault());
                for (EventRecord event : loaded) {
                    String packageName = event.packageName == null
                            ? "(unknown package)" : event.packageName;
                    String summary = firstNonEmpty(event.title, event.text);
                    String row = format.format(new Date(event.timestampMillis))
                            + "  [" + event.type.name() + "]\n"
                            + packageName
                            + (summary.isEmpty() ? "" : "\n" + summary);
                    rows.add(row);
                }
                adapter.notifyDataSetChanged();
                status.setText(loaded.size() + " event(s) stored locally");
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

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear all history?")
                .setMessage("This permanently deletes all locally stored events.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clear", (dialog, which) ->
                        databaseExecutor.execute(() -> {
                            database.deleteAll();
                            loadEvents();
                        }))
                .show();
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) return first;
        return second == null ? "" : second;
    }

    private static String value(Object value) {
        return value == null ? "—" : value.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        databaseExecutor.shutdownNow();
        if (database != null) database.close();
        super.onDestroy();
    }
}
