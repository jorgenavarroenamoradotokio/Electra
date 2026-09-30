package com.redur.electra.ui.menu;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;

/** Qué hace una entrada del menú lateral al pulsarla. */
@FunctionalInterface
public interface MenuAction {

    /**
     * @param args argumentos del destino, creados con {@link MenuArgs#toBundle()}: incluyen los
     *             permisos del menú pulsado para que la pantalla los lea con {@link MenuArgs#fromBundle}.
     */
    void navigate(@NonNull NavController navController, @NonNull Bundle args);
}
