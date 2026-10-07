package com.redur.electra.ui.bulto.incidencia;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.data.model.bulto.BultoIncidence;
import com.redur.electra.data.repository.BultoIncidenceRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Incidencia del último bulto leído: carga las incidencias del tipo de operación, conserva la
 * elegida y la foto ya enviada y, al aceptar, exige las observaciones o la foto si la incidencia
 * lo pide. La elegida y la foto se guardan en {@link SavedStateHandle}: la app de cámara puede
 * hacer que el sistema mate este proceso.
 */
@HiltViewModel
public class BultoIncidenceViewModel extends ViewModel {

    static final String KEY_SELECTED_CODE = "bultoIncidence.selectedCode";
    static final String KEY_PHOTO_URI = "bultoIncidence.photoUri";

    private final MutableLiveData<BultoIncidenceState> state = new MutableLiveData<>();
    private final MutableLiveData<BultoIncidence> selectedIncidence = new MutableLiveData<>();
    private final MutableLiveData<BultoIncidenceFormState> formState =
            new MutableLiveData<>(BultoIncidenceFormState.EMPTY);

    private final SavedStateHandle savedState;
    private final BultoIncidenceRepository repository;
    private final BultoIncidenceArgs args;

    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public BultoIncidenceViewModel(SavedStateHandle savedState, BultoIncidenceRepository repository) {
        this.savedState = savedState;
        this.repository = repository;
        this.args = BultoIncidenceArgs.from(savedState);
        loadIncidences();
    }

    @NonNull
    public BultoIncidenceArgs getArgs() {
        return args;
    }

    public LiveData<BultoIncidenceState> getState() {
        return state;
    }

    /** Incidencia elegida; null hasta que el usuario elige una. */
    public LiveData<BultoIncidence> getSelectedIncidence() {
        return selectedIncidence;
    }

    /** content:// de la foto ya enviada; null si aún no se ha hecho. */
    public LiveData<String> getPhotoUri() {
        return savedState.getLiveData(KEY_PHOTO_URI);
    }

    public LiveData<BultoIncidenceFormState> getFormState() {
        return formState;
    }

    /** Solo se graba una incidencia elegida de la lista ya cargada. */
    public boolean canConfirm() {
        return state.getValue() instanceof BultoIncidenceState.Ready && selectedIncidence.getValue() != null;
    }

    /** La foto se hace para la incidencia: hasta elegir una no se sabe si la exige. */
    public boolean canTakePhoto() {
        return canConfirm();
    }

    @MainThread
    public void onIncidenceSelected(@NonNull BultoIncidence incidence) {
        if (!(state.getValue() instanceof BultoIncidenceState.Ready)) {
            return;
        }
        savedState.set(KEY_SELECTED_CODE, incidence.code());
        selectedIncidence.setValue(incidence);
        // Lo que faltaba se refería a la incidencia anterior
        formState.setValue(BultoIncidenceFormState.EMPTY);
    }

    /** Al corregir las observaciones deja de mostrarse su error. */
    @MainThread
    public void onObservationsChanged() {
        BultoIncidenceFormState current = formState.getValue();
        if (current != null && current.observationsError() != null) {
            formState.setValue(new BultoIncidenceFormState(null, current.photoError()));
        }
    }

    /** Foto hecha o elegida y ya enviada desde la hoja de foto. */
    @MainThread
    public void onPhotoAttached(@NonNull String photoUri) {
        savedState.set(KEY_PHOTO_URI, photoUri);
        BultoIncidenceFormState current = formState.getValue();
        if (current != null && current.photoError() != null) {
            formState.setValue(new BultoIncidenceFormState(current.observationsError(), null));
        }
    }

    /**
     * Valida la incidencia elegida con lo que exige y, si es correcta, deja la pantalla lista para
     * otra incidencia.
     *
     * @param observations lo escrito en observaciones; se ignora si la incidencia no las exige.
     * @return la incidencia a grabar, o null si falta algo (el motivo queda en {@link #getFormState()}).
     */
    @MainThread
    @Nullable
    public BultoIncidenceEntry onConfirmClicked(@NonNull String observations) {
        BultoIncidence incidence = selectedIncidence.getValue();
        if (!canConfirm() || incidence == null) {
            return null;
        }
        String photoUri = savedState.get(KEY_PHOTO_URI);
        String text = incidence.observationsRequired() ? observations.trim() : "";
        BultoIncidenceFormState form = new BultoIncidenceFormState(
                observationsError(incidence, text),
                incidence.photoRequired() && photoUri == null ? R.string.bulto_incidence_error_photo : null);
        formState.setValue(form);
        if (!form.isValid()) {
            return null;
        }
        BultoIncidenceEntry entry = new BultoIncidenceEntry(args.barcode(), incidence, text, photoUri);
        reset();
        return entry;
    }

    /** Descarta la incidencia elegida, las observaciones y la foto. */
    @MainThread
    public void onCancelClicked() {
        reset();
    }

    /** Solo tras un fallo o una lista vacía: con la carga en curso no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        BultoIncidenceState current = state.getValue();
        if (current instanceof BultoIncidenceState.Failed || current instanceof BultoIncidenceState.Empty) {
            loadIncidences();
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

    @Nullable
    @StringRes
    private static Integer observationsError(@NonNull BultoIncidence incidence, @NonNull String text) {
        if (!incidence.observationsRequired()) {
            return null;
        }
        if (text.isEmpty()) {
            return R.string.bulto_incidence_error_observations;
        }
        return text.length() > BultoIncidence.OBSERVATIONS_MAX_LENGTH
                ? R.string.bulto_incidence_error_observations_length
                : null;
    }

    private void reset() {
        savedState.remove(KEY_SELECTED_CODE);
        savedState.remove(KEY_PHOTO_URI);
        selectedIncidence.setValue(null);
        formState.setValue(BultoIncidenceFormState.EMPTY);
    }

    private void loadIncidences() {
        state.setValue(new BultoIncidenceState.Loading());
        pendingLoad = repository.getIncidences(args.operationType(), new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<BultoIncidence> incidences) {
                pendingLoad = null;
                if (incidences.isEmpty()) {
                    state.setValue(new BultoIncidenceState.Empty());
                    return;
                }
                state.setValue(new BultoIncidenceState.Ready(incidences));
                restoreSelection(incidences);
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLoad = null;
                state.setValue(new BultoIncidenceState.Failed(ErrorUiMapper.toUiText(error)));
            }
        });
    }

    /** Tras recrearse el proceso se vuelve a marcar la incidencia elegida, si sigue en la lista. */
    private void restoreSelection(@NonNull List<BultoIncidence> incidences) {
        String code = savedState.get(KEY_SELECTED_CODE);
        if (code == null) {
            return;
        }
        BultoIncidence restored = null;
        for (BultoIncidence incidence : incidences) {
            if (incidence.code().equals(code)) {
                restored = incidence;
                break;
            }
        }
        if (restored == null) {
            savedState.remove(KEY_SELECTED_CODE);
        }
        selectedIncidence.setValue(restored);
    }
}
