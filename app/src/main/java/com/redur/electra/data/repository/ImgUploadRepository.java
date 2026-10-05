package com.redur.electra.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.api.FileApiService;
import com.redur.electra.data.remote.dto.request.file.UploadFileDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.upload.ProgressRequestBody;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.MediaType;
import okio.Okio;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Envía al backend una imagen hecha con la cámara o elegida en la galería, con las credenciales de
 * la sesión. La imagen llega como content:// y se lee entera antes de enviarla. Progreso y resultado
 * llegan en el hilo principal y no se entregan si la operación se cancela.
 */
public class ImgUploadRepository extends BaseRepository {

    private static final String DEFAULT_MIME_TYPE = "image/jpeg";
    private static final String DEFAULT_FILE_PREFIX = "IMG_";
    private static final String DEFAULT_FILE_EXTENSION = ".jpg";

    // El backend identifica el inglés como "uk" (igual que en el login)
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String BACKEND_LANGUAGE_ENGLISH = "uk";

    /** Lee la imagen apuntada por un content://. */
    @VisibleForTesting
    public interface ImageReader {
        @WorkerThread
        @NonNull
        ImageContent read(@NonNull String imageUri) throws IOException;
    }

    /** Imagen leída; {@code mimeType} es null si el proveedor no lo conoce. */
    @VisibleForTesting
    public record ImageContent(@NonNull String fileName, @Nullable String mimeType, @NonNull byte[] bytes) {
        public ImageContent {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(bytes, "bytes");
        }
    }

    private final ImageReader reader;
    private final FileApiService api;
    private final UserSession session;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    @Inject
    public ImgUploadRepository(@ApplicationContext Context context, FileApiService api, UserSession session,
                               @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this(new ContentImageReader(context.getContentResolver()), api, session, ioExecutor, mainExecutor);
    }

    @VisibleForTesting
    public ImgUploadRepository(@NonNull ImageReader reader, FileApiService api, UserSession session,
                               Executor ioExecutor, Executor mainExecutor) {
        this.reader = reader;
        this.api = api;
        this.session = session;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /**
     * Sube la imagen de {@code imageUri}. {@code onProgress} recibe el porcentaje (0-100) enviado; al
     * llegar a 100 solo falta la confirmación del servidor.
     */
    @NonNull
    public Cancellable upload(@NonNull String imageUri, @NonNull IntConsumer onProgress,
                              @NonNull ResultCallback<Boolean> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Envío de imagen sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }
        User user = session.getUser();
        String plzsId = user != null ? user.plazaId() : null;

        AtomicBoolean canceled = new AtomicBoolean();
        AtomicReference<Call<ApiResponseDTO<Boolean>>> pendingCall = new AtomicReference<>();
        ioExecutor.execute(() -> {
            ImageContent image = readImage(imageUri);
            if (image == null) {
                deliver(canceled, () -> callback.onError(new AppError.Api(null, null)));
                return;
            }
            IntConsumer progressOnMain = percent -> deliver(canceled, () -> onProgress.accept(percent));
            UploadFileDTO request = new UploadFileDTO(image.fileName(),
                    new ProgressRequestBody(image.bytes(), mediaTypeOf(image), progressOnMain),
                    plzsId, credentials.username(), credentials.password(), backendLanguage());
            Call<ApiResponseDTO<Boolean>> call = api.uploadImg(request.toParts());
            pendingCall.set(call);
            if (canceled.get()) {
                return;
            }
            enqueue(call, image.fileName(), callback);
        });
        return () -> {
            canceled.set(true);
            Call<ApiResponseDTO<Boolean>> call = pendingCall.get();
            if (call != null) {
                call.cancel();
            }
        };
    }

    private void enqueue(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull String fileName,
                         @NonNull ResultCallback<Boolean> callback) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<Boolean>> call,
                                   @NonNull Response<ApiResponseDTO<Boolean>> response) {
                if (call.isCanceled()) {
                    return;
                }
                Boolean uploaded = extractData(response, callback);
                if (uploaded == null) {
                    return;
                }
                if (!uploaded) {
                    Timber.w("El backend no ha aceptado la imagen %s", fileName);
                    callback.onError(new AppError.Api(null, null));
                    return;
                }
                Timber.i("Imagen %s enviada al backend", fileName);
                callback.onSuccess(Boolean.TRUE);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(t));
            }
        });
    }

    /** Null si no se puede leer o está vacía: no tiene sentido enviarla. */
    @WorkerThread
    @Nullable
    private ImageContent readImage(@NonNull String imageUri) {
        try {
            ImageContent image = reader.read(imageUri);
            if (image.bytes().length == 0) {
                Timber.w("La imagen %s está vacía", image.fileName());
                return null;
            }
            return image;
        } catch (IOException | SecurityException e) {
            // SecurityException: se ha perdido el permiso de lectura que concedió la galería
            Timber.e(e, "No se ha podido leer la imagen a enviar");
            return null;
        }
    }

    @NonNull
    private static MediaType mediaTypeOf(@NonNull ImageContent image) {
        MediaType type = image.mimeType() != null ? MediaType.parse(image.mimeType()) : null;
        return type != null ? type : MediaType.get(DEFAULT_MIME_TYPE);
    }

    @NonNull
    private static String backendLanguage() {
        String language = Locale.getDefault().getLanguage();
        return LANGUAGE_ENGLISH.equals(language) ? BACKEND_LANGUAGE_ENGLISH : language;
    }

    private void deliver(@NonNull AtomicBoolean canceled, @NonNull Runnable result) {
        mainExecutor.execute(() -> {
            if (!canceled.get()) {
                result.run();
            }
        });
    }

    /** Lee imágenes de la cámara (FileProvider) o de la galería (selector de fotos). */
    private static final class ContentImageReader implements ImageReader {

        private final ContentResolver resolver;

        ContentImageReader(@NonNull ContentResolver resolver) {
            this.resolver = resolver;
        }

        @WorkerThread
        @NonNull
        @Override
        public ImageContent read(@NonNull String imageUri) throws IOException {
            Uri uri = Uri.parse(imageUri);
            byte[] bytes;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) {
                    throw new FileNotFoundException("El proveedor no devuelve contenido");
                }
                // InputStream#readAllBytes no existe hasta API 33
                bytes = Okio.buffer(Okio.source(input)).readByteArray();
            }
            return new ImageContent(displayName(uri), resolver.getType(uri), bytes);
        }

        /** Nombre que da el proveedor; si no lo da, uno único con la extensión por defecto. */
        @NonNull
        private String displayName(@NonNull Uri uri) {
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }
            }
            return DEFAULT_FILE_PREFIX + System.currentTimeMillis() + DEFAULT_FILE_EXTENSION;
        }
    }
}
