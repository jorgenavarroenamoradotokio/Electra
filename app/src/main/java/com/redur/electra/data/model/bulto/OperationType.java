package com.redur.electra.data.model.bulto;

import androidx.annotation.NonNull;

/** Tipo de operación en que se lee el bulto: decide qué incidencias se le pueden grabar. */
public enum OperationType {

    RECOGIDAS("REC");

    @NonNull
    private final String code;

    OperationType(@NonNull String code) {
        this.code = code;
    }

    /** Código del tipo de operación en el backend. */
    @NonNull
    public String code() {
        return code;
    }
}
