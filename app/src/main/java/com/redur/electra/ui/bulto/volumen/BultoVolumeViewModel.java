package com.redur.electra.ui.bulto.volumen;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.util.Validations;
import com.redur.electra.data.model.bulto.BultoMeasures;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Modo Volumen (sin peso) de la lectura: por cada bulto se lee su CB y sus tres medidas, se
 * calcula el volumen a medida que llegan y, al aceptar, se valida la lectura para grabarla. Tras
 * aceptar o cancelar la pantalla queda lista para el bulto siguiente.
 */
@HiltViewModel
public class BultoVolumeViewModel extends ViewModel {

    private static final String KEY_VOLUME_MODE = "bultoVolume.volumeMode";

    /** Hasta 3 dígitos sin separadores. */
    private static final Pattern MEASURE_PATTERN = Pattern.compile("\\d{1,3}");

    private final MutableLiveData<BultoVolumeFormState> formState =
            new MutableLiveData<>(BultoVolumeFormState.EMPTY);
    private final MutableLiveData<BigDecimal> volume = new MutableLiveData<>();
    /** Check "Volumen (sin peso)": se abre marcado y sobrevive a que el sistema mate el proceso. */
    private final MutableLiveData<Boolean> volumeMode;

    @Inject
    public BultoVolumeViewModel(SavedStateHandle savedState) {
        this.volumeMode = savedState.getLiveData(KEY_VOLUME_MODE, true);
    }

    public LiveData<BultoVolumeFormState> getFormState() {
        return formState;
    }

    /** Volumen calculado de las medidas leídas; null mientras falte alguna. */
    public LiveData<BigDecimal> getVolume() {
        return volume;
    }

    public LiveData<Boolean> getVolumeMode() {
        return volumeMode;
    }

    public boolean isVolumeMode() {
        return Boolean.TRUE.equals(volumeMode.getValue());
    }

    @MainThread
    public void onVolumeModeChanged(boolean enabled) {
        if (enabled != isVolumeMode()) {
            Timber.i("[ACCION] Modo volumen (sin peso): %b", enabled);
            volumeMode.setValue(enabled);
        }
    }

    @MainThread
    public void onBarcodeChanged() {
        BultoVolumeFormState current = currentState();
        if (current.barcodeError() != null) {
            formState.setValue(new BultoVolumeFormState(null, current.volumeError()));
        }
    }

    /** Ha cambiado una medida: se recalcula el volumen y se retira su error. */
    @MainThread
    public void onMeasuresChanged(@Nullable String height, @Nullable String width, @Nullable String depth) {
        updateVolume(height, width, depth);
        BultoVolumeFormState current = currentState();
        if (current.volumeError() != null) {
            formState.setValue(new BultoVolumeFormState(current.barcodeError(), null));
        }
    }

    /**
     * Medidas restauradas por el sistema (p. ej. tras matar el proceso): el volumen debe reflejar
     * lo que muestran los campos, pero no es una corrección y los errores se mantienen.
     */
    @MainThread
    public void onMeasuresRestored(@Nullable String height, @Nullable String width, @Nullable String depth) {
        updateVolume(height, width, depth);
    }

    /**
     * Valida la lectura. Si es correcta devuelve lo que se graba; si no, publica los errores y
     * devuelve null. Con el modo desactivado no hay nada que grabar.
     */
    @MainThread
    @Nullable
    public BultoVolumeReading onConfirmClicked(@Nullable String barcode, @Nullable String height,
                                               @Nullable String width, @Nullable String depth) {
        if (!isVolumeMode()) {
            Timber.i("[ACCION] Aceptar lectura con el modo volumen desactivado: no se graba nada");
            return null;
        }
        Integer barcodeError = Validations.isBlank(barcode) ? R.string.bulto_volume_error_barcode : null;

        Integer h = parseMeasure(height);
        Integer w = parseMeasure(width);
        Integer d = parseMeasure(depth);
        Integer volumeError = null;
        BigDecimal volumeM3 = null;
        if (h == null || w == null || d == null) {
            volumeError = R.string.bulto_volume_error_measures;
        } else {
            volumeM3 = BultoMeasures.volumeOf(h, w, d);
            if (!BultoMeasures.isValidVolume(volumeM3)) {
                volumeError = R.string.bulto_volume_error_volume;
            }
        }

        BultoVolumeFormState validated = new BultoVolumeFormState(barcodeError, volumeError);
        formState.setValue(validated);
        if (!validated.isValid()) {
            Timber.i("[ACCION] Lectura de volumen rechazada (CB válido: %b, medidas válidas: %b)",
                    barcodeError == null, volumeError == null);
            return null;
        }
        String code = Objects.requireNonNull(barcode).trim();
        Timber.i("[ACCION] Lectura de volumen aceptada: bulto %s, %dx%dx%d cm, %s m3", code, h, w, d, volumeM3);
        return new BultoVolumeReading(code, h, w, d, Objects.requireNonNull(volumeM3));
    }

    /** Descarta la lectura en curso: la pantalla vuelve a esperar el CB de un bulto. */
    @MainThread
    public void onCancelClicked() {
        Timber.i("[ACCION] Lectura de volumen cancelada");
        formState.setValue(BultoVolumeFormState.EMPTY);
        volume.setValue(null);
    }

    private void updateVolume(@Nullable String height, @Nullable String width, @Nullable String depth) {
        Integer h = parseMeasure(height);
        Integer w = parseMeasure(width);
        Integer d = parseMeasure(depth);
        BigDecimal calculated = h != null && w != null && d != null ? BultoMeasures.volumeOf(h, w, d) : null;
        if (!Objects.equals(calculated, volume.getValue())) {
            volume.setValue(calculated);
        }
    }

    @Nullable
    private static Integer parseMeasure(@Nullable String value) {
        if (value == null || !MEASURE_PATTERN.matcher(value.trim()).matches()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    @NonNull
    private BultoVolumeFormState currentState() {
        BultoVolumeFormState state = formState.getValue();
        return state != null ? state : BultoVolumeFormState.EMPTY;
    }
}
