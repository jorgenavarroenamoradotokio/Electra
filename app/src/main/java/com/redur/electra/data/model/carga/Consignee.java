package com.redur.electra.data.model.carga;

import androidx.annotation.NonNull;

import java.util.Objects;

/** Destinatario (consignatario) de una expedición. */
public record Consignee(@NonNull String name,
                        @NonNull String address,
                        @NonNull String postalCode,
                        @NonNull String town) {

    public Consignee {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(town, "town");
    }
}
