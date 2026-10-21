package com.redur.electra.ui.carga.camion;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.carga.DeliveryInstruction;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.data.model.carga.ExpeditionAlert;
import com.redur.electra.data.model.carga.PackageKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Etiquetas que se ven en una expedición, en el orden del prototipo: vuelta, tipo, instrucciones
 * de entrega e información. Solo aparece lo que la distingue: una vuelta 1 o una expedición de
 * paquetes son lo habitual y no se rotulan.
 */
final class TruckLoadTags {

    private static final int FIRST_TRIP = 1;

    private TruckLoadTags() {
    }

    @NonNull
    static List<TruckLoadTag> of(@NonNull Expedition expedition) {
        List<TruckLoadTag> tags = new ArrayList<>();
        if (expedition.trip() > FIRST_TRIP) {
            tags.add(new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_trip, List.of(expedition.trip())),
                    R.drawable.ic_local_shipping_24, false));
        }
        if (expedition.packageKind() != PackageKind.PACKAGE) {
            tags.add(plain(packageKindLabel(expedition.packageKind())));
        }
        // Se recorren los enums para que el orden no dependa del Set
        for (DeliveryInstruction instruction : DeliveryInstruction.values()) {
            if (expedition.instructions().contains(instruction)) {
                tags.add(plain(instructionLabel(instruction)));
            }
        }
        for (ExpeditionAlert alert : ExpeditionAlert.values()) {
            if (expedition.alerts().contains(alert)) {
                tags.add(new TruckLoadTag(new UiText.Res(alertLabel(alert)), R.drawable.ic_report_problem_24, true));
            }
        }
        if (expedition.deliveryAttempts() > 0) {
            tags.add(new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_attempts,
                    List.of(expedition.deliveryAttempts())), 0, false));
        }
        if (expedition.pharma()) {
            tags.add(plain(R.string.truck_load_tag_pharma));
        }
        if (expedition.product() != null && !expedition.product().isBlank()) {
            tags.add(new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_product,
                    List.of(expedition.product())), 0, false));
        }
        return tags;
    }

    @NonNull
    private static TruckLoadTag plain(@StringRes int label) {
        return new TruckLoadTag(new UiText.Res(label), 0, false);
    }

    @StringRes
    private static int packageKindLabel(@NonNull PackageKind kind) {
        return switch (kind) {
            case PACKAGE -> R.string.truck_load_tag_package;
            case IRREGULAR -> R.string.truck_load_tag_irregular;
            case PALLET -> R.string.truck_load_tag_pallet;
        };
    }

    @StringRes
    private static int instructionLabel(@NonNull DeliveryInstruction instruction) {
        return switch (instruction) {
            case CASH_ON_DELIVERY -> R.string.truck_load_tag_cash_on_delivery;
            case PICKUP_POINT -> R.string.truck_load_tag_pickup_point;
            case COMPLETE_DELIVERY -> R.string.truck_load_tag_complete_delivery;
            case TIME_WINDOW -> R.string.truck_load_tag_time_window;
        };
    }

    @StringRes
    private static int alertLabel(@NonNull ExpeditionAlert alert) {
        return switch (alert) {
            case ALERT -> R.string.truck_load_tag_alert;
            case INCIDENCE -> R.string.truck_load_tag_incidence;
            case URGENT -> R.string.truck_load_tag_urgent;
        };
    }
}
