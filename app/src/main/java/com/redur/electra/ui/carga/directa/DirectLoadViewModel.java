package com.redur.electra.ui.carga.directa;

import android.os.Bundle;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.util.Validations;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.ui.bulto.tipo.BultoTypePicker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Carga con precinto: al entrar se pide el precinto y, una vez aceptado, se leen bultos. En
 * cualquier momento se puede fijar, cambiar o quitar el tipo de bulto que se asocia a las lecturas
 * siguientes, y corregir el tipo de un bulto ya leído. Precinto, tipo fijado, último CB y lecturas
 * se guardan en {@link SavedStateHandle} para no perder la carga si el sistema mata el proceso.
 * <p>
 * Solo UI: las lecturas aún no se validan como CB Redur nacional ni se graban en el servidor.
 */
@HiltViewModel
public class DirectLoadViewModel extends ViewModel {

    /** Precinto: numérico de 8 dígitos, leído de su CB o tecleado. */
    static final int SEAL_LENGTH = 8;
    private static final Pattern SEAL_PATTERN = Pattern.compile("\\d{" + SEAL_LENGTH + "}");

    static final String KEY_SEAL = "directLoad.seal";
    static final String KEY_FIXED_TYPE = "directLoad.fixedType";
    static final String KEY_READINGS = "directLoad.readings";
    static final String KEY_LAST_BARCODE = "directLoad.lastBarcode";
    /** CB del bulto cuyo tipo se está corrigiendo en el selector; sin él, el selector fija el tipo. */
    static final String KEY_TYPE_TARGET = "directLoad.typeTarget";
    private static final String KEY_READING_BARCODE = "barcode";
    private static final String KEY_READING_TYPE = "type";

    private final MutableLiveData<DirectLoadState> state = new MutableLiveData<>();
    private final SavedStateHandle savedState;

    @Inject
    public DirectLoadViewModel(SavedStateHandle savedState) {
        this.savedState = savedState;
        String seal = savedState.get(KEY_SEAL);
        if (seal == null) {
            state.setValue(new DirectLoadState.SealEntry(null));
        } else {
            Timber.i("[ESTADO] Se recupera la carga del precinto %s", seal);
            state.setValue(new DirectLoadState.Reading(seal,
                    BultoTypePicker.fromBundle(savedState.get(KEY_FIXED_TYPE)), savedState.get(KEY_LAST_BARCODE),
                    restoreReadings(), null));
        }
    }

    public LiveData<DirectLoadState> getState() {
        return state;
    }

    /** Código del tipo fijado, para marcarlo al abrir el selector; null si no hay ninguno. */
    @Nullable
    public String getFixedTypeCode() {
        if (state.getValue() instanceof DirectLoadState.Reading reading && reading.fixedType() != null) {
            return reading.fixedType().code();
        }
        return null;
    }

    /** Acepta el precinto si es numérico de 8 dígitos; si no, explica qué falta. */
    @MainThread
    public void onSealEntered(@Nullable String text) {
        if (!(state.getValue() instanceof DirectLoadState.SealEntry)) {
            return;
        }
        String seal = text != null ? text.trim() : "";
        if (!SEAL_PATTERN.matcher(seal).matches()) {
            Timber.i("[ACCION] Precinto no válido (%d caracteres)", seal.length());
            state.setValue(new DirectLoadState.SealEntry(seal.isEmpty()
                    ? R.string.direct_load_error_seal_required
                    : R.string.direct_load_error_seal_format));
            return;
        }
        Timber.i("[ACCION] Precinto aceptado: %s", seal);
        savedState.set(KEY_SEAL, seal);
        state.setValue(new DirectLoadState.Reading(seal, null, null, List.of(), null));
    }

    /** Al corregir el precinto se retira el error, que ya no describe lo que hay escrito. */
    @MainThread
    public void onSealEdited() {
        if (state.getValue() instanceof DirectLoadState.SealEntry entry && entry.sealError() != null) {
            state.setValue(new DirectLoadState.SealEntry(null));
        }
    }

    /** Añade el bulto con el tipo fijado en este momento. Un CB vacío se ignora. */
    @MainThread
    public void onBarcodeScanned(@Nullable String barcode) {
        if (Validations.isBlank(barcode)
                || !(state.getValue() instanceof DirectLoadState.Reading current)) {
            return;
        }
        String code = barcode.trim();
        savedState.set(KEY_LAST_BARCODE, code);
        if (indexOf(current.readings(), code) >= 0) {
            Timber.i("[ACCION] Bulto %s ya leído en el precinto %s", code, current.seal());
            state.setValue(new DirectLoadState.Reading(current.seal(), current.fixedType(), code,
                    current.readings(), new DirectLoadState.Feedback.Duplicate(code)));
            return;
        }
        DirectLoadReading reading = new DirectLoadReading(code, current.fixedType());
        Timber.i("[ACCION] Bulto %s leído en el precinto %s con tipo %s", code, current.seal(),
                current.fixedType() != null ? current.fixedType().code() : "-");
        List<DirectLoadReading> readings = new ArrayList<>(current.readings().size() + 1);
        readings.add(reading);
        readings.addAll(current.readings());
        saveReadings(readings);
        state.setValue(new DirectLoadState.Reading(current.seal(), current.fixedType(), code, readings,
                new DirectLoadState.Feedback.Added(reading)));
    }

