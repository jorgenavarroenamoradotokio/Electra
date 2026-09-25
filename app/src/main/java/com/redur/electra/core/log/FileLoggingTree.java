package com.redur.electra.core.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Árbol de Timber que escribe en fichero:
 * - Escritura en un único hilo de fondo (orden garantizado, sin I/O en main thread).
 * - Rotación diaria y por tamaño.
 * - Purga por antigüedad y por tamaño total, al arrancar y en cada rotación.
 * Hereda de DebugTree para reutilizar la inferencia automática de tag
 * (en PRO requiere conservar nombres de clase, ver proguard-rules.pro).
 * Usa android.util.Log (no Timber) para sus propios errores: registrar vía Timber
 * desde un Tree plantado provocaría recursión infinita si falla la escritura.
 */
@SuppressLint("LogNotTimber")
public final class FileLoggingTree extends Timber.DebugTree {

    public static final String DEFAULT_PREFIX = "electra_";
    public static final long DEFAULT_MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int DEFAULT_RETENTION_DAYS = 7;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private static final String TAG = "FileLoggingTree";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final File dir;
    private final int minPriority;
    private final String filePrefix;
    private final long maxFileBytes;
    private final long retentionMs;
    private final long maxTotalBytes;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "file-logger");
        t.setDaemon(true);
        return t;
    });

    // Solo accedidos desde el hilo del executor
    private BufferedWriter writer;
    private File currentFile;
    private LocalDate currentDay;

    /** Constructor de producción. */
    public FileLoggingTree(@NonNull File dir, int minPriority) {
        this(dir, minPriority, DEFAULT_PREFIX, DEFAULT_MAX_FILE_BYTES, DEFAULT_RETENTION_DAYS,
                DEFAULT_MAX_TOTAL_BYTES, Clock.systemDefaultZone());
    }

    /** Constructor completo: permite a los tests fijar tamaños, retención y reloj. */
    @VisibleForTesting
    FileLoggingTree(@NonNull File dir, int minPriority, @NonNull String filePrefix,
                    long maxFileBytes, int retentionDays, long maxTotalBytes,
                    @NonNull Clock clock) {
        if (maxTotalBytes < maxFileBytes) {
            throw new IllegalArgumentException("maxTotalBytes debe ser >= maxFileBytes");
        }
        this.dir = dir;
        this.minPriority = minPriority;
        this.filePrefix = filePrefix;
        this.maxFileBytes = maxFileBytes;
        this.retentionMs = TimeUnit.DAYS.toMillis(retentionDays);
        this.maxTotalBytes = maxTotalBytes;
        this.clock = clock;
        executor.execute(this::purgeOldFiles);
    }

    /** Carpeta de logs recomendada: app-specific external (sin permisos, extraíble por adb). */
    @NonNull
    public static File resolveLogDir(@NonNull Context context) {
        File external = context.getExternalFilesDir("logs");
        return external != null ? external : new File(context.getFilesDir(), "logs");
    }

    @Override
    protected boolean isLoggable(@Nullable String tag, int priority) {
        return priority >= minPriority;
    }

    @Override
    protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
        if (executor.isShutdown()) {
            return;
        }

        final String line = TS.format(LocalDateTime.now(clock)) + ' '
                + priorityChar(priority) + '/' + (tag != null ? tag : "-")
                + " [" + Thread.currentThread().getName() + "]: " + message;
        try {
            executor.execute(() -> write(line));
        } catch (RejectedExecutionException ignored) {
            // shutdown() concurrente entre la comprobación y el execute: se descarta la línea
            // en lugar de propagar la excepción a quien llamó a Timber
        }
    }

    /** Espera a que se vacíe la cola de escritura. Crash handler y tests. */
    public void flushBlocking(long timeoutMs) {
        try {
            executor.submit(() -> { }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Timeout o interrupción: se aceptan posibles pérdidas de las últimas líneas
        }
    }

    /** Cierra el fichero y detiene el hilo. Tras llamarlo, los logs se descartan. */
    public void shutdown() {
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(this::closeQuietly);
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @NonNull
    public File getLogDir() {
        return dir;
    }

    private void write(String line) {
        try {
            ensureWriter();
            writer.write(line);
            writer.newLine();
            writer.flush(); // Garantiza persistencia aunque el proceso muera a continuación
        } catch (IOException e) {
            Log.e(TAG, "Error escribiendo log", e);
            closeQuietly();
        }
    }

    private void ensureWriter() throws IOException {
        LocalDate today = LocalDate.now(clock);
        if (writer != null && today.equals(currentDay) && currentFile.length() < maxFileBytes) {
            return;
        }
        closeQuietly();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("No se puede crear " + dir);
        }
        // Cada rotación (día o tamaño) es un buen momento para purgar: cubre procesos
        // que llevan días vivos sin reiniciarse.
        purgeOldFiles();
        currentDay = today;
        currentFile = nextFile(today);
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(currentFile, true), StandardCharsets.UTF_8));
    }

    private File nextFile(LocalDate day) {
        String prefix = filePrefix + DAY.format(day);
        int i = 0;
        File f;
        do {
            f = new File(dir, prefix + (i == 0 ? "" : "_" + i) + ".log");
            i++;
        } while (f.exists() && f.length() >= maxFileBytes);
        return f;
    }

    /**
     * Borra los ficheros más antiguos que la retención y, si aun así se supera
     * {@code maxTotalBytes}, los más antiguos hasta quedar por debajo.
     * Nunca se llama con el writer abierto, así que no borra el fichero en uso.
     */
    private void purgeOldFiles() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        long limit = clock.millis() - retentionMs;
        long totalBytes = 0;
        for (File f : files) {
            totalBytes += f.length();
        }
        for (File f : files) {
            boolean expired = f.lastModified() < limit;
            if (!expired && totalBytes <= maxTotalBytes) {
                break; // Ordenados del más antiguo al más reciente: el resto se conserva
            }
            long size = f.length();
            if (f.delete()) {
                totalBytes -= size;
            } else {
                Log.w(TAG, "No se pudo borrar " + f.getName());
            }
        }
    }

    private void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Nada que hacer
            }
            writer = null;
        }
    }

    private static char priorityChar(int priority) {
        return switch (priority) {
            case Log.VERBOSE -> 'V';
            case Log.DEBUG -> 'D';
            case Log.INFO -> 'I';
            case Log.WARN -> 'W';
            case Log.ERROR -> 'E';
            case Log.ASSERT -> 'A';
            default -> '?';
        };
    }
}
