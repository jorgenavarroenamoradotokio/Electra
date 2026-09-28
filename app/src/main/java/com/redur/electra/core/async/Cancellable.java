package com.redur.electra.core.async;

/**
 * Operación en curso que puede cancelarse (p. ej. desde ViewModel#onCleared) sin exponer
 * cómo se ejecuta por debajo.
 */
@FunctionalInterface
public interface Cancellable {

    void cancel();
}
