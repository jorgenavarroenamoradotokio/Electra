package com.redur.electra.data.repository;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.PrinterFailure;
import com.redur.electra.data.bluetooth.BluetoothPrinterGateway;
import com.redur.electra.data.bluetooth.PrinterConnectionException;
import com.redur.electra.data.model.printer.Printer;
import com.redur.electra.fake.FakeBluetoothPrinterGateway;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class PrinterRepositoryTest {

    private static final Printer ZEBRA = new Printer("ZQ520", "AC:3F:A4:00:00:01", true, true);
    private static final Printer HEADSET = new Printer("Auriculares", "AC:3F:A4:00:00:02", true, false);
    private static final String ZPL = "^XA^FO50,50^FDCaña^FS^XZ";

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final Executor direct = Runnable::run;
    private final FakeBluetoothPrinterGateway gateway = new FakeBluetoothPrinterGateway();
    private final PrinterRepository repository = new PrinterRepository(gateway, direct, direct);
    private final RecordingCallback callback = new RecordingCallback();

    @Test
    public void emparejadas_lasImpresorasPrimero() {
        gateway.paired = List.of(HEADSET, ZEBRA);

        assertEquals(List.of(ZEBRA, HEADSET), repository.getPairedPrinters());
    }

    @Test
    public void emparejadasSinPermiso_listaVacia() {
        gateway.paired = List.of(ZEBRA);
        gateway.permissionRevoked = true;

        assertEquals(List.of(), repository.getPairedPrinters());
    }

    @Test
    public void imprimir_enviaElZplEnUtf8ALaImpresora() {
        repository.print(ZEBRA, ZPL, callback);

        assertEquals(Boolean.TRUE, callback.result);
        assertEquals(ZEBRA.address(), gateway.lastAddress());
        assertArrayEquals(ZPL.getBytes(StandardCharsets.UTF_8), gateway.lastData());
    }

    @Test
    public void sinConexion_esFalloDeConexion() {
        gateway.sendFailure = new PrinterConnectionException("apagada", new IOException());

        repository.print(ZEBRA, ZPL, callback);

        assertEquals(new AppError.Printer(PrinterFailure.CONNECTION), callback.error);
    }

    @Test
    public void corteAlEnviar_esFalloDeEnvio() {
        gateway.sendFailure = new IOException("broken pipe");

        repository.print(ZEBRA, ZPL, callback);

        assertEquals(new AppError.Printer(PrinterFailure.SEND), callback.error);
    }

    @Test
    public void permisoRevocado_esBluetoothNoDisponible() {
        gateway.sendFailure = new SecurityException("sin BLUETOOTH_CONNECT");

        repository.print(ZEBRA, ZPL, callback);

        assertEquals(new AppError.Printer(PrinterFailure.BLUETOOTH_UNAVAILABLE), callback.error);
    }

    @Test
    public void cancelada_noEntregaResultado() {
        List<Runnable> pending = new ArrayList<>();
        PrinterRepository deferred = new PrinterRepository(gateway, pending::add, direct);

        deferred.print(ZEBRA, ZPL, callback).cancel();
        pending.forEach(Runnable::run);

        assertNull(callback.result);
        assertNull(callback.error);
    }

    @Test
    public void busquedaSinPermiso_terminaEnElActo() {
        gateway.permissionRevoked = true;
        boolean[] finished = {false};

        repository.discoverPrinters(new BluetoothPrinterGateway.DiscoveryListener() {
            @Override
            public void onPrinterFound(@NonNull Printer printer) {
            }

            @Override
            public void onDiscoveryFinished() {
                finished[0] = true;
            }
        });

        assertTrue(finished[0]);
    }

    private static final class RecordingCallback implements ResultCallback<Boolean> {
        @Nullable
        Boolean result;
        @Nullable
        AppError error;

        @Override
        public void onSuccess(@NonNull Boolean result) {
            this.result = result;
        }

        @Override
        public void onError(@NonNull AppError error) {
            this.error = error;
        }
    }
}
