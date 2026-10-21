package com.redur.electra.core.scanner;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.redur.electra.core.scanner.datalogic.DatalogicScannerManager;
import com.redur.electra.core.scanner.honeywell.HoneywellScannerManager;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

/**
 * Elige la implementación según el fabricante del terminal. El listener se pasa al crear porque
 * pertenece a la pantalla que escanea.
 */
public class ScannerManagerFactory {

    private static final String MANUFACTURER_HONEYWELL = "honeywell";
    private static final String MANUFACTURER_DATALOGIC = "datalogic";

    private final Context context;

    @Inject
    public ScannerManagerFactory(@ApplicationContext Context context) {
        this.context = context;
    }

    @NonNull
    public HardwareScannerManager create(@NonNull HardwareScannerManager.ScanListener listener) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);

        if (manufacturer.contains(MANUFACTURER_HONEYWELL)) {
            return new HoneywellScannerManager(context, listener);
        }

        if (manufacturer.contains(MANUFACTURER_DATALOGIC)) {
            return new DatalogicScannerManager(context, listener);
        }

        Timber.i("Fabricante no reconocido para escáner hardware: '%s'. Se usará solo escaneo por cámara.", Build.MANUFACTURER);
        return new NoOpScannerManager();
    }

}
