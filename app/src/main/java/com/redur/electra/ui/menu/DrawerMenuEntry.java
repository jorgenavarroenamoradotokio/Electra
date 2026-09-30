package com.redur.electra.ui.menu;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;

import java.util.List;
import java.util.Objects;

/** Entrada del menú lateral ya ordenada en jerarquía: si tiene hijos se pinta como sección. */
public record DrawerMenuEntry(int menuId, @NonNull UiText title, @NonNull List<DrawerMenuEntry> children) {

    public DrawerMenuEntry {
        Objects.requireNonNull(title, "title");
        children = List.copyOf(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
