package com.redur.electra.core.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.Nullable;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Identificador del terminal para registrarlo en los sistemas. Desde Android 10 una app
 * normal no puede leer el IMEI ni el número de serie, así que se usa {@code ANDROID_ID}: no requiere
 * permisos, es el mismo en cualquier fabricante y sobrevive a reinstalaciones y actualizaciones.
 * Cambia tras un restablecimiento de fábrica, y es distinto por clave de firma de la app (debug y
 * release dan valores diferentes en el mismo terminal).
 */
@Singleton
public class TerminalIdProvider {

    private final Context context;
    @Nullable
    private volatile String terminalId;

    @Inject
    public TerminalIdProvider(@ApplicationContext Context context) {
        this.context = context;
    }

    /** Hexadecimal en mayúsculas, más fácil de leer y dictar. Null si el sistema no lo facilita. */
    @Nullable
    @SuppressLint("HardwareIds") // Uso intencionado: identificar el terminal ante el backend
    public String getTerminalId() {
        String id = terminalId;
        if (id == null) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isBlank()) {
                id = androidId.toUpperCase(Locale.ROOT);
                terminalId = id;
            }
        }
        return id;
    }
}
