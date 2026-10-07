package com.redur.electra.ui.bulto;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.data.model.bulto.BultoMeasures;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Argumentos de la pantalla de peso y medidas: desde dónde se abre y, si el origen lo usa, el peso
 * y medidas del bulto de referencia (el actual para el botón KG, el anterior para "Peso/Volumen
 * anterior"). Abierta sin argumentos propios, como desde el menú lateral, equivale al botón KG de
 * un bulto sin datos.
 */
public record BultoWeightArgs(@NonNull BultoWeightOrigin origin, @Nullable BultoMeasures reference) {

    private static final String KEY_ORIGIN = "bultoWeight.origin";
    private static final String KEY_WEIGHT = "bultoWeight.weight";
    private static final String KEY_HEIGHT = "bultoWeight.height";
    private static final String KEY_WIDTH = "bultoWeight.width";
    private static final String KEY_DEPTH = "bultoWeight.depth";
    private static final String KEY_VOLUME = "bultoWeight.volume";

    public BultoWeightArgs {
        Objects.requireNonNull(origin, "origin");
    }

    /** Añade estos argumentos a los del destino (p. ej. los de {@code MenuArgs}). */
    @NonNull
    public Bundle addTo(@NonNull Bundle bundle) {
        bundle.putString(KEY_ORIGIN, origin.name());
        if (reference != null) {
            bundle.putString(KEY_WEIGHT, reference.weightKg().toPlainString());
            if (reference.hasMeasures()) {
                bundle.putInt(KEY_HEIGHT, Objects.requireNonNull(reference.heightCm()));
                bundle.putInt(KEY_WIDTH, Objects.requireNonNull(reference.widthCm()));
                bundle.putInt(KEY_DEPTH, Objects.requireNonNull(reference.depthCm()));
                bundle.putString(KEY_VOLUME, Objects.requireNonNull(reference.volumeM3()).toPlainString());
            }
        }
        return bundle;
    }

    /** Lee los argumentos que el Fragment recibió; los que faltan toman su valor por defecto. */
    @NonNull
    public static BultoWeightArgs from(@NonNull SavedStateHandle handle) {
        String originName = handle.get(KEY_ORIGIN);
        BultoWeightOrigin origin = originName != null
                ? BultoWeightOrigin.valueOf(originName)
                : BultoWeightOrigin.KG_BUTTON;

        String weight = handle.get(KEY_WEIGHT);
        if (weight == null) {
            return new BultoWeightArgs(origin, null);
        }
        String volume = handle.get(KEY_VOLUME);
        BultoMeasures reference = volume == null
                ? BultoMeasures.weightOnly(new BigDecimal(weight))
                : new BultoMeasures(new BigDecimal(weight),
                handle.get(KEY_HEIGHT), handle.get(KEY_WIDTH), handle.get(KEY_DEPTH),
                new BigDecimal(volume));
        return new BultoWeightArgs(origin, reference);
    }
}
