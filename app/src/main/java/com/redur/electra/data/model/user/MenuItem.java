package com.redur.electra.data.model.user;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Entrada del menú del usuario tal como la define el servidor (lista plana): la jerarquía se
 * construye a partir de {@code parentId} en la pantalla que pinta el menú.
 */
public record MenuItem(
        int id,
        @NonNull String text,
        @Nullable Integer parentId,
        @NonNull Set<Integer> permissions
) {

    public MenuItem {
        Objects.requireNonNull(text, "text");
        permissions = Set.copyOf(permissions);
    }

    public boolean isRoot() {
        return parentId == null;
    }
}
