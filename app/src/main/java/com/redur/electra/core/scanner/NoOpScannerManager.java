package com.redur.electra.core.scanner;

/** Terminal sin escáner hardware compatible: no entrega lecturas y la pantalla usa la cámara. */
public class NoOpScannerManager implements HardwareScannerManager {

    @Override
    public boolean isAvailable() {
        return false;
    }
}
