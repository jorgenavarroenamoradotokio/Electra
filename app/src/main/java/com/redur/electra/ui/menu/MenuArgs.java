package com.redur.electra.ui.menu;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Argumentos con los que se abre una pantalla desde el menú lateral: el menú pulsado y sus
 * permisos, tomados de la sesión en el momento del toque. La pantalla destino los recupera con
 * {@code MenuArgs.fromBundle(requireArguments())} y consulta {@link #hasPermission(int)}.
 */
public record MenuArgs(int menuId, @NonNull Set<Integer> permissions) {

    private static final String KEY_MENU_ID = "menuArgs.menuId";
    private static final String KEY_PERMISSIONS = "menuArgs.permissions";

    public MenuArgs {
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(int permissionId) {
        return permissions.contains(permissionId);
    }

    @NonNull
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_MENU_ID, menuId);
        bundle.putIntArray(KEY_PERMISSIONS, permissions.stream().mapToInt(Integer::intValue).toArray());
        return bundle;
    }

    /** @throws IllegalArgumentException si la pantalla no se abrió desde el menú lateral. */
    @NonNull
    public static MenuArgs fromBundle(@NonNull Bundle bundle) {
        int[] values = bundle.getIntArray(KEY_PERMISSIONS);
        if (!bundle.containsKey(KEY_MENU_ID) || values == null) {
            throw new IllegalArgumentException("La pantalla no se ha abierto desde el menú lateral");
        }
        Set<Integer> permissions = new HashSet<>();
        for (int value : values) {
            permissions.add(value);
        }
        return new MenuArgs(bundle.getInt(KEY_MENU_ID), permissions);
    }
}
