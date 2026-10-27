package com.redur.electra.data.model.bulto;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Incidencia que se puede grabar en un bulto.
 *
 * @param codigoIncidencia     código de la incidencia (p. ej. "A21").
 * @param descripcion          descripción en el idioma del usuario; vacía si el backend no la envía.
 * @param photoRequired        si para grabarla hay que hacer una foto del bulto.
 * @param observationsRequired si para grabarla hay que escribir observaciones.
 */
public record BultoIncidencia(
        @NonNull String codigoIncidencia,
        @NonNull String descripcion,
        boolean photoRequired,
        boolean observationsRequired
) {

    /** Longitud máxima de las observaciones que se graban con la incidencia. */
    public static final int OBSERVATIONS_MAX_LENGTH = 40;

    public BultoIncidencia {
        Objects.requireNonNull(codigoIncidencia, "codigoIncidencia");
        Objects.requireNonNull(descripcion, "descripcion");
    }

}
