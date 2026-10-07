package com.redur.electra.ui.bulto.volumen;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lectura validada del modo Volumen (sin peso), lista para grabar: el CB del bulto, sus medidas
 * en cm y el volumen en m³ con los decimales con los que se graba.
 */
public record BultoVolumeReading(
        @NonNull String barcode,
        int heightCm,
        int widthCm,
        int depthCm,
        @NonNull BigDecimal volumeM3
) {

    public BultoVolumeReading {
        Objects.requireNonNull(barcode, "barcode");
        Objects.requireNonNull(volumeM3, "volumeM3");
    }
}
