package com.redur.electra.data.repository;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.PrinterFailure;
import com.redur.electra.data.bluetooth.BluetoothPrinterGateway;
import com.redur.electra.data.bluetooth.PrinterConnectionException;
import com.redur.electra.data.model.printer.Printer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Impresoras Bluetooth: las emparejadas, la búsqueda de nuevas y el envío de etiquetas ZPL. Exige
 * el permiso {@code AppPermission.BLUETOOTH} concedido. El resultado de imprimir llega en el hilo
 * principal y no se entrega si se cancela; la etiqueta puede imprimirse igualmente si ya se envió.
 */
public class PrinterRepository {

    private final BluetoothPrinterGateway gateway;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    @Inject
    public PrinterRepository(BluetoothPrinterGateway gateway, @IoExecutor Executor ioExecutor,
                             @MainExecutor Executor mainExecutor) {
        this.gateway = gateway;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    public boolean isBluetoothSupported() {
        return gateway.isSupported();
    }

    public boolean isBluetoothEnabled() {
        return gateway.isEnabled();
    }

    /** Si buscar puede encontrar impresoras nuevas (hasta Android 11 exige la ubicación activada). */
    public boolean canDiscoverPrinters() {
        return gateway.canDiscover();
    }

    /** Emparejadas, las que se anuncian como impresora primero. Vacía si no se pueden leer. */
    @NonNull
    public List<Printer> getPairedPrinters() {
        try {
            return printersFirst(gateway.pairedPrinters());
        } catch (SecurityException e) {
            Timber.w(e, "Sin permiso para leer los dispositivos emparejados");
            return List.of();
        }
    }

    /** Si no se puede buscar (p. ej. sin permiso), se da por terminada en el acto. */
    @MainThread
    @NonNull
    public Cancellable discoverPrinters(@NonNull BluetoothPrinterGateway.DiscoveryListener listener) {
        try {
            return gateway.discover(listener);
        } catch (SecurityException e) {
            Timber.w(e, "Sin permiso para buscar dispositivos Bluetooth");
            listener.onDiscoveryFinished();
            return () -> { };
        }
    }

    /** Envía el ZPL a la impresora. El texto viaja en UTF-8 (la etiqueta debe declararlo con ^CI28). */
    @NonNull
    public Cancellable print(@NonNull Printer printer, @NonNull String zpl,
                             @NonNull ResultCallback<Boolean> callback) {
        AtomicBoolean canceled = new AtomicBoolean();
        byte[] data = zpl.getBytes(StandardCharsets.UTF_8);
        ioExecutor.execute(() -> {
            AppError error = send(printer, data);
            mainExecutor.execute(() -> {
                if (canceled.get()) {
                    return;
                }
                if (error == null) {
                    callback.onSuccess(Boolean.TRUE);
                } else {
                    callback.onError(error);
                }
            });
        });
        return () -> canceled.set(true);
    }

    /** Null si se ha enviado. */
    private AppError send(@NonNull Printer printer, @NonNull byte[] data) {
        try {
            gateway.send(printer.address(), data);
            Timber.i("Etiqueta enviada a la impresora %s (%d bytes)", printer.address(), data.length);
            return null;
        } catch (PrinterConnectionException e) {
            Timber.w(e, "No se ha podido conectar con la impresora %s", printer.address());
            return new AppError.Printer(PrinterFailure.CONNECTION);
        } catch (IOException e) {
            Timber.w(e, "Envío a la impresora %s interrumpido", printer.address());
            return new AppError.Printer(PrinterFailure.SEND);
        } catch (SecurityException e) {
            Timber.w(e, "Sin permiso de Bluetooth al imprimir");
            return new AppError.Printer(PrinterFailure.BLUETOOTH_UNAVAILABLE);
        }
    }

    /** Las que se anuncian como impresora primero; el resto conserva su orden. */
    @NonNull
    static List<Printer> printersFirst(@NonNull List<Printer> devices) {
        // List#sort es estable; Stream#toList no existe hasta API 34
        List<Printer> sorted = new ArrayList<>(devices);
        sorted.sort((a, b) -> Boolean.compare(b.likelyPrinter(), a.likelyPrinter()));
        return sorted;
    }
}
