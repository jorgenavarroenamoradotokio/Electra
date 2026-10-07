package com.redur.electra.ui.bulto.tipo;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.bulto.BultoType;

import java.util.List;
import java.util.Objects;

/** Estado de la carga de tipos de bulto. */
public sealed interface BultoTypeState permits
        BultoTypeState.Loading,
        BultoTypeState.Ready,
        BultoTypeState.Empty,
        BultoTypeState.Failed {

    record Loading() implements BultoTypeState {
    }

    /** Tipos seleccionables, al menos uno, en el orden en que los envía el backend. */
    record Ready(@NonNull List<BultoType> types) implements BultoTypeState {
        public Ready {
            types = List.copyOf(types);
        }
    }

    /** El backend no tiene tipos activos. Se puede reintentar. */
    record Empty() implements BultoTypeState {
    }

    /** No se pudieron cargar; {@code message} explica qué ha pasado. Se puede reintentar. */
    record Failed(@NonNull UiText message) implements BultoTypeState {
        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }
}
