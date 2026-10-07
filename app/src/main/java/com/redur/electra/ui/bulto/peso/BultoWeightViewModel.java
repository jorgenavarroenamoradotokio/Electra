package com.redur.electra.ui.bulto.peso;

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
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Peso y medidas del bulto. Calcula el volumen a medida que se teclean las medidas, valida al
 * aceptar y decide, según el origen, qué ocurre con la lectura al aceptar o cancelar.
 */
@HiltViewModel
public class BultoWeightViewModel extends ViewModel {

    /** Límites exclusivos: el peso debe ser mayor que 0 y menor que 100.000 kg. */
    static final BigDecimal MAX_WEIGHT_KG = BigDecimal.valueOf(100_000);

    /** Hasta 3 dígitos sin separadores. */
    private static final Pattern MEASURE_PATTERN = Pattern.compile("\\d{1,3}");
    /** Enteros con decimales opcionales; se admite coma o punto como separador. */
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("\\d+([.,]\\d*)?|[.,]\\d+");

    private final MutableLiveData<BultoWeightFormState> formState =
            new MutableLiveData<>(BultoWeightFormState.EMPTY);
    private final MutableLiveData<BigDecimal> volume = new MutableLiveData<>();

    private final BultoWeightOrigin origin;
    @Nullable
    private final BultoMeasures initialMeasures;

    @Inject
    public BultoWeightViewModel(SavedStateHandle savedState) {
        BultoWeightArgs args = BultoWeightArgs.from(savedState);
        this.origin = args.origin();
        this.initialMeasures = initialMeasuresFor(args);
        if (initialMeasures != null) {
            volume.setValue(initialMeasures.volumeM3());
        }
    }

    public LiveData<BultoWeightFormState> getFormState() {
        return formState;
    }

    /** Volumen calculado de las medidas tecleadas; null mientras falte alguna. */
    public LiveData<BigDecimal> getVolume() {
        return volume;
    }

    @NonNull
    public BultoWeightOrigin getOrigin() {
        return origin;
    }

    /** Datos con los que se rellena la pantalla al abrirse; null si se abre vacía. */
    @Nullable
    public BultoMeasures getInitialMeasures() {
        return initialMeasures;
    }

    @MainThread
    public void onWeightChanged() {
        BultoWeightFormState current = currentState();
        if (current.weightError() != null) {
            formState.setValue(new BultoWeightFormState(null, current.volumeError()));
        }
    }

    /** El usuario ha cambiado una medida: se recalcula el volumen y se retira su error. */
    @MainThread
    public void onMeasuresChanged(@Nullable String height, @Nullable String width, @Nullable String depth) {
        updateVolume(height, width, depth);
        BultoWeightFormState current = currentState();
        if (current.volumeError() != null) {
            formState.setValue(new BultoWeightFormState(current.weightError(), null));
        }
    }

    /**
     * Valida lo tecleado. Si es correcto devuelve los datos a grabar, redondeados a los decimales
     * con los que se guardan; si no, publica los errores y devuelve null.
     */
    @MainThread
    @Nullable
    public BultoWeightResult.Saved onConfirmClicked(@Nullable String weight, @Nullable String height,
                                                    @Nullable String width, @Nullable String depth) {
        BigDecimal weightKg = parseWeight(weight);
        Integer weightError = isValidWeight(weightKg) ? null : R.string.bulto_weight_error_weight;

        boolean anyMeasure = !Validations.isBlank(height) || !Validations.isBlank(width)
                || !Validations.isBlank(depth);
        Integer h = parseMeasure(height);
        Integer w = parseMeasure(width);
        Integer d = parseMeasure(depth);
        boolean allMeasures = h != null && w != null && d != null;

        Integer volumeError = null;
        BigDecimal volumeM3 = null;
        if (anyMeasure && !allMeasures) {
            volumeError = R.string.bulto_weight_error_measures;
        } else if (allMeasures) {
            volumeM3 = BultoMeasures.volumeOf(h, w, d);
            if (!BultoMeasures.isValidVolume(volumeM3)) {
                volumeError = R.string.bulto_weight_error_volume;
            }
        }

        BultoWeightFormState validated = new BultoWeightFormState(weightError, volumeError);
        formState.setValue(validated);
        if (!validated.isValid()) {
            return null;
        }

        BultoMeasures measures = allMeasures
                ? new BultoMeasures(Objects.requireNonNull(weightKg), h, w, d, volumeM3)
                : BultoMeasures.weightOnly(Objects.requireNonNull(weightKg));
        return new BultoWeightResult.Saved(measures, origin.marksFullExpedition());
    }

    /**
     * Medidas restauradas por el sistema (p. ej. tras matar el proceso): el volumen debe reflejar
     * lo que muestran los campos, pero no es una corrección y los errores se mantienen.
     */
    @MainThread
    public void onMeasuresRestored(@Nullable String height, @Nullable String width, @Nullable String depth) {
        updateVolume(height, width, depth);
    }

    @NonNull
    public BultoWeightResult.Canceled onCancelClicked() {
        return new BultoWeightResult.Canceled(origin.cancelEffect());
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
    private static BultoMeasures initialMeasuresFor(@NonNull BultoWeightArgs args) {
        return switch (args.origin().initialData()) {
            case EMPTY -> null;
            case CURRENT_BULTO -> args.reference();
            // Sin bulto anterior se abre a cero
            case PREVIOUS_BULTO -> args.reference() != null
                    ? args.reference()
                    : new BultoMeasures(BigDecimal.ZERO.setScale(BultoMeasures.WEIGHT_SCALE),
                    0, 0, 0, BultoMeasures.volumeOf(0, 0, 0));
        };
    }

    /** Peso redondeado a 1 decimal, o null si está vacío o no es un número. */
    @Nullable
    private static BigDecimal parseWeight(@Nullable String value) {
        if (value == null || !WEIGHT_PATTERN.matcher(value.trim()).matches()) {
            return null;
        }
        String normalized = value.trim().replace(',', '.');
        return new BigDecimal(normalized).setScale(BultoMeasures.WEIGHT_SCALE, RoundingMode.HALF_UP);
    }

    @Nullable
    private static Integer parseMeasure(@Nullable String value) {
        if (value == null || !MEASURE_PATTERN.matcher(value.trim()).matches()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    private static boolean isValidWeight(@Nullable BigDecimal weightKg) {
        return weightKg != null
                && weightKg.signum() > 0
                && weightKg.compareTo(MAX_WEIGHT_KG) < 0;
    }

    @NonNull
    private BultoWeightFormState currentState() {
        BultoWeightFormState state = formState.getValue();
        return state != null ? state : BultoWeightFormState.EMPTY;
    }
}
