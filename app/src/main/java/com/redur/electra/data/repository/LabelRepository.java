package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.label.ZplLabel;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.api.LabelApiService;
import com.redur.electra.data.remote.dto.request.label.ZplLabelRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Generación de etiquetas ZPL en el backend, con las credenciales de la sesión. Las páginas llegan
 * como PNG en Base64 y se decodifican fuera del hilo principal. El resultado llega en el hilo
 * principal y no se entrega si la operación se cancela.
 */
public class LabelRepository extends BaseRepository {

    // El backend identifica el inglés como "uk" (igual que en el login)
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String BACKEND_LANGUAGE_ENGLISH = "uk";
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final LabelApiService api;
    private final UserSession session;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    @Inject
    public LabelRepository(LabelApiService api, UserSession session,
                           @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this.api = api;
        this.session = session;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /** Pide al backend la etiqueta y entrega su ZPL con las páginas listas para mostrar. */
    @NonNull
    public Cancellable createZplLabel(@NonNull ResultCallback<ZplLabel> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Generación de etiqueta sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }

        Timber.i("Iniciamos el proceso de obtener la etiqueta zpl para visualizar");
        String locale = backendLanguage();
        Timber.d("El idioma del usuario que esta usando: %s", locale);

        User user = session.getUser();
        ZplLabelRequestDTO request = new ZplLabelRequestDTO(credentials.username(), credentials.password(), locale, user != null ? user.plazaId() : null);
        Timber.i("DTO request  %s", request);

        AtomicBoolean canceled = new AtomicBoolean();
        Call<ApiResponseDTO<ZplLabelDTO>> call = api.createZpl(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<ZplLabelDTO>> call,
                                   @NonNull Response<ApiResponseDTO<ZplLabelDTO>> response) {
                if (call.isCanceled()) {
                    return;
                }

                ZplLabelDTO data = extractData(response, callback);
                if (data == null) {
                    return;
                }
                Timber.i("ZPL obtenida %s",data.zpl());
                ioExecutor.execute(() -> {
                    ZplLabel label = toLabel(data);
                    deliver(canceled, () -> {
                        if (label != null) {
                            callback.onSuccess(label);
                        } else {
                            callback.onError(new AppError.Api(null, null));
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<ZplLabelDTO>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(call, t));
            }
        });
        return () -> {
            canceled.set(true);
            call.cancel();
        };
    }

    /** Null si la etiqueta no trae ZPL o alguna página no es Base64 válido: no se puede mostrar. */
    @WorkerThread
    @Nullable
    private static ZplLabel toLabel(@NonNull ZplLabelDTO data) {
        if (data.zpl() == null || data.content() == null || data.content().isEmpty()) {
            Timber.e("Etiqueta sin ZPL o sin páginas");
            return null;
        }
        List<byte[]> pages = new ArrayList<>(data.content().size());
        for (String encoded : data.content()) {
            byte[] page = decodePage(encoded);
            if (page == null) {
                return null;
            }
            pages.add(page);
        }
        return new ZplLabel(data.zpl(), pages);
    }

    @Nullable
    private static byte[] decodePage(@Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) {
            Timber.e("Página de etiqueta vacía");
            return null;
        }
        try {
            // Se quitan los saltos de línea que algunos servidores insertan; el resto debe ser Base64
            // válido (el decodificador MIME ignoraría en silencio caracteres extraños)
            byte[] page = Base64.getDecoder().decode(WHITESPACE.matcher(encoded).replaceAll(""));
            return page.length > 0 ? page : null;
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Página de etiqueta con Base64 no válido");
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
