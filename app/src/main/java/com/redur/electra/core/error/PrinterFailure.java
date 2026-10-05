package com.redur.electra.core.error;

/** Por qué no ha llegado la etiqueta a la impresora. */
public enum PrinterFailure {

    /** No se pudo conectar: apagada, fuera de alcance, ocupada o sin emparejar. */
    CONNECTION,

    /** Conectada, pero la conexión se cortó al enviar la etiqueta. */
    SEND,

    /** Se ha perdido el permiso de Bluetooth o se ha apagado durante la impresión. */
    BLUETOOTH_UNAVAILABLE
}
