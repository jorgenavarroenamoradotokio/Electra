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
import com.redur.electra.core.util.Validations;
import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.repository.InicdenciaRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Incidencia del último bulto leído: al leer un bulto carga las incidencias del tipo de operación
 * (CAR si no se indica otro), conserva el bulto leído (el de los argumentos hasta que se lee otro),
 * la incidencia elegida y la foto ya enviada y, al aceptar, exige las observaciones o la foto si la
 * incidencia lo pide. Todo ello se guarda en {@link SavedStateHandle}: la app de cámara puede hacer
 * que el sistema mate este proceso.
 */
@HiltViewModel
public class BultoIncidenceViewModel extends ViewModel {

    static final String KEY_SELECTED_CODE = "bultoIncidence.selectedCode";
    static final String KEY_PHOTO_URI = "bultoIncidence.photoUri";
    static final String KEY_READ_BARCODE = "bultoIncidence.readBarcode";

    private final MutableLiveData<BultoIncidenceState> state = new MutableLiveData<>();
    private final MutableLiveData<BultoIncidencia> selectedIncidence = new MutableLiveData<>();
    private final MutableLiveData<BultoIncidenceFormState> formState =
            new MutableLiveData<>(BultoIncidenceFormState.EMPTY);

    private final SavedStateHandle savedState;
    private final InicdenciaRepository repository;
    private final BultoIncidenceArgs args;

    @Nullable
    private Cancellable pendingLoad;

    @Inject
    public BultoIncidenceViewModel(SavedStateHandle savedState, InicdenciaRepository repository) {
        this.savedState = savedState;
        this.repository = repository;
        this.args = BultoIncidenceArgs.from(savedState);
        Timber.i("[ESTADO] Incidencias del bulto %s (operación %s)", currentBarcode(), args.operationType());
        // Con un bulto ya leído (en los argumentos o antes de recrearse el proceso) se carga ya
        if (currentBarcode() != null) {
            loadIncidences();
        } else {
            state.setValue(new BultoIncidenceState.WaitingForBarcode());
        }
    }

    @NonNull
    public BultoIncidenceArgs getArgs() {
        return args;
    }

    public LiveData<BultoIncidenceState> getState() {
        return state;
    }

    /** Bulto al que se graba la incidencia; null si aún no se ha leído ninguno. */
    public LiveData<String> getBarcode() {
        return savedState.getLiveData(KEY_READ_BARCODE, args.barcode());
    }

