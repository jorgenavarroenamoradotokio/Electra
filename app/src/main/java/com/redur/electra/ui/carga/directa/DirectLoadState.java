package com.redur.electra.ui.carga.directa;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.redur.electra.data.model.bulto.BultoType;

import java.util.List;
import java.util.Objects;

/** Estado de la carga con precinto: primero se pide el precinto y después se leen los bultos. */
public sealed interface DirectLoadState permits DirectLoadState.SealEntry, DirectLoadState.Reading {

    /** A la espera del precinto; {@code sealError} explica por qué no vale el introducido. */
    record SealEntry(@Nullable @StringRes Integer sealError) implements DirectLoadState {
    }

    /**
     * Precinto aceptado: se leen bultos.
     *
     * @param fixedType tipo que se asocia a cada bulto leído a partir de ahora, o null si ninguno.
     * @param readings  bultos leídos, el último primero.
     * @param feedback  resultado de la última lectura, o null si aún no se ha leído ninguno.
     */
    record Reading(@NonNull String seal,
                   @Nullable BultoType fixedType,
                   @NonNull List<DirectLoadReading> readings,
                   @Nullable Feedback feedback) implements DirectLoadState {
        public Reading {
            Objects.requireNonNull(seal, "seal");
            readings = List.copyOf(readings);
        }
    }

    /** Resultado de una lectura de bulto, para confirmarla al operario sin que mire la lista. */
    sealed interface Feedback permits Feedback.Added, Feedback.Duplicate {

        record Added(@NonNull DirectLoadReading reading) implements Feedback {
            public Added {
                Objects.requireNonNull(reading, "reading");
            }
        }

        /** El bulto ya estaba leído en esta carga: no se añade otra vez. */
        record Duplicate(@NonNull String barcode) implements Feedback {
            public Duplicate {
                Objects.requireNonNull(barcode, "barcode");
            }
        }
    }
}
