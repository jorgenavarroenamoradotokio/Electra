package com.redur.electra.ui.bulto.muelle;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.bulto.BultoDock;

import java.util.Objects;

/** Estado de la lectura en la puerta de salida: del bulto leído se busca su muelle. */
public sealed interface BultoDockState permits
        BultoDockState.Waiting,
        BultoDockState.Loading,
        BultoDockState.Found,
        BultoDockState.NotFound,
        BultoDockState.Failed {

    /** Aún no se ha leído ningún bulto, o la lectura anterior ya se grabó o se canceló. */
    record Waiting() implements BultoDockState {
    }

    record Loading(@NonNull String barcode) implements BultoDockState {
        public Loading {
            Objects.requireNonNull(barcode, "barcode");
        }
    }

    /** Muelle del bulto: la lectura se puede grabar. */
    record Found(@NonNull String barcode, @NonNull BultoDock dock) implements BultoDockState {
        public Found {
            Objects.requireNonNull(barcode, "barcode");
            Objects.requireNonNull(dock, "dock");
        }
    }

    /** El bulto no tiene muelle asignado. Se puede leer otro. */
    record NotFound(@NonNull String barcode) implements BultoDockState {
        public NotFound {
            Objects.requireNonNull(barcode, "barcode");
        }
    }

    /** No se pudo consultar; {@code message} explica qué ha pasado. Se puede reintentar. */
    record Failed(@NonNull String barcode, @NonNull UiText message) implements BultoDockState {
        public Failed {
            Objects.requireNonNull(barcode, "barcode");
            Objects.requireNonNull(message, "message");
        }
    }
}
