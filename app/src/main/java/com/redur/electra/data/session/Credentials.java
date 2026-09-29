package com.redur.electra.data.session;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Credenciales con las que el usuario abrió la sesión. La API no devuelve la contraseña, así que
 * se conservan las enviadas en el login para las operaciones que el backend vuelve a autenticar
 * (p. ej. el cambio de plaza). Solo viven en memoria dentro de {@link UserSession}.
 */
public record Credentials(@NonNull String username, @NonNull String password) {

    public Credentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    /** Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log. */
    @NonNull
    @Override
    public String toString() {
        return "Credentials[username=" + username + ", password=****]";
    }
}
