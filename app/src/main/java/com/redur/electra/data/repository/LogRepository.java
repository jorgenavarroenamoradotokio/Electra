package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.log.LoggingInitializer;

import java.io.File;
import java.util.function.Supplier;

import javax.inject.Inject;

/** Acceso a los ficheros de log que escribe la app (ver {@code FileLoggingTree}). */
public class LogRepository {

    private static final String LOG_EXTENSION = ".log";

    private final Supplier<File> logDir;

    @Inject
    public LogRepository(LoggingInitializer logging) {
        this(logging::getLogDir);
    }

    @VisibleForTesting
    public LogRepository(@NonNull Supplier<File> logDir) {
        this.logDir = logDir;
    }

    /**
     * Fichero de log en uso: el modificado más recientemente, ya que se escribe línea a línea.
     * Null si la app no registra en fichero en este entorno o aún no se ha escrito nada.
     * Lee el disco: no debe llamarse desde el hilo principal.
     */
    @WorkerThread
    @Nullable
    public File findCurrentLogFile() {
        File dir = logDir.get();
        File[] files = dir != null ? dir.listFiles((d, name) -> name.endsWith(LOG_EXTENSION)) : null;
        if (files == null) {
            return null;
        }
        File current = null;
        for (File file : files) {
            if (file.length() > 0 && (current == null || file.lastModified() > current.lastModified())) {
                current = file;
            }
        }
        return current;
    }
}
