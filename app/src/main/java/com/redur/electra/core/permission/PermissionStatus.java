package com.redur.electra.core.permission;

import androidx.annotation.NonNull;

/** Respuesta del usuario a una petición de permiso. */
public enum PermissionStatus {

    /** "Mientras se usa la app" o "Solo esta vez": se puede continuar. */
    GRANTED,

    /** "No permitir", pero el sistema aún dejará volver a preguntar. */
    DENIED,

    /**
     * Denegado para siempre: el sistema ya no muestra su diálogo y solo puede activarse desde
     * los ajustes de la app.
     */
    PERMANENTLY_DENIED;

    /**
     * @param canAskAgain lo que devuelve {@code shouldShowRequestPermissionRationale} tras la
     *                    respuesta: false si el sistema ya no volverá a mostrar el diálogo.
     */
    @NonNull
    public static PermissionStatus from(boolean granted, boolean canAskAgain) {
        if (granted) {
            return GRANTED;
        }
        return canAskAgain ? DENIED : PERMANENTLY_DENIED;
    }
}
