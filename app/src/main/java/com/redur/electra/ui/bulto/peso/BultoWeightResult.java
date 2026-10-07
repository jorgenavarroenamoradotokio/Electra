package com.redur.electra.ui.bulto.peso;

import androidx.annotation.NonNull;

import com.redur.electra.data.model.bulto.BultoMeasures;

import java.util.Objects;

/** Cómo termina la pantalla de peso y medidas y qué debe hacer la lectura del bulto con ello. */
public sealed interface BultoWeightResult permits BultoWeightResult.Saved, BultoWeightResult.Canceled {

    /**
     * Datos validados para grabar en el escaneo.
     *
     * @param fullExpedition si el escaneo se graba con EXPEDICION_COMPLETA = 'S'
     */
    record Saved(@NonNull BultoMeasures measures, boolean fullExpedition) implements BultoWeightResult {
        public Saved {
            Objects.requireNonNull(measures, "measures");
        }
    }

    record Canceled(@NonNull BultoWeightOrigin.CancelEffect effect) implements BultoWeightResult {
        public Canceled {
            Objects.requireNonNull(effect, "effect");
        }
    }
}
