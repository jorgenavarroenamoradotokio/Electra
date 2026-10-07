package com.redur.electra.data.model.bulto;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Incidencia que se puede grabar en un bulto.
 *
 * @param code                 código de la incidencia (p. ej. "A21").
 * @param photoRequired        si para grabarla hay que hacer una foto del bulto (FOTO_SON = 'S').
 * @param observationsRequired si para grabarla hay que escribir observaciones.
 */
public record BultoIncidence(@NonNull String code, boolean photoRequired, boolean observationsRequired) {

    /** Longitud máxima de las observaciones que se graban con la incidencia. */
    public static final int OBSERVATIONS_MAX_LENGTH = 40;

    public BultoIncidence {
        Objects.requireNonNull(code, "code");
    }
}
