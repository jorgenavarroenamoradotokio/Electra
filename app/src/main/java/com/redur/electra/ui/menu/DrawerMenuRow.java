package com.redur.electra.ui.menu;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;

import java.util.Objects;

/**
 * Fila visible del menú lateral: el árbol de {@link DrawerMenuEntry} ya aplanado según qué
 * submenús están desplegados.
 *
 * @param depth    nivel en el árbol (0 = primer nivel), para la sangría.
 * @param group    tiene submenús: al pulsarla se despliega o pliega en lugar de navegar.
 * @param selected es la opción de la pantalla actual.
 */
public record DrawerMenuRow(
        int menuId,
        @NonNull UiText title,
        @DrawableRes int icon,
        int depth,
        boolean group,
        boolean expanded,
        boolean selected
) {

    public DrawerMenuRow {
        Objects.requireNonNull(title, "title");
    }

    @NonNull
    public DrawerMenuRow withExpanded(boolean newExpanded) {
        return new DrawerMenuRow(menuId, title, icon, depth, group, newExpanded, selected);
    }
}
