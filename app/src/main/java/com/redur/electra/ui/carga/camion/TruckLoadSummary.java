package com.redur.electra.ui.carga.camion;

import androidx.annotation.NonNull;

import com.redur.electra.data.model.carga.Consignee;
import com.redur.electra.data.model.carga.Expedition;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resumen de toda la carga, se vea filtrada o no.
 *
 * @param expeditions      expediciones asignadas.
 * @param stops            paradas: direcciones de entrega distintas; varias expediciones al mismo
 *                         destinatario son una sola parada.
 * @param bultos           bultos documentados.
 * @param chargeableWeight peso tasado, en kg.
 */
public record TruckLoadSummary(int expeditions, int stops, int bultos, double chargeableWeight) {

    @NonNull
    static TruckLoadSummary of(@NonNull List<Expedition> load) {
        Set<String> stops = new HashSet<>();
        int bultos = 0;
        double weight = 0;
        for (Expedition expedition : load) {
            stops.add(stopKey(expedition.consignee()));
            bultos += expedition.documented();
            weight += expedition.chargeableWeight();
        }
        return new TruckLoadSummary(load.size(), stops.size(), bultos, weight);
    }

    /** Misma dirección y CP, aunque cambien mayúsculas o espacios, es la misma parada. */
    @NonNull
    private static String stopKey(@NonNull Consignee consignee) {
        return consignee.postalCode().trim() + '|'
                + consignee.address().trim().replaceAll("\s+", " ").toLowerCase(Locale.ROOT);
    }
}
