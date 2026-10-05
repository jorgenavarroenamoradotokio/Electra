package com.redur.electra.ui.label;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;

import java.util.List;
import java.util.Objects;

/** Estado de la vista previa de la etiqueta. */
public sealed interface LabelPreviewState permits
        LabelPreviewState.Generating,
        LabelPreviewState.Ready,
        LabelPreviewState.Failed {

    /** El backend está generando la etiqueta. */
    record Generating() implements LabelPreviewState {
    }

    /** Etiqueta lista: una imagen por página, al menos una. */
    record Ready(@NonNull List<Bitmap> pages) implements LabelPreviewState {
        public Ready {
            pages = List.copyOf(pages);
        }
    }

    /** No se pudo generar o mostrar; {@code message} explica qué ha pasado. Se puede reintentar. */
    record Failed(@NonNull UiText message) implements LabelPreviewState {
        public Failed {
            Objects.requireNonNull(message, "message");
        }
    }
}
