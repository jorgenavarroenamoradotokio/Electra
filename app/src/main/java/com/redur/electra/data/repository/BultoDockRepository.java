package com.redur.electra.data.repository;

import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.data.model.bulto.BultoDock;

import java.util.Optional;

import javax.inject.Inject;

/**
 * Muelle de salida que corresponde a un bulto leído (INT_MST_ALM_MUELLES). Provisional: hasta que
 * el backend publique el servicio de muelles todo bulto sale por el mismo muelle; la firma ya es
 * asíncrona para que sustituirlo no afecte a quien lo usa. Un resultado vacío indica que el bulto
 * no tiene muelle asignado. El callback llega en el hilo principal y no se invoca si la operación
 * se cancela.
 */
public class BultoDockRepository {

    private static final BultoDock PROVISIONAL_DOCK = new BultoDock("Z14", "ZAR", "50237", "España");

    @Inject
    public BultoDockRepository() {
    }

    @NonNull
    public Cancellable getDock(@NonNull String barcode, @NonNull ResultCallback<Optional<BultoDock>> callback) {
        callback.onSuccess(Optional.of(PROVISIONAL_DOCK));
        return () -> { };
    }
}
