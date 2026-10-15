package com.redur.electra.ui.carga.directa;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.bulto.BultoType;

import java.util.Objects;

/**
 * Bulto leído en la carga con precinto.
 *
 * @param type tipo fijado en el momento de la lectura, o null si no había ninguno.
 */
public record DirectLoadReading(@NonNull String barcode, @Nullable BultoType type) {

    public DirectLoadReading {
        Objects.requireNonNull(barcode, "barcode");
    }
}
