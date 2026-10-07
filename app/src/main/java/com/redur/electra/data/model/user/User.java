package com.redur.electra.data.model.user;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Usuario autenticado. No contiene credenciales. */
public record User(
        @NonNull String username,
        @Nullable String fullName,
        @Nullable String plazaId,
        @NonNull List<MenuItem> menu,
        @NonNull Set<Integer> permission
) {

    public User {
        Objects.requireNonNull(username, "username");
        menu = List.copyOf(menu);
        permission = Set.copyOf(permission);
    }

    /** Nombre con el que se presenta al usuario: el completo si existe, si no su usuario. */
    @NonNull
    public String displayName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    /** Inicial del nombre para el avatar, o null si el nombre está vacío. */
    @Nullable
    public String initial() {
        String name = displayName();
        if (name.isBlank()) {
            return null;
        }
        return new String(Character.toChars(name.strip().codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    /** Copia del usuario asignado a otra plaza. */
    @NonNull
    public User withPlazaId(@NonNull String newPlazaId) {
        return new User(username, fullName, newPlazaId, menu, permission);
    }

    public boolean hasMenuActive (int menuId){
        return menu.stream().filter(menuItem -> menuItem.id() == menuId).count() == 1;
    }

    public boolean hasMenuPermission (int menuId, int permissionId){
        for (MenuItem item : menu) {
            if (item.id() == menuId && item.permissions().contains(permissionId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(int permissionId) {
        return permission.contains(permissionId);
    }
}
