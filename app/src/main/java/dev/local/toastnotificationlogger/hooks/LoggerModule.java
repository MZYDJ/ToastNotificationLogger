package dev.local.toastnotificationlogger.hooks;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** libxposed API 102 entry point for system_server capture. */
public final class LoggerModule extends XposedModule {
    public LoggerModule() {
        super();
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        log(android.util.Log.INFO, "ToastNotificationLogger",
                "Starting ColorOS 16 NotificationManagerService hook installation");
        new SystemEventHooks(this).install(param.getClassLoader());
    }
}
