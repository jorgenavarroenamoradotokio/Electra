package com.redur.electra.ui.bulto;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.repository.BultoTypeRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Selección del tipo de bulto. Carga los tipos al crearse y conserva el elegido y la opción de
 * fijarlo durante los cambios de configuración.
 */
@HiltViewModel
public class BultoTypeViewModel extends ViewModel {

    private final MutableLiveData<BultoTypeState> state = new MutableLiveData<>();
    private final MutableLiveData<BultoType> selectedType = new MutableLiveData<>();
    private final MutableLiveData<Boolean> fixedForNextReadings = new MutableLiveData<>(false);

    private final BultoTypeRepository repository;

    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public BultoTypeViewModel(BultoTypeRepository repository) {
        this.repository = repository;
        loadTypes();
    }

    public LiveData<BultoTypeState> getState() {
        return state;
    }

    /** Tipo elegido; null hasta que el usuario elige uno. */
    public LiveData<BultoType> getSelectedType() {
        return selectedType;
    }

    /** Si el tipo elegido se mantiene para los bultos que se lean a continuación. */
    public LiveData<Boolean> getFixedForNextReadings() {
        return fixedForNextReadings;
    }

    /** Solo se asigna un tipo elegido de la lista ya cargada. */
    public boolean canConfirm() {
        return state.getValue() instanceof BultoTypeState.Ready && selectedType.getValue() != null;
    }

    @MainThread
    public void onTypeSelected(@NonNull BultoType type) {
        if (state.getValue() instanceof BultoTypeState.Ready) {
            selectedType.setValue(type);
        }
    }

    @MainThread
    public void onFixedChanged(boolean fixed) {
        if (!Boolean.valueOf(fixed).equals(fixedForNextReadings.getValue())) {
            fixedForNextReadings.setValue(fixed);
        }
    }

    /** Solo tras un fallo o una lista vacía: con la carga en curso no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        BultoTypeState current = state.getValue();
        if (current instanceof BultoTypeState.Failed || current instanceof BultoTypeState.Empty) {
            loadTypes();
        }
    }

    @Override
    protected void onCleared() {
        // Evita que la respuesta llegue a un ViewModel ya destruido
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    private void loadTypes() {
        state.setValue(new BultoTypeState.Loading());
        pendingLoad = repository.getBultoTypes(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<BultoType> types) {
                pendingLoad = null;
                state.setValue(types.isEmpty()
                        ? new BultoTypeState.Empty()
                        : new BultoTypeState.Ready(types));
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLoad = null;
                state.setValue(new BultoTypeState.Failed(ErrorUiMapper.toUiText(error)));
            }
        });
    }
}
