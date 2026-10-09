package com.redur.electra.core.log;

import android.content.Context;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.BuildConfig;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

/**
 * Configura Timber según el buildType (flags LOG_* de BuildConfig).
 * Las llamadas de log en el resto de la app usan Timber directamente.
 */
@Singleton
public final class LoggingInitializer {

    private static final long CRASH_FLUSH_TIMEOUT_MS = 2000;

    private static final String LOGCAT_TAG_PREFIX = "Electra.";
    private final Context context;
    @Nullable
    private FileLoggingTree fileTree;
    private boolean initialized;

    @Inject
    LoggingInitializer(@ApplicationContext Context context) {
        this.context = context;
    }

    public synchronized void init() {
        // Evita plantar árboles duplicados
        if (initialized) {
            return;
        }
        initialized = true;

        if (BuildConfig.LOG_TO_LOGCAT) {
            Timber.plant(new Timber.DebugTree() {
                @Override
                protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
                    super.log(priority, LOGCAT_TAG_PREFIX + tag, message, t);
                }
            });
        }

        if (BuildConfig.LOG_TO_FILE) {
            fileTree = new FileLoggingTree(FileLoggingTree.resolveLogDir(context), BuildConfig.LOG_MIN_PRIORITY);
            Timber.plant(fileTree);
            installCrashHandler(fileTree);
        }

        // Primera línea de cada arranque: identifica versión y terminal al leer un log recibido
        Timber.i("Arranque %s %s (%d) [%s] en %s %s, Android %s (API %d)",
                BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                BuildConfig.ENVIRONMENT, Build.MANUFACTURER, Build.MODEL,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
    }

    /** Carpeta de logs para una futura opción de "Exportar logs". Null si no se escribe a fichero. */
    @Nullable
    public File getLogDir() {
        return fileTree != null ? fileTree.getLogDir() : null;
    }

    private static void installCrashHandler(FileLoggingTree tree) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Timber.e(ex, "Crash no capturado en hilo %s", thread.getName());
            tree.flushBlocking(CRASH_FLUSH_TIMEOUT_MS);
            if (previous != null) {
                previous.uncaughtException(thread, ex);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }
}