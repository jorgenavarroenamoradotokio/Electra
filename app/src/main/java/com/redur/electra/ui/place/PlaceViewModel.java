package com.redur.electra.ui.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.ChangePlaceRepository;
import com.redur.electra.data.session.UserSession;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Cambio de plaza. Al crearse muestra las plazas guardadas en el terminal y, en paralelo, las
 * sincroniza con el backend. Solo si el terminal no tiene ninguna, la carga bloquea la selección.
 */
@HiltViewModel
public class PlaceViewModel extends ViewModel {

    private final MutableLiveData<List<Place>> places = new MutableLiveData<>(List.of());
    private final MutableLiveData<UiState> loadState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<Place> selectedPlace = new MutableLiveData<>();
    private final MutableLiveData<UiState> changePlaceState = new MutableLiveData<>(new UiState.Idle());

    private final ChangePlaceRepository repository;
    private final UserSession session;

    @Nullable
    private Cancellable pendingCacheRead;
    @Nullable
    private Cancellable pendingSync;
    @Nullable
    private Cancellable pendingChange;

    @Inject
    public PlaceViewModel(ChangePlaceRepository repository, UserSession session) {
        this.repository = repository;
        this.session = session;
        loadPlaces();
    }

    /** Plazas seleccionables, ordenadas por código. */
    public LiveData<List<Place>> getPlaces() {
        return places;
    }

    /**
     * Carga de plazas: Loading → Success | Error. Si el terminal ya tenía plazas pasa a Success al
     * instante y la sincronización posterior solo actualiza la lista.
     */
    public LiveData<UiState> getLoadState() {
        return loadState;
    }

    /** Plaza elegida en el desplegable; null hasta que el usuario elige una. */
    public LiveData<Place> getSelectedPlace() {
        return selectedPlace;
    }

    /** Estado del cambio de plaza (Idle → Loading → Success | Error). */
    public LiveData<UiState> getChangePlaceState() {
        return changePlaceState;
    }

    /** Plaza actual del usuario. Null si no tiene o si la sesión se ha perdido. */
    @Nullable
    public String getCurrentPlazaId() {
        User user = session.getUser();
        return user != null ? user.plazaId() : null;
    }

    /** Solo se confirma una plaza distinta de la actual y sin otro cambio en curso o terminado. */
    public boolean canConfirm() {
        Place selected = selectedPlace.getValue();
        UiState change = changePlaceState.getValue();
        return selected != null
                && !selected.id().equals(getCurrentPlazaId())
                && !(change instanceof UiState.Loading)
                && !(change instanceof UiState.Success);
    }

    public void onPlaceSelected(@NonNull Place place) {
        if (changePlaceState.getValue() instanceof UiState.Loading) {
            return;
        }
        selectedPlace.setValue(place);
        // Elegir otra plaza descarta el error del intento anterior
        if (changePlaceState.getValue() instanceof UiState.Error) {
            changePlaceState.setValue(new UiState.Idle());
        }
    }

    /**
     * En caso de no poder cargar las plazas mostramos al usuarios la opcion de volver a recargarlo
     */
    public void onRetryLoadClicked() {
        if (loadState.getValue() instanceof UiState.Loading) {
            return;
        }
        loadPlaces();
    }

    public void onConfirmClicked() {
        // También evita peticiones duplicadas mientras hay una en curso
        if (!canConfirm()) {
            return;
        }
        Place selected = selectedPlace.getValue();
        changePlaceState.setValue(new UiState.Loading());
        pendingChange = repository.updatePlzs(selected.id(), new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull Boolean result) {
                pendingChange = null;
                changePlaceState.setValue(new UiState.Success());
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingChange = null;
                changePlaceState.setValue(new UiState.Error(ErrorUiMapper.toUiText(error)));
            }
        });
    }

    private void loadPlaces() {
        loadState.setValue(new UiState.Loading());
        pendingCacheRead = repository.getCachedPlaces(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<Place> cached) {
                pendingCacheRead = null;
                if (!cached.isEmpty()) {
                    showPlaces(cached);
                }
                syncPlaces();
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingCacheRead = null;
                syncPlaces();
            }
        });
    }

    private void syncPlaces() {
        pendingSync = repository.syncPlaces(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<Place> synced) {
                pendingSync = null;
                if (synced.isEmpty()) {
                    loadState.setValue(new UiState.Error(new UiText.Res(R.string.change_plaza_empty)));
                    return;
                }
                showPlaces(synced);
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingSync = null;
                if (hasPlaces()) {
                    // Las plazas del terminal siguen siendo válidas: no se interrumpe al usuario
                    Timber.w("No se han podido sincronizar las plazas; se usan las del terminal");
                    return;
                }
                loadState.setValue(new UiState.Error(ErrorUiMapper.toUiText(error)));
            }
        });
    }

    private void showPlaces(@NonNull List<Place> loaded) {
        places.setValue(loaded);
        loadState.setValue(new UiState.Success());
    }

    private boolean hasPlaces() {
        List<Place> current = places.getValue();
        return current != null && !current.isEmpty();
    }

    @Override
    protected void onCleared() {
        // Evita que las respuestas lleguen a un ViewModel ya destruido
        cancel(pendingCacheRead);
        cancel(pendingSync);
        cancel(pendingChange);
        pendingCacheRead = null;
        pendingSync = null;
        pendingChange = null;
    }

    private static void cancel(@Nullable Cancellable operation) {
        if (operation != null) {
            operation.cancel();
        }
    }
}
