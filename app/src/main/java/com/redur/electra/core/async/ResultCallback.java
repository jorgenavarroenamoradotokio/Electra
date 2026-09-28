package com.redur.electra.core.async;

import androidx.annotation.NonNull;

import com.redur.electra.core.error.AppError;

/**
 * Resultado de una operación asíncrona. Se invoca una única vez, en el hilo principal,
 * y nunca tras cancelar la operación.
 */
public interface ResultCallback<T> {

    void onSuccess(@NonNull T result);

    void onError(@NonNull AppError error);
}
