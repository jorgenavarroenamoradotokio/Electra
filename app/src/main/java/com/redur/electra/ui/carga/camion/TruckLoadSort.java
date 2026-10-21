package com.redur.electra.ui.carga.camion;

import androidx.annotation.NonNull;

import com.redur.electra.data.model.carga.Expedition;

import java.text.Collator;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Orden de la lista de expediciones, en el orden en que se ofrecen. Por defecto, el orden de carga.
 * A igualdad de criterio se mantiene el orden de carga, que es como se carga el camión.
 */
public enum TruckLoadSort {

    LOAD_ORDER(Comparator.comparingInt(Expedition::loadOrder)),
    EXPEDITION_NUMBER(byLoadOrderAfter(Comparator.comparing(Expedition::number))),
    DELIVERY_NOTE(byLoadOrderAfter(Comparator.comparing(Expedition::deliveryNote))),
    SENDER(byText(Expedition::sender)),
    CONSIGNEE(byText(expedition -> expedition.consignee().name())),
    POSTAL_CODE(byLoadOrderAfter(Comparator.comparing(expedition -> expedition.consignee().postalCode()))),
    TOWN(byText(expedition -> expedition.consignee().town())),
    ZONE(byLoadOrderAfter(Comparator.comparing(Expedition::zone)));

    @NonNull
    private final Comparator<Expedition> comparator;

    TruckLoadSort(@NonNull Comparator<Expedition> comparator) {
        this.comparator = comparator;
    }

    @NonNull
    public Comparator<Expedition> comparator() {
        return comparator;
    }

    @NonNull
    private static Comparator<Expedition> byLoadOrderAfter(@NonNull Comparator<Expedition> comparator) {
        return comparator.thenComparingInt(Expedition::loadOrder);
    }

    /** Nombres y poblaciones: sin distinguir mayúsculas ni acentos ("Àvila" junto a "Avila"). */
    @NonNull
    private static Comparator<Expedition> byText(@NonNull Function<Expedition, String> text) {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        return byLoadOrderAfter(Comparator.comparing(text, collator));
    }
}
