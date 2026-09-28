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
 * sea null.
 */
@Singleton
public class UserSession {

    @Nullable
    private volatile User user;

    @Inject
    public UserSession() {
    }

    public void start(@NonNull User user) {
        this.user = Objects.requireNonNull(user, "user");
    }

    @Nullable
    public User getUser() {
        return user;
    }

    public boolean isActive() {
        return user != null;
    }

    public void clear() {
        user = null;
    }
}
