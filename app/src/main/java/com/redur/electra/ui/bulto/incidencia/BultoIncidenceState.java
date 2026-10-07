package com.redur.electra.ui.bulto.incidencia;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.bulto.BultoIncidence;

import java.util.List;
import java.util.Objects;

/** Estado de la carga de incidencias del tipo de operación. */
public sealed interface BultoIncidenceState permits
        BultoIncidenceState.Loading,
        BultoIncidenceState.Ready,
        BultoIncidenceState.Empty,
        BultoIncidenceState.Failed {

    record Loading() implements BultoIncidenceState {
    }

    /** Incidencias seleccionables, al menos una, en el orden en que se muestran. */
    record Ready(@NonNull List<BultoIncidence> incidences) implements BultoIncidenceState {
        public Ready {
            incidences = List.copyOf(incidences);
        }
    }

    /** El tipo de operación no tiene incidencias. Se puede reintentar. */
    record Empty() implements BultoIncidenceState {
    }

    /** No se pudieron cargar; {@code message} explica qué ha pasado. Se puede reintentar. */
    record Failed(@NonNull UiText message) implements BultoIncidenceState {
        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }
}
