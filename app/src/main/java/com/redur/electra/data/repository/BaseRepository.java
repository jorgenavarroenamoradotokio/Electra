package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
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
    private static final Gson ERROR_BODY_GSON = new Gson();
    private static final char JSON_OBJECT_START = '{';

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
        String endpoint = describe(response.raw().request());
        if (!response.isSuccessful()) {
            callback.onError(toHttpError(response, endpoint));
            return null;
        }

        ApiResponseDTO<T> body = response.body();
        if (body == null) {
            Timber.e("[RED] %s%s sin cuerpo en %s", HTTP_ERROR_PREFIX, response.code(), endpoint);
            callback.onError(new AppError.Api(HTTP_ERROR_PREFIX + response.code(), null));
            return null;
        }

        ApiErrorDetailResponseDTO apiError = firstError(body.errorList());
        if (apiError != null) {
            Timber.e("[API] Error %s en %s: %s", apiError.code(), endpoint, apiError.description());
            callback.onError(new AppError.Api(apiError.code(), apiError.description()));
            return null;
        }

        if (body.data() == null) {
            Timber.e("[API] Respuesta sin datos en %s: %s", endpoint, body.errorText());
            callback.onError(new AppError.Api(null, body.errorText()));
            return null;
        }
        return body.data();
    }

    /**
     * Traduce el fallo de una llamada a {@link AppError} y lo registra con el endpoint afectado.
     * Solo debe llamarse si la llamada no se ha cancelado: una cancelación no es un error.
     */
    @NonNull
    protected static AppError toAppError(@NonNull Call<?> call, @NonNull Throwable t) {
        String endpoint = describe(call.request());
        // SocketTimeoutException hereda de InterruptedIOException
        if (t instanceof InterruptedIOException) {
            // Sin traza completa: en un fallo de red basta con el tipo y el mensaje
            Timber.w("[RED] Timeout en %s: %s", endpoint, t.toString());
            return new AppError.Network(NetworkType.TIMEOUT);
        }
        if (t instanceof IOException) {
            Timber.w("[RED] Sin conexión con el backend en %s: %s", endpoint, t.toString());
            return new AppError.Network(NetworkType.NO_CONNECTION);
        }
        // Respuesta imposible de interpretar (p. ej. JSON inesperado): no es un fallo de red
        Timber.e(t, "[API] Respuesta del backend no interpretable en %s", endpoint);
        return new AppError.Api(null, null);
    }

    /**
     * Un HTTP no 2xx puede traer igualmente el sobre {@link ApiResponseDTO} (p. ej. un 403 por
     * credenciales incorrectas): si incluye un error de negocio se prioriza su código; si no, el
     * error se identifica por el código HTTP.
     */
    @NonNull
    private static AppError toHttpError(@NonNull Response<?> response, @NonNull String endpoint) {
        ApiErrorDetailResponseDTO apiError = firstError(parseErrorBody(response.errorBody(), endpoint));
        if (apiError != null && apiError.code() != null) {
            Timber.e("[API] %s%s con error %s en %s", HTTP_ERROR_PREFIX, response.code(),
                    apiError.code(), endpoint);
            return new AppError.Api(apiError.code(), apiError.description());
        }
        Timber.e("[RED] %s%s en %s", HTTP_ERROR_PREFIX, response.code(), endpoint);
        return new AppError.Api(HTTP_ERROR_PREFIX + response.code(), null);
    }

    /** Lista de errores del cuerpo de error, o {@code null} si no hay cuerpo o no es el sobre esperado. */
    @Nullable
    private static List<ApiErrorDetailResponseDTO> parseErrorBody(@Nullable ResponseBody errorBody,
                                                                  @NonNull String endpoint) {
        if (errorBody == null) {
            return null;
        }
        // Retrofit ya ha volcado el cuerpo de error a memoria: leerlo no bloquea con red
        try (errorBody) {
            ApiResponseDTO<?> envelope = ERROR_BODY_GSON.fromJson(errorBody.string(), ApiResponseDTO.class);
            if (envelope == null) {
                return null;
            }
            if (firstError(envelope.errorList()) != null) {
                return envelope.errorList();
            }
            return nestedErrors(envelope.errorText());
        } catch (IOException | JsonParseException e) {
            Timber.w("[API] Cuerpo de error no interpretable en %s: %s", endpoint, e.toString());
            return null;
        }
    }

    /**
     * ElectraWS reenvía el error del servicio interno como texto dentro de {@code errorText}
     * (p. ej. {@code "Error en ElectraWS: 403 FORBIDDEN - {\"errorList\":[...]}"}) y deja
     * {@code errorList} vacío: se extrae ese sobre anidado.
     */
    @Nullable
    private static List<ApiErrorDetailResponseDTO> nestedErrors(@Nullable String errorText) {
        int jsonStart = errorText != null ? errorText.indexOf(JSON_OBJECT_START) : -1;
        if (jsonStart < 0) {
            return null;
        }
        ApiResponseDTO<?> nested = ERROR_BODY_GSON.fromJson(errorText.substring(jsonStart), ApiResponseDTO.class);
        return nested != null ? nested.errorList() : null;
    }

    /** Método y ruta, sin host ni query: identifica la operación sin volcar parámetros. */
    @NonNull
    private static String describe(@Nullable Request request) {
        if (request == null) {
            return "endpoint desconocido";
        }
        return request.method() + " " + request.url().encodedPath();
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
