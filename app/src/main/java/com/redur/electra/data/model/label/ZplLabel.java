package com.redur.electra.data.model.label;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Etiqueta lista para previsualizar: su código ZPL (lo que se enviará a la impresora) y cada
 * página como PNG. Siempre tiene al menos una página.
 */
public record ZplLabel(@NonNull String zpl, @NonNull List<byte[]> pages) {

    public ZplLabel {
        Objects.requireNonNull(zpl, "zpl");
        pages = List.copyOf(pages);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("La etiqueta no tiene páginas");
        }
    }
}
