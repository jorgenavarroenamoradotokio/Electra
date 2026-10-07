package com.redur.electra.ui.bulto.volumen;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Errores del formulario de volumen. Un error nulo indica que el campo es válido. Los de las
 * medidas se muestran en el volumen, que es lo que se valida de ellas.
 */
public record BultoVolumeFormState(
        @Nullable @StringRes Integer barcodeError,
        @Nullable @StringRes Integer volumeError
) {

    public static final BultoVolumeFormState EMPTY = new BultoVolumeFormState(null, null);

    public boolean isValid() {
        return barcodeError == null && volumeError == null;
    }
}
