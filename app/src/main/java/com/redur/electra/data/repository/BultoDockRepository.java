package com.redur.electra.data.repository;

import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.bulto.BultoDock;
import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.dto.request.bulto.BultoDockRequestDT0;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoDockDT0;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.util.Optional;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Muelle de salida que corresponde a un bulto leído (INT_MST_ALM_MUELLES). Provisional: hasta que
 * el backend publique el servicio de muelles todo bulto sale por el mismo muelle; la firma ya es
 * asíncrona para que sustituirlo no afecte a quien lo usa. Un resultado vacío indica que el bulto
 * no tiene muelle asignado. El callback llega en el hilo principal y no se invoca si la operación
 * se cancela.
 */
public class BultoDockRepository extends BaseRepository{
    private final BultoApiService api;
    private final UserSession session;

    @Inject
    public BultoDockRepository(BultoApiService api, UserSession session) {
        this.api = api;
        this.session = session;
    }

    @NonNull
    public Cancellable getDock(@NonNull String barcode, @NonNull ResultCallback<BultoDock> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Consulta de muelle sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> {
            };
        }

        Timber.i("Iniciamos el proceso de obtener el muelle del bulto");
        String locale = backendLanguage();
        Timber.d("El idioma del usuario que esta usando: %s", locale);

        BultoDockRequestDT0 request = new BultoDockRequestDT0(credentials.username(), credentials.password(), locale, barcode);
        Timber.i("DTO request  %s", request);

        Call<ApiResponseDTO<BultoDockDT0>> call = api.getMuelleBulto(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<BultoDockDT0>> call, @NonNull Response<ApiResponseDTO<BultoDockDT0>> response) {
                if (call.isCanceled()) {
                    return;
                }

                BultoDockDT0 data = extractData(response, callback);
                if (data == null) {
                    return;
                }

                Timber.i("El muelle asociado al barcode es: %s", data.muelle());
                callback.onSuccess(new BultoDock(data.muelle(), data.plzsId(), data.postalCode(), data.country()));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<BultoDockDT0>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(call, t));
            }
        });
        return call::cancel;
    }
}
