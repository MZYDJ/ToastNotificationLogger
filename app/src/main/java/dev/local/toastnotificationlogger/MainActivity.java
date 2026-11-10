package dev.local.toastnotificationlogger;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Minimal status screen. Event browsing and configuration will be added after
 * the capture pipeline and on-device storage contract are implemented.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setText(
                "ToastNotificationLogger\n\n"
                + "Development build — capture pipeline is not enabled yet.\n"
                + "Do not treat this build as a functional logger."
        );
        status.setTextSize(16);
        status.setPadding(32, 32, 32, 32);
        setContentView(status);
    }
}
