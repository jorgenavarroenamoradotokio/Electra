package com.redur.electra.ui.carga.camion;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;

import java.util.Objects;

/**
 * Etiqueta de una expedición: sustituye a los pictogramas del prototipo por texto, con icono
 * solo donde ayuda a reconocerla de un vistazo.
 *
 * @param icon  icono que acompaña al texto, o 0 si va sin icono.
 * @param alert si pide atención antes de cargar; se pinta con los colores de aviso, además del icono.
 */
public record TruckLoadTag(@NonNull UiText label, @DrawableRes int icon, boolean alert) {

    public TruckLoadTag {
        Objects.requireNonNull(label, "label");
    }
}
