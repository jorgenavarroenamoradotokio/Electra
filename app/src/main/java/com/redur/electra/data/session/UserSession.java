package com.redur.electra.data.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.user.User;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sesión del usuario autenticado, solo en memoria: se pierde si el sistema mata el proceso,
 * por lo que las pantallas que la necesiten deben volver al login cuando {@link #getUser()}
 * sea null. Las credenciales se guardan aparte del {@link User} para que nunca lleguen a la UI.
 */
@Singleton
public class UserSession {

    /** Usuario y credenciales se publican juntos para que nunca se lea uno sin el otro. */
    private record Active(@NonNull User user, @NonNull Credentials credentials) {
    }

    @Nullable
    private volatile Active active;

    @Inject
    public UserSession() {
    }

    public void start(@NonNull User user, @NonNull Credentials credentials) {
        this.active = new Active(
                Objects.requireNonNull(user, "user"),
                Objects.requireNonNull(credentials, "credentials"));
    }

    @Nullable
    public User getUser() {
        Active current = active;
        return current != null ? current.user() : null;
    }

    /** Solo para la capa de datos: la UI no debe acceder a la contraseña. */
    @Nullable
    public Credentials getCredentials() {
        Active current = active;
        return current != null ? current.credentials() : null;
    }

    /** Refleja en la sesión la plaza ya aceptada por el backend. Sin sesión no hace nada. */
    public void updatePlaza(@NonNull String plazaId) {
        Active current = active;
        if (current != null) {
            active = new Active(current.user().withPlazaId(plazaId), current.credentials());
        }
    }

    public boolean isActive() {
        return active != null;
    }

    public void clear() {
        active = null;
    }
}
