package com.redur.electra.core.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Objects;

/**
 * Texto para la UI que el ViewModel puede emitir sin conocer el {@link Context}: se resuelve en
 * la Activity/Fragment al renderizar, por lo que respeta el idioma y los cambios de configuración.
 */
public sealed interface UiText permits UiText.Res, UiText.Raw {

    @NonNull
    String resolve(@NonNull Context context);

    /** Recurso de strings. Los argumentos se guardan en una lista para que equals compare contenido. */
    record Res(@StringRes int id, @NonNull List<Object> args) implements UiText {

        public Res(@StringRes int id) {
            this(id, List.of());
        }

        public Res {
            args = List.copyOf(args);
        }

        @NonNull
        @Override
        public String resolve(@NonNull Context context) {
            return context.getString(id, args.toArray());
        }
    }

    /** Texto ya listo para mostrar (p. ej. un mensaje de servidor pensado para el usuario). */
    record Raw(@NonNull String text) implements UiText {

        public Raw {
            Objects.requireNonNull(text, "text");
        }

        @NonNull
        @Override
        public String resolve(@NonNull Context context) {
            return text;
        }
    }
}
