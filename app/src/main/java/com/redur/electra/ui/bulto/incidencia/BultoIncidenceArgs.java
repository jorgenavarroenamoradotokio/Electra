package com.redur.electra.ui.bulto.incidencia;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.data.model.bulto.OperationType;

import java.util.Objects;

/**
 * Argumentos de la pantalla de incidencias: el tipo de operación, que decide la lista, y el último
 * bulto leído. Abierta sin argumentos propios, como desde el menú lateral, equivale a una recogida
 * sin bulto leído.
 */
public record BultoIncidenceArgs(@NonNull OperationType operationType, @Nullable String barcode) {

    private static final String KEY_OPERATION_TYPE = "bultoIncidence.operationType";
    private static final String KEY_BARCODE = "bultoIncidence.barcode";

    public BultoIncidenceArgs {
        Objects.requireNonNull(operationType, "operationType");
    }

    /** Añade estos argumentos a los del destino (p. ej. los de {@code MenuArgs}). */
    @NonNull
    public Bundle addTo(@NonNull Bundle bundle) {
        bundle.putString(KEY_OPERATION_TYPE, operationType.name());
        bundle.putString(KEY_BARCODE, barcode);
        return bundle;
    }

    /** Lee los argumentos que el Fragment recibió; los que faltan toman su valor por defecto. */
    @NonNull
    public static BultoIncidenceArgs from(@NonNull SavedStateHandle handle) {
        String operationName = handle.get(KEY_OPERATION_TYPE);
        OperationType operationType = operationName != null
                ? OperationType.valueOf(operationName)
                : OperationType.RECOGIDAS;
        return new BultoIncidenceArgs(operationType, handle.get(KEY_BARCODE));
    }
}
