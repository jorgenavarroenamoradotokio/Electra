package com.redur.electra.data.repository;

import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.data.model.carga.Consignee;
import com.redur.electra.data.model.carga.DeliveryInstruction;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.data.model.carga.ExpeditionAlert;
import com.redur.electra.data.model.carga.PackageKind;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Expediciones asignadas a la carga del camión. Provisional: hasta que el backend publique el
 * servicio de carga se sirve una carga de ejemplo; la firma ya es asíncrona para que sustituirlo
 * no afecte a quien lo usa. El callback llega en el hilo principal y no se invoca si la operación
 * se cancela.
 */
public class TruckLoadRepository {

    private static final int PROVISIONAL_LOAD_TOTAL = 6;

    private static final List<Expedition> PROVISIONAL_LOAD = List.of(
            new Expedition("977991202", "80012345", "Ferrer Internacional",
                    new Consignee("Carmen Meca", "CL Joan Buscallà 1-9", "08190", "S.Cugat del Vallés"),
                    3, 3, 0, "23B", 1, PROVISIONAL_LOAD_TOTAL, 2, PackageKind.PACKAGE,
                    Set.of(DeliveryInstruction.CASH_ON_DELIVERY), Set.of(ExpeditionAlert.ALERT),
                    3, true, "E", 12.5),
            new Expedition("963456111", "45120077", "3M",
                    new Consignee("Juan López", "Calle Centolla", "08001", "Barcelona"),
                    3, 2, 3, "23B", 2, PROVISIONAL_LOAD_TOTAL, 1, PackageKind.PACKAGE,
                    Set.of(), Set.of(), 3, false, "U", 30.0),
            new Expedition("971200548", "70033981", "Laboratorios Esteve",
                    new Consignee("Farmacia Rambla", "Rambla de Catalunya 88", "08008", "Barcelona"),
                    2, 2, 2, "23A", 3, PROVISIONAL_LOAD_TOTAL, 1, PackageKind.PACKAGE,
                    Set.of(DeliveryInstruction.TIME_WINDOW), Set.of(ExpeditionAlert.URGENT),
                    0, true, null, 4.2),
            new Expedition("975118020", "91550024", "Leroy Merlin",
                    new Consignee("Marta Puig", "Av. Diagonal 640", "08017", "Barcelona"),
                    4, 4, 1, "24C", 4, PROVISIONAL_LOAD_TOTAL, 1, PackageKind.PALLET,
                    Set.of(DeliveryInstruction.COMPLETE_DELIVERY), Set.of(), 0, false, null, 410.0),
            new Expedition("968004731", "30007712", "Decathlon",
                    new Consignee("Punto Pack Gràcia", "C/ Verdi 12", "08012", "Barcelona"),
                    1, 1, 1, "24C", 5, PROVISIONAL_LOAD_TOTAL, 2, PackageKind.IRREGULAR,
                    Set.of(DeliveryInstruction.PICKUP_POINT), Set.of(), 1, false, null, 18.0),
            new Expedition("979330415", "80012399", "Ferrer Internacional",
                    new Consignee("Jordi Serra", "Pg. de Gràcia 21", "08007", "Barcelona"),
                    2, 2, 0, "23A", 6, PROVISIONAL_LOAD_TOTAL, 1, PackageKind.PACKAGE,
                    Set.of(), Set.of(ExpeditionAlert.INCIDENCE), 0, false, null, 7.5));

    @Inject
    public TruckLoadRepository() {
    }

    @NonNull
    public Cancellable getExpeditions(@NonNull ResultCallback<List<Expedition>> callback) {
        callback.onSuccess(PROVISIONAL_LOAD);
        return () -> { };
    }
}
