package com.redur.electra.fake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Call de Retrofit controlado por el test. Por defecto entrega el resultado de forma síncrona al
 * encolarse; con {@link #deferred()} lo retiene hasta {@link #complete()}, para simular una
 * petición en curso.
 */
public final class FakeCall<T> implements Call<T> {

    @Nullable
    private final Response<T> response;
    @Nullable
    private final Throwable failure;
    private boolean deferred;
    private boolean executed;
    private boolean canceled;
    @Nullable
    private Callback<T> callback;

    private FakeCall(@Nullable Response<T> response, @Nullable Throwable failure) {
        this.response = response;
        this.failure = failure;
    }

    public static <T> FakeCall<T> success(Response<T> response) {
        return new FakeCall<>(response, null);
    }

    public static <T> FakeCall<T> failure(Throwable failure) {
        return new FakeCall<>(null, failure);
    }

    public FakeCall<T> deferred() {
        deferred = true;
        return this;
    }

    /** Entrega el resultado retenido, como haría Retrofit aunque la llamada se haya cancelado. */
    public void complete() {
        if (callback == null) {
            throw new IllegalStateException("La llamada no se ha encolado");
        }
        deliver(callback);
    }

    @Override
    public void enqueue(@NonNull Callback<T> callback) {
        executed = true;
        this.callback = callback;
        if (!deferred) {
            deliver(callback);
        }
    }

    private void deliver(Callback<T> callback) {
        if (failure != null) {
            callback.onFailure(this, failure);
        } else {
            callback.onResponse(this, response);
        }
    }

    @Override
    public Response<T> execute() throws IOException {
        throw new UnsupportedOperationException("Los repositorios usan enqueue");
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public void cancel() {
        canceled = true;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public Call<T> clone() {
        return new FakeCall<>(response, failure);
    }

    @Override
    public Request request() {
        return new Request.Builder().url("http://localhost/").build();
    }

    @Override
    public Timeout timeout() {
        return Timeout.NONE;
    }
}
