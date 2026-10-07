package com.redur.electra.data.model.bulto;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Muelle de salida de un bulto (INT_MST_ALM_MUELLES) y el destino por el que se le asigna.
 *
 * @param code       código del muelle (p. ej. "Z14").
 * @param place      plaza de destino (p. ej. "ZAR").
 * @param postalCode código postal de destino.
 * @param country    país de destino, ya traducido.
 */
public record BultoDock(@NonNull String code, @NonNull String place, @NonNull String postalCode,
                        @NonNull String country) {

    public BultoDock {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(country, "country");
    }
}
