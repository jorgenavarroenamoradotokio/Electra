package com.redur.electra.ui.bulto.tipo;

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
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.repository.BultoTypeRepository;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Selección del tipo de bulto. Carga los tipos al crearse y conserva el elegido y la opción de
 * fijarlo durante los cambios de configuración. Abierta como selector ({@link BultoTypePicker}),
 * marca el tipo que ya estaba fijado en cuanto llega la lista.
 */
@HiltViewModel
public class BultoTypeViewModel extends ViewModel {

    private final MutableLiveData<BultoTypeState> state = new MutableLiveData<>();
    private final MutableLiveData<BultoType> selectedType = new MutableLiveData<>();
    private final MutableLiveData<Boolean> fixedForNextReadings = new MutableLiveData<>(false);

    private final BultoTypeRepository repository;
    private final boolean picker;
    /** Tipo que se marca al recibir la lista; solo la primera vez, luego decide el usuario. */
    @Nullable
    private String preselectedCode;

    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public BultoTypeViewModel(SavedStateHandle savedState, BultoTypeRepository repository) {
        this.repository = repository;
        this.picker = BultoTypePicker.isPicker(savedState);
        this.preselectedCode = BultoTypePicker.preselectedCode(savedState);
        loadTypes();
    }

    /** Si se abrió como selector desde otra pantalla, que recibe el tipo elegido. */
    public boolean isPicker() {
        return picker;
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
            Timber.i("[ACCION] Tipo de bulto elegido: %s", type.code());
            selectedType.setValue(type);
        }
    }

    @MainThread
    public void onFixedChanged(boolean fixed) {
        if (!Boolean.valueOf(fixed).equals(fixedForNextReadings.getValue())) {
            Timber.i("[ACCION] Fijar tipo para los siguientes bultos: %b", fixed);
            fixedForNextReadings.setValue(fixed);
        }
    }

    /** Solo tras un fallo o una lista vacía: con la carga en curso no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        BultoTypeState current = state.getValue();
        if (current instanceof BultoTypeState.Failed || current instanceof BultoTypeState.Empty) {
            Timber.i("[ACCION] Reintentar carga de tipos de bulto");
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

    private void preselect(@NonNull List<BultoType> types) {
        if (preselectedCode == null || selectedType.getValue() != null) {
            return;
        }
        for (BultoType type : types) {
            if (Objects.equals(type.code(), preselectedCode)) {
                selectedType.setValue(type);
                break;
            }
        }
        preselectedCode = null;
    }

    private void loadTypes() {
        state.setValue(new BultoTypeState.Loading());
        pendingLoad = repository.getBultoTypes(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<BultoType> types) {
                pendingLoad = null;
                if (types.isEmpty()) {
                    Timber.i("[ESTADO] Sin tipos de bulto que mostrar");
                }
                state.setValue(types.isEmpty() ? new BultoTypeState.Empty() : new BultoTypeState.Ready(types));
                preselect(types);
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLoad = null;
                state.setValue(new BultoTypeState.Failed(ErrorUiMapper.toUiText(error)));
            }
        });
    }
}
