package com.redur.electra.data.model.user;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/** Usuario autenticado. No contiene credenciales. */
public record User(
        @NonNull String username,
        @Nullable String fullName,
        @Nullable String plazaId,
        @NonNull List<MenuItem> menu
) {

    public User {
        Objects.requireNonNull(username, "username");
        menu = List.copyOf(menu);
    }
}
