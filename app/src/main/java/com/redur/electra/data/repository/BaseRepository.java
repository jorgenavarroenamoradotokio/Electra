package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;
import timber.log.Timber;

/**
 * Base de los repositorios que consumen el backend: interpreta el sobre {@link ApiResponseDTO}
 * y traduce los fallos a {@link AppError}.
 */
public abstract class BaseRepository {

    private static final String HTTP_ERROR_PREFIX = "HTTP_";
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String BACKEND_LANGUAGE_ENGLISH = "uk";

    @NonNull
    protected static String backendLanguage() {
        String language = Locale.getDefault().getLanguage();
        return LANGUAGE_ENGLISH.equals(language) ? BACKEND_LANGUAGE_ENGLISH : language;
    }

    /**
     * Valida la respuesta y devuelve su {@code data}. Si la respuesta no es válida notifica el
     * error a {@code callback} y devuelve {@code null}: en ese caso el llamador no debe volver a
     * invocar el callback.
     */
    @Nullable
    protected static <T> T extractData(@NonNull Response<ApiResponseDTO<T>> response,
                                       @NonNull ResultCallback<?> callback) {
        if (!response.isSuccessful()) {
            Timber.e("%s%s", HTTP_ERROR_PREFIX, response.code());
            callback.onError(new AppError.Api(HTTP_ERROR_PREFIX + response.code(), null));
            return null;
        }

        ApiResponseDTO<T> body = response.body();
        if (body == null) {
            Timber.e("%s%s", HTTP_ERROR_PREFIX, response.code());
            callback.onError(new AppError.Api(HTTP_ERROR_PREFIX + response.code(), null));
            return null;
        }

        ApiErrorDetailResponseDTO apiError = firstError(body.errorList());
        if (apiError != null) {
            Timber.e("%s%s", apiError.code(), apiError.description());
            callback.onError(new AppError.Api(apiError.code(), apiError.description()));
            return null;
        }

        if (body.data() == null) {
            Timber.e("%s", body.errorText());
            callback.onError(new AppError.Api(null, body.errorText()));
            return null;
        }
        return body.data();
    }

    @NonNull
    protected static AppError toAppError(@NonNull Throwable t) {
        // SocketTimeoutException hereda de InterruptedIOException
        if (t instanceof InterruptedIOException) {
            return new AppError.Network(NetworkType.TIMEOUT);
        }
        if (t instanceof IOException) {
            return new AppError.Network(NetworkType.NO_CONNECTION);
        }
        // Respuesta imposible de interpretar (p. ej. JSON inesperado): no es un fallo de red
        Timber.e(t, "Respuesta del backend no interpretable");
        return new AppError.Api(null, null);
    }

    @Nullable
    private static ApiErrorDetailResponseDTO firstError(@Nullable List<ApiErrorDetailResponseDTO> errors) {
        if (errors == null) {
            return null;
        }
        for (ApiErrorDetailResponseDTO error : errors) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }
}
