package com.redur.electra.data.bluetooth;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.data.model.printer.Printer;

import java.io.IOException;
import java.util.List;

/**
 * Acceso al Bluetooth del dispositivo para localizar impresoras y enviarles datos. Todas las
 * operaciones exigen el permiso {@code AppPermission.BLUETOOTH} concedido; si se pierde lanzan
 * {@link SecurityException}.
 */
public interface BluetoothPrinterGateway {

    /** Avisos de una búsqueda; llegan en el hilo principal. */
    interface DiscoveryListener {
        @MainThread
        void onPrinterFound(@NonNull Printer printer);

        /** La búsqueda ha terminado (o no se pudo iniciar). */
        @MainThread
        void onDiscoveryFinished();
    }

    /** Si el dispositivo tiene Bluetooth. */
    boolean isSupported();

    boolean isEnabled();

    /**
     * Si una búsqueda puede encontrar dispositivos. Hasta Android 11 el sistema solo los entrega
     * con la ubicación activada; los emparejados se pueden usar igualmente.
     */
    boolean canDiscover();

    /** Dispositivos ya emparejados con nombre. */
    @NonNull
    List<Printer> pairedPrinters();

    /** Busca dispositivos cercanos (unos 12 s). El {@link Cancellable} la detiene y deja de avisar. */
    @MainThread
    @NonNull
    Cancellable discover(@NonNull DiscoveryListener listener);

    /**
     * Envía {@code data} tal cual al dispositivo por el perfil serie (SPP), que es por donde las
     * impresoras ZPL reciben las etiquetas. Bloquea hasta terminar.
     *
     * @throws PrinterConnectionException si no se puede conectar.
     * @throws IOException                si la conexión se corta al enviar.
     */
    @WorkerThread
    void send(@NonNull String address, @NonNull byte[] data) throws IOException;
}
