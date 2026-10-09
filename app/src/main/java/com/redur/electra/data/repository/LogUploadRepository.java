package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.api.FileApiService;
import com.redur.electra.data.remote.dto.request.file.UploadFileDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.upload.ProgressRequestBody;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import javax.inject.Inject;

import okhttp3.MediaType;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Envía al backend un fichero de log con las credenciales de la sesión. Progreso y resultado
 * llegan en el hilo principal y no se entregan si la operación se cancela.
 */
public class LogUploadRepository extends BaseRepository {

    private static final MediaType LOG_MEDIA_TYPE = MediaType.get("text/plain; charset=utf-8");

    private final FileApiService api;
    private final UserSession session;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    @Inject
    public LogUploadRepository(FileApiService api, UserSession session,
                               @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this.api = api;
        this.session = session;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /**
     * Sube {@code logFile}. {@code onProgress} recibe el porcentaje (0-100) enviado; al llegar a 100
     * solo falta la confirmación del servidor.
     */
    @NonNull
    public Cancellable upload(@NonNull File logFile, @NonNull IntConsumer onProgress,
                              @NonNull ResultCallback<Boolean> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Envío del log sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }

        User user = session.getUser();
        String plzsId = user != null ? user.plazaId() : null;

        // Ajustamos el texto para enviar UK en vez de EN a la API
        String locale = backendLanguage();
        Timber.d("El idioma del usuario que esta usando: %s", locale);

        AtomicBoolean canceled = new AtomicBoolean();
        AtomicReference<Call<ApiResponseDTO<Boolean>>> pendingCall = new AtomicReference<>();
        ioExecutor.execute(() -> {
            byte[] content = readSnapshot(logFile);
            if (content == null) {
                deliver(canceled, () -> callback.onError(new AppError.Api(null, null)));
                return;
            }
            IntConsumer progressOnMain = percent -> deliver(canceled, () -> onProgress.accept(percent));
            // Construimos el DTO que vamos a enviar a la api
            UploadFileDTO request = new UploadFileDTO(logFile.getName(),
                    new ProgressRequestBody(content, LOG_MEDIA_TYPE, progressOnMain),
                    plzsId, credentials.username(), credentials.password(), locale);


            Timber.i("DTO request  %s", request.toString());
            Call<ApiResponseDTO<Boolean>> call = api.uploadLog(request.toParts());
            pendingCall.set(call);
            if (canceled.get()) {
                return;
            }
            enqueue(call, logFile.getName(), callback);
        });
        return () -> {
            canceled.set(true);
            Call<ApiResponseDTO<Boolean>> call = pendingCall.get();
            if (call != null) {
                call.cancel();
            }
        };
    }

    private void enqueue(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull String fileName, @NonNull ResultCallback<Boolean> callback) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<Boolean>> call,
                                   @NonNull Response<ApiResponseDTO<Boolean>> response) {
                if (call.isCanceled()) {
                    return;
                }
                // Extraemos los datos
                Boolean uploaded = extractData(response, callback);
                if (uploaded == null) {
                    return;
                }

                // Notificamos que no se ha podido enviar el log al servidor
                if (!uploaded) {
                    Timber.w("El backend no ha aceptado el log %s", fileName);
                    callback.onError(new AppError.Api(null, null));
                    return;
                }

                Timber.i("Log %s enviado al backend", fileName);
                callback.onSuccess(Boolean.TRUE);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(call, t));
            }
        });
    }

    /**
     * Copia en memoria del log (máx. {@code FileLoggingTree#DEFAULT_MAX_FILE_BYTES}). El fichero sigue
     * creciendo mientras se envía, incluso con las trazas de esta misma petición; subirlo en directo
     * haría que no coincidiera con la longitud declarada y OkHttp abortaría la petición.
     */
    @WorkerThread
    @Nullable
    private static byte[] readSnapshot(@NonNull File logFile) {
        try {
            return Files.readAllBytes(logFile.toPath());
        } catch (IOException e) {
            Timber.e(e, "No se ha podido leer el log %s", logFile.getName());
            return null;
        }
    }

    private void deliver(@NonNull AtomicBoolean canceled, @NonNull Runnable result) {
        mainExecutor.execute(() -> {
            if (!canceled.get()) {
                result.run();
            }
        });
    }
}
