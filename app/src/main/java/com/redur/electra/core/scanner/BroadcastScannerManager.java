package com.redur.electra.core.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.redur.electra.BuildConfig;

import java.util.Objects;

import timber.log.Timber;

/**
 * Escáner hardware que entrega las lecturas mediante un broadcast del servicio del fabricante.
 * El receiver solo está registrado mientras el lifecycle observado está en RESUMED.
 * <p>
 * El receiver es exportado porque el broadcast lo emite otra app (el servicio del sistema), así que
 * cualquier app instalada podría enviar un intent con la misma acción.
 */
public abstract class BroadcastScannerManager implements HardwareScannerManager {

    private final Context context;
    private final ScanListener listener;
    private boolean registered;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            if (intent != null) {
                handleScan(intent);
            }
        }
    };

    protected BroadcastScannerManager(@NonNull Context context, @NonNull ScanListener listener) {
        this.context = Objects.requireNonNull(context, "context");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @NonNull
    protected abstract IntentFilter createIntentFilter();

    /** Devuelve el código leído, o null si el intent no lo contiene. */
    @Nullable
    protected abstract String extractBarcode(@NonNull Intent intent);

    @Override
    public final boolean isAvailable() {
        return true;
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (registered) {
            return;
        }
        ContextCompat.registerReceiver(context, scanReceiver, createIntentFilter(), ContextCompat.RECEIVER_EXPORTED);
        registered = true;
        Timber.d("Receiver del escáner registrado");
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (!registered) {
            return;
        }
        context.unregisterReceiver(scanReceiver);
        registered = false;
        Timber.d("Receiver del escáner liberado");
    }

    private void handleScan(@NonNull Intent intent) {
        if (BuildConfig.DEBUG) {
            logExtras(intent.getExtras());
        }

        String barcode = extractBarcode(intent);
        if (barcode == null || barcode.isEmpty()) {
            Timber.w("No se pudo extraer el código de barras del intent %s", intent.getAction());
            return;
        }

        Timber.i("Código detectado: %s", barcode);
        listener.onScanReceived(barcode);
    }

    private static void logExtras(@Nullable Bundle extras) {
        if (extras == null) {
            Timber.d("El intent no contiene extras");
            return;
        }
        for (String key : extras.keySet()) {
            Timber.d("Extra -> %s = %s", key, extras.get(key));
        }
    }
}
