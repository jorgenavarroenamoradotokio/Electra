package com.redur.electra.ui.bulto.muelle;

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
import com.redur.electra.core.util.Validations;
import com.redur.electra.data.model.bulto.BultoDock;
import com.redur.electra.data.repository.BultoDockRepository;

import java.util.Optional;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Puerta de salida: por cada bulto leído con el escáner se busca su muelle, que queda a la vista
 * hasta leer el bulto siguiente o cancelar. El CB leído se guarda en {@link SavedStateHandle} para
 * volver a buscar su muelle si el sistema mata el proceso.
 */
@HiltViewModel
public class BultoDockViewModel extends ViewModel {

    static final String KEY_BARCODE = "bultoDock.barcode";

    private final MutableLiveData<BultoDockState> state = new MutableLiveData<>(new BultoDockState.Waiting());

    private final SavedStateHandle savedState;
    private final BultoDockRepository repository;

    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public BultoDockViewModel(SavedStateHandle savedState, BultoDockRepository repository) {
        this.savedState = savedState;
        this.repository = repository;
        String barcode = savedState.get(KEY_BARCODE);
        if (barcode != null) {
            loadDock(barcode);
        }
    }

    public LiveData<BultoDockState> getState() {
        return state;
    }

    /**
     * Bulto leído con el escáner. Una lectura nueva sustituye a la anterior aunque su muelle
     * siga a la vista; repetir la que se está consultando no lanza otra consulta.
     */
    @MainThread
    public void onBarcodeScanned(@Nullable String barcode) {
        if (Validations.isBlank(barcode)) {
            return;
        }
        String code = barcode.trim();
        if (state.getValue() instanceof BultoDockState.Loading loading && loading.barcode().equals(code)) {
            return;
        }
        loadDock(code);
    }

    /** Solo tras un fallo: con la consulta en curso no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        if (state.getValue() instanceof BultoDockState.Failed failed) {
            loadDock(failed.barcode());
        }
    }

    /** Descarta la lectura en curso: se espera el bulto siguiente. */
    @MainThread
    public void onCancelClicked() {
        reset();
    }

    @Override
    protected void onCleared() {
        cancelPendingLoad();
    }

    private void reset() {
        cancelPendingLoad();
        savedState.remove(KEY_BARCODE);
        state.setValue(new BultoDockState.Waiting());
    }

    private void cancelPendingLoad() {
        // Evita que la respuesta de un bulto anterior sustituya a la del actual
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    private void loadDock(@NonNull String barcode) {
        cancelPendingLoad();
        savedState.set(KEY_BARCODE, barcode);
        state.setValue(new BultoDockState.Loading(barcode));
        pendingLoad = repository.getDock(barcode, new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull Optional<BultoDock> dock) {
                pendingLoad = null;
                state.setValue(dock.<BultoDockState>map(found -> new BultoDockState.Found(barcode, found))
                        .orElseGet(() -> new BultoDockState.NotFound(barcode)));
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLoad = null;
                state.setValue(new BultoDockState.Failed(barcode, ErrorUiMapper.toUiText(error)));
            }
        });
    }
}
