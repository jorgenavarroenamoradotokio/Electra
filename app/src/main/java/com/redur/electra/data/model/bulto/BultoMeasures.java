package com.redur.electra.data.model.bulto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Peso y medidas de un bulto tal como se graban en el escaneo: peso en kg con 1 decimal y, si se
 * han medido, alto, ancho y profundo en cm y el volumen en m³ con 2 decimales. Las medidas van
 * siempre las tres o ninguna: se puede grabar solo el peso.
 */
public record BultoMeasures(
        @NonNull BigDecimal weightKg,
        @Nullable Integer heightCm,
        @Nullable Integer widthCm,
        @Nullable Integer depthCm,
        @Nullable BigDecimal volumeM3
) {

    public static final int WEIGHT_SCALE = 1;
    public static final int VOLUME_SCALE = 2;
    /** Límite exclusivo: el volumen debe ser mayor que 0 y menor que 10 m³. */
    public static final BigDecimal MAX_VOLUME_M3 = BigDecimal.TEN;

    private static final BigDecimal CM3_PER_M3 = BigDecimal.valueOf(1_000_000);

    public BultoMeasures {
        Objects.requireNonNull(weightKg, "weightKg");
        boolean anyMeasure = heightCm != null || widthCm != null || depthCm != null || volumeM3 != null;
        boolean allMeasures = heightCm != null && widthCm != null && depthCm != null && volumeM3 != null;
        if (anyMeasure && !allMeasures) {
            throw new IllegalArgumentException("Las medidas y el volumen van todos o ninguno");
        }
    }

    /** Solo peso, sin medidas. */
    @NonNull
    public static BultoMeasures weightOnly(@NonNull BigDecimal weightKg) {
        return new BultoMeasures(weightKg, null, null, null, null);
    }

    public boolean hasMeasures() {
        return volumeM3 != null;
    }

    /** Volumen en m³ de unas medidas en cm, redondeado a los decimales con los que se graba. */
    @NonNull
    public static BigDecimal volumeOf(int heightCm, int widthCm, int depthCm) {
        BigDecimal cm3 = BigDecimal.valueOf((long) heightCm * widthCm * depthCm);
        return cm3.divide(CM3_PER_M3, VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    /** Si el volumen se puede grabar: mayor que 0 y menor que {@link #MAX_VOLUME_M3}. */
    public static boolean isValidVolume(@NonNull BigDecimal volumeM3) {
        return volumeM3.signum() > 0 && volumeM3.compareTo(MAX_VOLUME_M3) < 0;
    }
}