    /** El selector que se va a abrir fija el tipo de las lecturas siguientes. */
    @MainThread
    public void onFixedTypePickerRequested() {
        savedState.remove(KEY_TYPE_TARGET);
    }

    /** El selector que se va a abrir corrige el tipo del bulto ya leído {@code barcode}. */
    @MainThread
    public void onReadingTypePickerRequested(@NonNull String barcode) {
        Timber.i("[ACCION] Corregir el tipo del bulto %s", barcode);
        savedState.set(KEY_TYPE_TARGET, barcode);
    }

    /** Tipo devuelto por el selector: corrige el bulto pedido o, si no se pidió ninguno, fija el tipo. */
    @MainThread
    public void onBultoTypePicked(@NonNull BultoType type) {
        String target = savedState.get(KEY_TYPE_TARGET);
        savedState.remove(KEY_TYPE_TARGET);
        if (target != null) {
            changeReadingType(target, type);
        } else {
            onBultoTypeFixed(type);
        }
    }

    /** Fija o sustituye el tipo que se asocia a los bultos que se lean a partir de ahora. */
    @MainThread
    public void onBultoTypeFixed(@NonNull BultoType type) {
        if (state.getValue() instanceof DirectLoadState.Reading current) {
            Timber.i("[ACCION] Tipo de bulto fijado: %s", type.code());
            savedState.set(KEY_FIXED_TYPE, BultoTypePicker.toBundle(type));
            state.setValue(new DirectLoadState.Reading(current.seal(), type, current.lastBarcode(),
                    current.readings(), current.feedback()));
        }
    }

    /** Corrige el tipo con el que se leyó un bulto; el resto de lecturas y su orden no cambian. */
    private void changeReadingType(@NonNull String barcode, @NonNull BultoType type) {
        if (!(state.getValue() instanceof DirectLoadState.Reading current)) {
            return;
        }
        int index = indexOf(current.readings(), barcode);
        if (index < 0) {
            Timber.w("[ACCION] El bulto %s ya no está en la carga: no se corrige su tipo", barcode);
            return;
        }
        if (type.equals(current.readings().get(index).type())) {
            return;
        }
        Timber.i("[ACCION] Bulto %s corregido al tipo %s", barcode, type.code());
        List<DirectLoadReading> readings = new ArrayList<>(current.readings());
        readings.set(index, new DirectLoadReading(barcode, type));
        saveReadings(readings);
        state.setValue(new DirectLoadState.Reading(current.seal(), current.fixedType(), current.lastBarcode(),
                readings, new DirectLoadState.Feedback.TypeChanged(barcode, type)));
    }

    private static int indexOf(@NonNull List<DirectLoadReading> readings, @NonNull String barcode) {
        for (int i = 0; i < readings.size(); i++) {
            if (readings.get(i).barcode().equals(barcode)) {
                return i;
            }
        }
        return -1;
    }

    /** Los bultos que se lean a partir de ahora no llevan tipo; los ya leídos lo conservan. */
    @MainThread
    public void onBultoTypeCleared() {
        if (state.getValue() instanceof DirectLoadState.Reading current && current.fixedType() != null) {
            Timber.i("[ACCION] Se quita el tipo de bulto fijado");
            savedState.remove(KEY_FIXED_TYPE);
            state.setValue(new DirectLoadState.Reading(current.seal(), null, current.lastBarcode(),
                    current.readings(), current.feedback()));
        }
    }

    private void saveReadings(@NonNull List<DirectLoadReading> readings) {
        ArrayList<Bundle> bundles = new ArrayList<>(readings.size());
        for (DirectLoadReading reading : readings) {
            Bundle bundle = new Bundle();
            bundle.putString(KEY_READING_BARCODE, reading.barcode());
            if (reading.type() != null) {
                bundle.putBundle(KEY_READING_TYPE, BultoTypePicker.toBundle(reading.type()));
            }
            bundles.add(bundle);
        }
        savedState.set(KEY_READINGS, bundles);
    }

    @NonNull
    private List<DirectLoadReading> restoreReadings() {
        ArrayList<Bundle> bundles = savedState.get(KEY_READINGS);
        if (bundles == null) {
            return List.of();
        }
        List<DirectLoadReading> readings = new ArrayList<>(bundles.size());
        for (Bundle bundle : bundles) {
            String barcode = bundle.getString(KEY_READING_BARCODE);
            if (barcode != null) {
                readings.add(new DirectLoadReading(barcode,
                        BultoTypePicker.fromBundle(bundle.getBundle(KEY_READING_TYPE))));
            }
        }
        return readings;
    }
}
