package com.redur.electra.ui.carga.camion;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.data.repository.TruckLoadRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Carga del camión: expediciones asignadas con lo escaneado de cada una. La lista se ordena y se
 * filtra por vuelta en local, y se elige una expedición para actuar sobre ella. Refrescar vuelve a
 * pedir la carga sin dejar de mostrar la actual. Orden, filtro y elegida se guardan en
 * {@link SavedStateHandle}; la carga se vuelve a pedir si el sistema mata el proceso.
 * <p>
 * Solo UI: la carga es provisional y las acciones sobre expediciones aún no se graban.
 */
@HiltViewModel
public class TruckLoadViewModel extends ViewModel {

    static final String KEY_SORT = "truckLoad.sort";
    static final String KEY_TRIP_FILTER = "truckLoad.tripFilter";
    static final String KEY_SELECTED = "truckLoad.selected";

    private final MutableLiveData<TruckLoadState> state = new MutableLiveData<>(new TruckLoadState.Loading());

    private final SavedStateHandle savedState;
    private final TruckLoadRepository repository;

    /** Toda la carga tal como llega, sin filtrar ni ordenar. */
    @NonNull
    private List<Expedition> loaded = List.of();
    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public TruckLoadViewModel(SavedStateHandle savedState, TruckLoadRepository repository) {
        this.savedState = savedState;
        this.repository = repository;
        load();
    }

    public LiveData<TruckLoadState> getState() {
        return state;
    }

    /** Con una petición en curso no se lanza otra. */
    @MainThread
    public void onRefreshClicked() {
        TruckLoadState current = state.getValue();
        if (current instanceof TruckLoadState.Loading
                || current instanceof TruckLoadState.Ready ready && ready.refreshing()) {
            Timber.d("[ACCION] Refresco de la carga ignorado: ya hay una petición en curso");
            return;
        }
        Timber.i("[ACCION] Refrescar la carga del camión");
        load();
    }

    /** Solo tras un fallo. */
    @MainThread
    public void onRetryClicked() {
        if (state.getValue() instanceof TruckLoadState.Failed) {
            Timber.i("[ACCION] Reintentar la carga del camión");
            load();
        }
    }

    @MainThread
    public void onSortSelected(@NonNull TruckLoadSort sort) {
        if (!(state.getValue() instanceof TruckLoadState.Ready)) {
            return;
        }
        Timber.i("[ACCION] Ordenar la carga por %s", sort);
        savedState.set(KEY_SORT, sort.name());
        publishReady(isRefreshing(), null);
    }

    /** Filtra por la vuelta indicada; null para ver todas. */
    @MainThread
    public void onTripFilterSelected(@Nullable Integer trip) {
        if (!(state.getValue() instanceof TruckLoadState.Ready)) {
            return;
        }
        Timber.i("[ACCION] Filtrar la carga por vuelta %s", trip != null ? trip : "-");
        savedState.set(KEY_TRIP_FILTER, trip);
        publishReady(isRefreshing(), null);
    }

    /** Elige la expedición; tocar la ya elegida la deja sin elegir. */
    @MainThread
    public void onExpeditionClicked(@NonNull String number) {
        if (!(state.getValue() instanceof TruckLoadState.Ready)) {
            return;
        }
        boolean deselect = number.equals(savedState.get(KEY_SELECTED));
        Timber.i("[ACCION] Expedición %s %s", number, deselect ? "deseleccionada" : "seleccionada");
        savedState.set(KEY_SELECTED, deselect ? null : number);
        publishReady(isRefreshing(), null);
    }

    /** El fallo del refresco ya se ha avisado: no se repite al volver a la pantalla. */
    @MainThread
    public void onRefreshErrorShown() {
        if (state.getValue() instanceof TruckLoadState.Ready ready && ready.refreshError() != null) {
            publishReady(ready.refreshing(), null);
        }
    }

    @Override
    protected void onCleared() {
        cancelPendingLoad();
    }

    private void load() {
        cancelPendingLoad();
        // Con carga a la vista se refresca encima; si no, se muestra el estado de carga
        boolean hasData = state.getValue() instanceof TruckLoadState.Ready;
        if (hasData) {
            publishReady(true, null);
        } else {
            state.setValue(new TruckLoadState.Loading());
        }
        pendingLoad = repository.getExpeditions(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<Expedition> result) {
                pendingLoad = null;
                Timber.i("[ESTADO] Carga del camión con %d expediciones", result.size());
                loaded = List.copyOf(result);
                if (loaded.isEmpty()) {
                    state.setValue(new TruckLoadState.Empty());
                } else {
                    publishReady(false, null);
                }
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLoad = null;
                Timber.w("[ESTADO] No se pudo obtener la carga del camión");
                UiText message = ErrorUiMapper.toUiText(error);
                if (hasData) {
                    publishReady(false, message);
                } else {
                    state.setValue(new TruckLoadState.Failed(message));
                }
            }
        });
    }

    private void cancelPendingLoad() {
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    private boolean isRefreshing() {
        return state.getValue() instanceof TruckLoadState.Ready ready && ready.refreshing();
    }

    /**
     * Pinta la carga con el orden, filtro y elegida guardados. Un filtro o una elegida que ya no
     * están en la carga (p. ej. tras refrescar) se descartan.
     */
    private void publishReady(boolean refreshing, @Nullable UiText refreshError) {
        TreeSet<Integer> trips = new TreeSet<>();
        int complete = 0;
        for (Expedition expedition : loaded) {
            trips.add(expedition.trip());
            if (expedition.isComplete()) {
                complete++;
            }
        }

        Integer tripFilter = savedState.get(KEY_TRIP_FILTER);
        if (tripFilter != null && !trips.contains(tripFilter)) {
            tripFilter = null;
            savedState.remove(KEY_TRIP_FILTER);
        }

        List<Expedition> visible = new ArrayList<>(loaded.size());
        for (Expedition expedition : loaded) {
            if (tripFilter == null || expedition.trip() == tripFilter) {
                visible.add(expedition);
            }
        }
        TruckLoadSort sort = currentSort();
        visible.sort(sort.comparator());

        String selected = savedState.get(KEY_SELECTED);
        if (selected != null && !containsNumber(visible, selected)) {
            selected = null;
            savedState.remove(KEY_SELECTED);
        }

        state.setValue(new TruckLoadState.Ready(visible, new ArrayList<>(trips), tripFilter, sort,
                selected, complete, loaded.size(), TruckLoadSummary.of(loaded), refreshing, refreshError));
    }

    @NonNull
    private TruckLoadSort currentSort() {
        String name = savedState.get(KEY_SORT);
        if (name == null) {
            return TruckLoadSort.LOAD_ORDER;
        }
        try {
            return TruckLoadSort.valueOf(name);
        } catch (IllegalArgumentException e) {
            Timber.w("[ESTADO] Orden guardado desconocido: %s", name);
            return TruckLoadSort.LOAD_ORDER;
        }
    }

    private static boolean containsNumber(@NonNull List<Expedition> expeditions, @NonNull String number) {
        for (Expedition expedition : expeditions) {
            if (expedition.number().equals(number)) {
                return true;
            }
        }
        return false;
    }
}
