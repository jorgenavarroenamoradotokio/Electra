package com.redur.electra.data.model.carga;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Expedición asignada a la carga del camión.
 *
 * @param number           nº de expedición; identifica la expedición en la carga.
 * @param deliveryNote     nº de albarán del cliente.
 * @param sender           cliente remitente.
 * @param documented       bultos documentados.
 * @param atDestination    bultos en la plaza de destino.
 * @param scanned          bultos escaneados por el repartidor al cargar.
 * @param zone             área o zona de carga.
 * @param loadOrder        posición en el orden de carga, de 1 a {@code loadTotal}.
 * @param trip             nº de carga o vuelta; mayor que 1 en una recarga.
 * @param deliveryAttempts intentos de entrega ya realizados.
 * @param product          letra del producto contratado, o null si no tiene.
 * @param chargeableWeight peso tasado de la expedición, en kg.
 */
public record Expedition(@NonNull String number,
                         @NonNull String deliveryNote,
                         @NonNull String sender,
                         @NonNull Consignee consignee,
                         int documented,
                         int atDestination,
                         int scanned,
                         @NonNull String zone,
                         int loadOrder,
                         int loadTotal,
                         int trip,
                         @NonNull PackageKind packageKind,
                         @NonNull Set<DeliveryInstruction> instructions,
                         @NonNull Set<ExpeditionAlert> alerts,
                         int deliveryAttempts,
                         boolean pharma,
                         @Nullable String product,
                         double chargeableWeight) {

    public Expedition {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(deliveryNote, "deliveryNote");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(consignee, "consignee");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(packageKind, "packageKind");
        instructions = Set.copyOf(instructions);
        alerts = Set.copyOf(alerts);
    }

    /**
     * Bultos documentados que aún no se han escaneado; negativo si se han escaneado de más.
     * La expedición está completa, sin diferencias, cuando es 0.
     */
    public int pendingBultos() {
        return documented - scanned;
    }

    public boolean isComplete() {
        return pendingBultos() == 0;
    }
}
