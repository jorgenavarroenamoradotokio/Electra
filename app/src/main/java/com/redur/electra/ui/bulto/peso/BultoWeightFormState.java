package com.redur.electra.ui.bulto.peso;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Errores del formulario de peso y medidas. Un error nulo indica que el campo es válido. Los de
 * las medidas se muestran en el volumen, que es lo que se valida de ellas.
 */
public record BultoWeightFormState(
        @Nullable @StringRes Integer weightError,
        @Nullable @StringRes Integer volumeError
) {

    public static final BultoWeightFormState EMPTY = new BultoWeightFormState(null, null);

    public boolean isValid() {
        return weightError == null && volumeError == null;
    }
}
