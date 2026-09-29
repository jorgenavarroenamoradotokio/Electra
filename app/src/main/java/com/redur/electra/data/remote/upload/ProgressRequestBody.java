package com.redur.electra.data.remote.upload;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.IntConsumer;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

/**
 * Cuerpo en memoria que informa del porcentaje (0-100) ya escrito en la conexión. Se escribe por
 * bloques: cada bloque sale hacia el socket antes de contar el siguiente, así que el porcentaje
 * sigue a la red y no a la copia en memoria. El listener se invoca en el hilo de OkHttp y solo
 * cuando el porcentaje cambia.
 */
public final class ProgressRequestBody extends RequestBody {

    private static final int CHUNK_BYTES = 8 * 1024;
    private static final int COMPLETE = 100;

    private final byte[] content;
    @Nullable
    private final MediaType contentType;
    private final IntConsumer onProgress;

    public ProgressRequestBody(@NonNull byte[] content, @Nullable MediaType contentType,
                               @NonNull IntConsumer onProgress) {
        this.content = Objects.requireNonNull(content, "content");
        this.contentType = contentType;
        this.onProgress = Objects.requireNonNull(onProgress, "onProgress");
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() {
        return content.length;
    }

    /** OkHttp puede volver a llamarlo si reintenta la conexión: el progreso arranca de nuevo. */
    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        if (content.length == 0) {
            onProgress.accept(COMPLETE);
            return;
        }
        int lastPercent = -1;
        for (int offset = 0; offset < content.length; offset += CHUNK_BYTES) {
            int count = Math.min(CHUNK_BYTES, content.length - offset);
            sink.write(content, offset, count);
            int percent = (int) ((offset + count) * (long) COMPLETE / content.length);
            if (percent != lastPercent) {
                lastPercent = percent;
                onProgress.accept(percent);
            }
        }
    }
}
