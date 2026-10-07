package com.redur.electra.ui.bulto.incidencia;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/** Errores del formulario de incidencia. Un error nulo indica que esa parte es válida. */
public record BultoIncidenceFormState(
        @Nullable @StringRes Integer observationsError,
        @Nullable @StringRes Integer photoError
) {

    public static final BultoIncidenceFormState EMPTY = new BultoIncidenceFormState(null, null);

    public boolean isValid() {
        return observationsError == null && photoError == null;
    }
}
