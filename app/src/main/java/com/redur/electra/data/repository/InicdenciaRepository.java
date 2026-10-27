package com.redur.electra.data.repository;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.dto.request.incidencia.IncidenciaRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;
import com.redur.electra.data.remote.mapper.IncidenciaMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class InicdenciaRepository extends BaseRepository {

    private final BultoApiService api;
    private final UserSession session;
    private final IncidenciaMapper mapper;

    @Inject
    public InicdenciaRepository(BultoApiService api, UserSession session, IncidenciaMapper mapper) {
        this.api = api;
        this.session = session;
        this.mapper = mapper;
    }

    @NonNull
    public Cancellable getIncidenciasPorCodigo(@NotNull String codigoAplicacion, @NonNull ResultCallback<List<BultoIncidencia>> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Consulta de incidencias sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> {
            };
        }

        Timber.i("Iniciamos el proceso de obtener las incidencias");
        String locale = backendLanguage();
        Timber.d("El idioma del usuario que esta usando: %s", locale);

        IncidenciaRequestDTO request = new IncidenciaRequestDTO(credentials.username(), credentials.password(), locale, codigoAplicacion);
        Timber.i("DTO request  %s", request);

        Call<ApiResponseDTO<List<IncidenciaDTO>>> call = api.getIncidenciaPorAplicacion(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<List<IncidenciaDTO>>> call, @NonNull Response<ApiResponseDTO<List<IncidenciaDTO>>> response) {
                if (call.isCanceled()) {
                    return;
                }

                List<IncidenciaDTO> data = extractData(response, callback);
                if (data == null)
                    return;

                Timber.i("Los tipos de bultos obtenidos son: %s", data.toString());
                callback.onSuccess(mapper.toIncidencia(data));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<List<IncidenciaDTO>>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(call, t));
            }
        });
        return call::cancel;
    }
}
