package com.redur.electra.core.ui;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Estado genérico de una operación asíncrona lanzada desde la UI (login, envío, carga...).
 * Los estados propios de un formulario (errores por campo) se modelan aparte en cada feature.
 */
public sealed interface UiState
        permits UiState.Idle, UiState.Loading, UiState.Success, UiState.Error {

    record Idle() implements UiState {
    }

    record Loading() implements UiState {
    }

    record Success() implements UiState {
    }

    /** El mensaje se resuelve en la UI; se obtiene con {@link ErrorUiMapper#toUiText}. */
    record Error(@NonNull UiText message) implements UiState {
        public Error {
            Objects.requireNonNull(message, "message");
        }
    }
}
