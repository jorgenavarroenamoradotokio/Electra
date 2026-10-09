package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.dto.request.bulto.BultoTypeRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;
import com.redur.electra.data.remote.mapper.BultoTypeMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Catálogo de tipos de bulto del backend, con la descripción en el idioma del terminal.
 * Los callbacks llegan en el hilo principal y no se invocan si la operación se cancela.
 */
public class BultoTypeRepository extends BaseRepository {

    private static final String LANGUAGE_ENGLISH = "en";
    /** El backend identifica el inglés como "uk", no con el código ISO. */
    private static final String BACKEND_LANGUAGE_ENGLISH = "uk";

    private final BultoApiService api;
    private final UserSession session;
    private final BultoTypeMapper mapper;

    @Inject
    public BultoTypeRepository(BultoApiService api, UserSession session, BultoTypeMapper mapper) {
        this.api = api;
        this.session = session;
        this.mapper = mapper;
    }

    @NonNull
    public Cancellable getBultoTypes(@NonNull ResultCallback<List<BultoType>> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Consulta de tipos de bulto sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }

        Timber.i("Iniciamos el proceso de obtener los tipos de bultos");
        String locale = backendLanguage();
        Timber.d("El idioma del usuario que esta usando: %s", locale);

        BultoTypeRequestDTO request = new BultoTypeRequestDTO(credentials.username(), credentials.password(), locale);
        Timber.i("DTO request  %s", request);

        Call<ApiResponseDTO<List<BultoTypeDTO>>> call = api.getBultoTypes(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<List<BultoTypeDTO>>> call,
                                   @NonNull Response<ApiResponseDTO<List<BultoTypeDTO>>> response) {
                if (call.isCanceled()) {
                    return;
                }
                List<BultoTypeDTO> data = extractData(response, callback);
                if (data == null) {
                    return;
                }

                Timber.i("Los tipos de bultos obtenidos son: %s", data.toString());
                callback.onSuccess(mapper.toBultoTypes(data));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<List<BultoTypeDTO>>> call,
                                  @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(call, t));
            }
        });
        return call::cancel;
    }
}
