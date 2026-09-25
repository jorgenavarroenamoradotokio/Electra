package com.redur.electra.core.ui;

/**
 * Estado genérico de una operación asíncrona lanzada desde la UI (login, envío, carga...).
 * Los estados propios de un formulario (errores por campo) se modelan aparte en cada feature.
 */
public sealed interface UiState
        permits UiState.Idle, UiState.Loading, UiState.Success, UiState.Error, UiState.ErrorMsg {

    record Idle() implements UiState {
    }

    record Loading() implements UiState {
    }

    record Success() implements UiState {
    }

    record Error(int codeError) implements UiState {
    }

    record ErrorMsg(String msg) implements UiState {
    }
}
