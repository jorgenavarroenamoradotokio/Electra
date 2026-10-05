package com.redur.electra.data.bluetooth;

import androidx.annotation.NonNull;

import java.io.IOException;

/** No se pudo establecer la conexión con la impresora (a diferencia de un corte al enviar). */
public class PrinterConnectionException extends IOException {

    public PrinterConnectionException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
