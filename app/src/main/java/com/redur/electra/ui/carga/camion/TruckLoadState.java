package com.redur.electra.ui.carga.camion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.carga.Expedition;

import java.util.List;
import java.util.Objects;

/** Estado de la carga del camión: las expediciones asignadas y cómo se están viendo. */
public sealed interface TruckLoadState permits
        TruckLoadState.Loading,
        TruckLoadState.Empty,
        TruckLoadState.Failed,
        TruckLoadState.Ready {

    record Loading() implements TruckLoadState {
    }

    /** La carga no tiene expediciones asignadas. Se puede refrescar. */
    record Empty() implements TruckLoadState {
    }

    /** No se pudo obtener la carga; {@code message} explica qué ha pasado. Se puede reintentar. */
    record Failed(@NonNull UiText message) implements TruckLoadState {
        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * Expediciones de la carga.
     *
     * @param expeditions    las que se ven, ya filtradas por vuelta y ordenadas.
     * @param trips          vueltas presentes en la carga, de menor a mayor.
     * @param tripFilter     vuelta por la que se filtra, o null si se ven todas.
     * @param selected       nº de la expedición elegida para sus acciones, o null si ninguna.
     * @param completeCount  expediciones de toda la carga sin diferencias.
     * @param totalCount     expediciones de toda la carga, se vean o no.
     * @param summary        resumen de toda la carga, se vea filtrada o no.
     * @param refreshing     se está volviendo a pedir la carga sin dejar de mostrar la actual.
     * @param refreshError   fallo del último refresco, a avisar una vez; la carga mostrada sigue valiendo.
     */
    record Ready(@NonNull List<Expedition> expeditions,
                 @NonNull List<Integer> trips,
                 @Nullable Integer tripFilter,
                 @NonNull TruckLoadSort sort,
                 @Nullable String selected,
                 int completeCount,
                 int totalCount,
                 @NonNull TruckLoadSummary summary,
                 boolean refreshing,
                 @Nullable UiText refreshError) implements TruckLoadState {

        public Ready {
            expeditions = List.copyOf(expeditions);
            trips = List.copyOf(trips);
            Objects.requireNonNull(sort, "sort");
            Objects.requireNonNull(summary, "summary");
        }

        /** Solo se finaliza una carga sin diferencias en ninguna expedición. */
        public boolean canFinish() {
            return completeCount == totalCount && !refreshing;
        }

        public boolean hasSelection() {
            return selected != null;
        }
    }
}
