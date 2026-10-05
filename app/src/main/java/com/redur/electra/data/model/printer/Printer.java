package com.redur.electra.data.model.printer;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Dispositivo Bluetooth al que se puede enviar una etiqueta. {@code likelyPrinter} es true si se
 * anuncia como impresora; hay impresoras que no lo hacen, por eso no se descarta el resto.
 */
public record Printer(@NonNull String name, @NonNull String address, boolean paired, boolean likelyPrinter) {

    public Printer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
    }
}
