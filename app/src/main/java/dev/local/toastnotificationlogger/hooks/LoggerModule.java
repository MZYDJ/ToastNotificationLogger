package dev.local.toastnotificationlogger.hooks;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102 entry point.
 *
 * Hook installation is intentionally deferred until the exact ColorOS method
 * bodies and lifecycle timing are validated. This class currently performs no
 * hooks and must not be presented as a working capture implementation.
 */
public final class LoggerModule extends XposedModule {
    public LoggerModule() {
        super();
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        log(android.util.Log.INFO, "ToastNotificationLogger", "Loaded in system_server; hooks are not enabled in this development build.");
    }
}
