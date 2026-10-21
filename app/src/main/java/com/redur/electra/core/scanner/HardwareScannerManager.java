package com.redur.electra.core.scanner;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;

/**
 * Escáner hardware del terminal. Se registra como observer del lifecycle de la vista
 * ({@code getViewLifecycleOwner()} en un Fragment) y solo entrega lecturas mientras está en RESUMED.
 */
public interface HardwareScannerManager extends DefaultLifecycleObserver {

    /** False si el terminal no tiene escáner hardware compatible y solo queda la cámara. */
    boolean isAvailable();

    interface ScanListener {
        @MainThread
        void onScanReceived(@NonNull String barcode);
    }

}
