package com.redur.electra.data.repository;

import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.data.model.bulto.BultoIncidence;
import com.redur.electra.data.model.bulto.OperationType;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Incidencias que se pueden grabar en un bulto según el tipo de operación, en el orden en que se
 * muestran. Provisional: hasta que el backend publique el servicio de incidencias se sirve un
 * catálogo fijo; la firma ya es asíncrona para que sustituirlo no afecte a quien lo usa. El
 * callback llega en el hilo principal y no se invoca si la operación se cancela.
 */
public class BultoIncidenceRepository {

    private static final Map<OperationType, List<BultoIncidence>> PROVISIONAL_CATALOG = Map.of(
            OperationType.RECOGIDAS, List.of(
                    new BultoIncidence("A16", false, false),
                    new BultoIncidence("A21", true, false),
                    new BultoIncidence("A23", true, false),
                    new BultoIncidence("A40", false, false),
                    new BultoIncidence("A41", false, false),
                    new BultoIncidence("I11", false, true)));

    @Inject
    public BultoIncidenceRepository() {
    }

    @NonNull
    public Cancellable getIncidences(@NonNull OperationType operationType,
                                     @NonNull ResultCallback<List<BultoIncidence>> callback) {
        callback.onSuccess(PROVISIONAL_CATALOG.getOrDefault(operationType, List.of()));
        return () -> { };
    }
}
