package com.redur.electra.data.repository;

import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.di.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.local.dao.plaza.PlaceDao;
import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.remote.api.PlaceApiService;
import com.redur.electra.data.remote.dto.request.place.ChangePlaceRequestDTO;
import com.redur.electra.data.remote.dto.request.place.PlaceListRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;
import com.redur.electra.data.remote.mapper.PlaceMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Catálogo de plazas y cambio de la plaza del usuario. Las plazas se guardan en el terminal y se
 * completan con las que el backend añada: nunca se borran ni se modifican las ya guardadas.
 * Todos los callbacks llegan en el hilo principal y no se invocan si la operación se cancela.
 */
public class ChangePlaceRepository extends BaseRepository {

    private final PlaceApiService api;
    private final UserSession session;
    private final PlaceDao dao;
    private final PlaceMapper mapper;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    @Inject
    public ChangePlaceRepository(PlaceApiService api, UserSession session, PlaceDao dao,
                                 PlaceMapper mapper, @IoExecutor Executor ioExecutor,
                                 @MainExecutor Executor mainExecutor) {
        this.api = api;
        this.session = session;
        this.dao = dao;
        this.mapper = mapper;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /**
     * Plazas guardadas en el terminal, ordenadas por código. Si no se pueden leer se entregan
     * como vacías: la sincronización con el backend sigue siendo posible.
     */
    @NonNull
    public Cancellable getCachedPlaces(@NonNull ResultCallback<List<Place>> callback) {
        AtomicBoolean canceled = new AtomicBoolean();
        ioExecutor.execute(() -> {
            List<Place> places = readCachedPlaces();
            deliver(canceled, () -> callback.onSuccess(places));
        });
        return () -> canceled.set(true);
    }

    /**
     * Pide las plazas al backend, guarda en el terminal las que aún no existan y entrega la lista
     * completa resultante.
     */
    @NonNull
    public Cancellable syncPlaces(@NonNull ResultCallback<List<Place>> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Sincronización de plazas sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }

        AtomicBoolean canceled = new AtomicBoolean();
        PlaceListRequestDTO request = new PlaceListRequestDTO(
                credentials.username(), credentials.password(), Locale.getDefault().getLanguage());
        Call<ApiResponseDTO<List<PlaceDTO>>> call = api.getPlaces(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<List<PlaceDTO>>> call,
                                   @NonNull Response<ApiResponseDTO<List<PlaceDTO>>> response) {
                if (call.isCanceled()) {
                    return;
                }
                List<PlaceDTO> data = extractData(response, callback);
                if (data == null) {
                    return;
                }
                List<Place> remote = mapper.toPlaces(data);
                ioExecutor.execute(() -> {
                    List<Place> places = storeMissingPlaces(remote);
                    deliver(canceled, () -> callback.onSuccess(places));
                });
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<List<PlaceDTO>>> call,
                                  @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(t));
            }
        });
        return () -> {
            canceled.set(true);
            call.cancel();
        };
    }

    /**
     * Asigna al usuario la plaza {@code plzsId}. Si el backend la acepta, la sesión pasa a
     * reflejar la nueva plaza antes de notificar el éxito.
     */
    @NonNull
    public Cancellable updatePlzs(@NonNull String plzsId, @NonNull ResultCallback<Boolean> callback) {
        Credentials credentials = session.getCredentials();
        if (credentials == null) {
            Timber.w("Cambio de plaza sin sesión activa");
            callback.onError(new AppError.Api(null, null));
            return () -> { };
        }

        ChangePlaceRequestDTO request = new ChangePlaceRequestDTO(credentials.username(),
                credentials.password(), Locale.getDefault().getLanguage(), plzsId);
        Call<ApiResponseDTO<Boolean>> call = api.changePlace(request);
        call.enqueue(new Callback<>() {

            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull Response<ApiResponseDTO<Boolean>> response) {
                if (call.isCanceled()) {
                    return;
                }
                handleChangeResponse(response, plzsId, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<Boolean>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(t));
            }
        });
        return call::cancel;
    }

    private void handleChangeResponse(@NonNull Response<ApiResponseDTO<Boolean>> response,
                                      @NonNull String plzsId,
                                      @NonNull ResultCallback<Boolean> callback) {
        Boolean changed = extractData(response, callback);
        if (changed == null) {
            return;
        }
        if (!changed) {
            Timber.w("El backend no ha aplicado el cambio a la plaza %s", plzsId);
            callback.onError(new AppError.Api(null, null));
            return;
        }
        Timber.d("Plaza del usuario cambiada a %s", plzsId);
        session.updatePlaza(plzsId);
        callback.onSuccess(Boolean.TRUE);
    }

    @WorkerThread
    @NonNull
    private List<Place> readCachedPlaces() {
        try {
            return dao.getAll();
        } catch (SQLException e) {
            Timber.e(e, "No se han podido leer las plazas del terminal");
            return List.of();
        }
    }

    /** Si el terminal no puede guardarlas, se trabaja con las recibidas para no bloquear al usuario. */
    @WorkerThread
    @NonNull
    private List<Place> storeMissingPlaces(@NonNull List<Place> remote) {
        try {
            int inserted = dao.insertMissing(remote);
            Timber.d("Plazas nuevas guardadas en el terminal: %d de %d", inserted, remote.size());
            return dao.getAll();
        } catch (SQLException e) {
            Timber.e(e, "No se han podido guardar las plazas en el terminal");
            List<Place> sorted = new ArrayList<>(remote);
            sorted.sort(Comparator.comparing(Place::id));
            return sorted;
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
