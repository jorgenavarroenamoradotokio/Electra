package com.redur.electra.core.ui;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/**
 * Márgenes que el contenido de una pantalla de borde a borde debe dejar libres.
 * <p>
 * Arriba siempre se deja la status bar. En los laterales y abajo solo se aparta la botonera de
 * navegación (3 botones), que se come los toques: con navegación por gestos la barra no es
 * pulsable y el contenido ocupa la pantalla completa. En horizontal la botonera se coloca en un
 * lateral, por eso no basta con el margen inferior. El notch se respeta en cualquier caso.
 */
public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    @NonNull
    public static Insets of(@NonNull WindowInsetsCompat windowInsets) {
        int cutout = WindowInsetsCompat.Type.displayCutout();
        Insets statusBar = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | cutout);
        Insets navigationButtons = windowInsets.getInsets(WindowInsetsCompat.Type.tappableElement() | cutout);
        return Insets.of(navigationButtons.left, statusBar.top, navigationButtons.right, navigationButtons.bottom);
    }
}