    /** Incidencia elegida; null hasta que el usuario elige una. */
    public LiveData<BultoIncidencia> getSelectedIncidence() {
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
    public void onIncidenceSelected(@NonNull BultoIncidencia incidence) {
        if (!(state.getValue() instanceof BultoIncidenceState.Ready)) {
            return;
        }
        Timber.i("[ACCION] Incidencia elegida: %s (exige foto: %b, exige observaciones: %b)",
                incidence.codigoIncidencia(), incidence.photoRequired(), incidence.observationsRequired());
        savedState.set(KEY_SELECTED_CODE, incidence.codigoIncidencia());
        selectedIncidence.setValue(incidence);
        // Lo que faltaba se refería a la incidencia anterior
        formState.setValue(BultoIncidenceFormState.EMPTY);
    }

    /**
     * Bulto leído con el escáner o la cámara: se cargan sus incidencias. Si es otro bulto, se
     * descarta lo elegido para el anterior: la incidencia y la foto eran suyas. Releer el mismo
     * bulto no repite la carga.
     */
    @MainThread
    public void onBarcodeScanned(@Nullable String barcode) {
        if (Validations.isBlank(barcode)) {
            return;
        }
        String code = barcode.trim();
        if (code.equals(currentBarcode())) {
            Timber.d("[ACCION] Bulto %s ya leído", code);
            return;
        }
        Timber.i("[ACCION] Bulto leído para la incidencia: %s", code);
        savedState.set(KEY_READ_BARCODE, code);
        reset();
        loadIncidences();
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
        Timber.i("[ACCION] Foto adjuntada a la incidencia");
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
        BultoIncidencia incidence = selectedIncidence.getValue();
        if (!canConfirm() || incidence == null) {
            Timber.i("[ACCION] Aceptar incidencia sin incidencia elegida");
            return null;
        }
        String photoUri = savedState.get(KEY_PHOTO_URI);
        String text = incidence.observationsRequired() ? observations.trim() : "";
        BultoIncidenceFormState form = new BultoIncidenceFormState(
                observationsError(incidence, text),
                incidence.photoRequired() && photoUri == null ? R.string.bulto_incidence_error_photo : null);
        formState.setValue(form);
        if (!form.isValid()) {
            Timber.i("[ACCION] Incidencia %s rechazada (observaciones válidas: %b, foto: %b)",
                    incidence.codigoIncidencia(), form.observationsError() == null, form.photoError() == null);
            return null;
        }
        // Las observaciones son texto libre: solo se registra su longitud
        Timber.i("[ACCION] Incidencia %s aceptada para el bulto %s (observaciones: %d caracteres, foto: %b)",
                incidence.codigoIncidencia(), currentBarcode(), text.length(), photoUri != null);
        BultoIncidenceEntry entry = new BultoIncidenceEntry(currentBarcode(), incidence, text, photoUri);
        reset();
        return entry;
    }

    /** Descarta la incidencia elegida, las observaciones y la foto. */
    @MainThread
    public void onCancelClicked() {
        Timber.i("[ACCION] Incidencia cancelada");
        reset();
    }

    /** Solo tras un fallo o una lista vacía: con la carga en curso no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        BultoIncidenceState current = state.getValue();
        if (current instanceof BultoIncidenceState.Failed || current instanceof BultoIncidenceState.Empty) {
            Timber.i("[ACCION] Reintentar carga de incidencias");
            loadIncidences();
        }
    }

    @Override
    protected void onCleared() {
        // Evita que la respuesta llegue a un ViewModel ya destruido
        cancelPendingLoad();
    }

    private void cancelPendingLoad() {
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
    }

    @Nullable
    @StringRes
    private static Integer observationsError(@NonNull BultoIncidencia incidence, @NonNull String text) {
        if (!incidence.observationsRequired()) {
            return null;
        }
        if (text.isEmpty()) {
            return R.string.bulto_incidence_error_observations;
        }
        return text.length() > BultoIncidencia.OBSERVATIONS_MAX_LENGTH
                ? R.string.bulto_incidence_error_observations_length
                : null;
    }

    @Nullable
    private String currentBarcode() {
        return savedState.contains(KEY_READ_BARCODE) ? savedState.get(KEY_READ_BARCODE) : args.barcode();
    }

    private void reset() {
        savedState.remove(KEY_SELECTED_CODE);
        savedState.remove(KEY_PHOTO_URI);
        selectedIncidence.setValue(null);
        formState.setValue(BultoIncidenceFormState.EMPTY);
    }

    /** Una lectura nueva sustituye a la carga en curso: su respuesta sería la del bulto anterior. */
    private void loadIncidences() {
        cancelPendingLoad();
        state.setValue(new BultoIncidenceState.Loading());
        pendingLoad = repository.getIncidenciasPorCodigo(args.operationType().code(), new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull List<BultoIncidencia> incidences) {
                pendingLoad = null;
                if (incidences.isEmpty()) {
                    Timber.i("[ESTADO] Sin incidencias para la operación %s", args.operationType());
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
    private void restoreSelection(@NonNull List<BultoIncidencia> incidences) {
        String code = savedState.get(KEY_SELECTED_CODE);
        if (code == null) {
            return;
        }
        BultoIncidencia restored = null;
        for (BultoIncidencia incidence : incidences) {
            if (incidence.codigoIncidencia().equals(code)) {
                restored = incidence;
                break;
            }
        }
        if (restored == null) {
            Timber.i("[ESTADO] La incidencia %s elegida antes ya no está en la lista", code);
            savedState.remove(KEY_SELECTED_CODE);
        }
        selectedIncidence.setValue(restored);
    }
}
