package com.redur.electra.ui.bulto.incidencia;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.bulto.BultoIncidence;

import java.util.Objects;

/**
 * Incidencia validada, lista para grabar: el bulto (si se ha leído alguno), la incidencia, sus
 * observaciones (vacías si no las exige) y la foto enviada (si se ha hecho).
 */
public record BultoIncidenceEntry(
        @Nullable String barcode,
        @NonNull BultoIncidence incidence,
        @NonNull String observations,
        @Nullable String photoUri
) {

    public BultoIncidenceEntry {
        Objects.requireNonNull(incidence, "incidence");
        Objects.requireNonNull(observations, "observations");
    }
}
