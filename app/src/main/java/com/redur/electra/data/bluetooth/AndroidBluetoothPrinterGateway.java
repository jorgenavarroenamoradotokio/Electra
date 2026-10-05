package com.redur.electra.data.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.location.LocationManagerCompat;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.data.model.printer.Printer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

/**
 * {@link BluetoothPrinterGateway} sobre el Bluetooth clásico de Android. Quien lo usa garantiza el
 * permiso (por eso se suprime el aviso de lint); si se revoca, el sistema lanza SecurityException.
 */
@SuppressLint("MissingPermission")
public class AndroidBluetoothPrinterGateway implements BluetoothPrinterGateway {

    /** Perfil serie (SPP): el canal por el que las impresoras ZPL reciben datos en crudo. */
    private static final UUID SERIAL_PORT_PROFILE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    /**
     * Margen antes de cerrar la conexión: algunas impresoras descartan lo que aún no han leído
     * si se cierra justo después de escribir.
     */
    private static final long SEND_SETTLE_MS = 500L;

    private final Context context;

    @Inject
    public AndroidBluetoothPrinterGateway(@ApplicationContext Context context) {
        this.context = context;
    }

    @Override
    public boolean isSupported() {
        return adapter() != null;
    }

    @Override
    public boolean isEnabled() {
        BluetoothAdapter adapter = adapter();
        return adapter != null && adapter.isEnabled();
    }

    @Override
    public boolean canDiscover() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return true;
        }
        LocationManager locationManager = context.getSystemService(LocationManager.class);
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager);
    }

    @NonNull
    @Override
    public List<Printer> pairedPrinters() {
        BluetoothAdapter adapter = adapter();
        Set<BluetoothDevice> bonded = adapter != null ? adapter.getBondedDevices() : null;
        List<Printer> printers = new ArrayList<>();
        if (bonded != null) {
            for (BluetoothDevice device : bonded) {
                Printer printer = toPrinter(device);
                if (printer != null) {
                    printers.add(printer);
                }
            }
        }
        return printers;
    }

    @NonNull
    @Override
    public Cancellable discover(@NonNull DiscoveryListener listener) {
        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            listener.onDiscoveryFinished();
            return () -> { };
        }
        AtomicBoolean stopped = new AtomicBoolean();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (stopped.get()) {
                    return;
                }
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = IntentCompat.getParcelableExtra(
                            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    Printer printer = device != null ? toPrinter(device) : null;
                    if (printer != null) {
                        listener.onPrinterFound(printer);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    stop(this, stopped, adapter);
                    listener.onDiscoveryFinished();
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Solo emisiones del sistema: no hace falta exponer el receptor a otras apps
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        if (!adapter.startDiscovery()) {
            Timber.w("No se ha podido iniciar la búsqueda de dispositivos Bluetooth");
            stop(receiver, stopped, adapter);
            listener.onDiscoveryFinished();
        }
        return () -> stop(receiver, stopped, adapter);
    }

    @WorkerThread
    @Override
    public void send(@NonNull String address, @NonNull byte[] data) throws IOException {
        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            throw new IOException("El dispositivo no tiene Bluetooth");
        }
        // La búsqueda ralentiza (y a veces impide) la conexión
        adapter.cancelDiscovery();
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            throw new PrinterConnectionException("Dirección Bluetooth no válida", e);
        }
        try (BluetoothSocket socket = connect(device)) {
            OutputStream output = socket.getOutputStream();
            output.write(data);
            output.flush();
            waitForPrinterToRead();
        }
    }

    @WorkerThread
    @NonNull
    private static BluetoothSocket connect(@NonNull BluetoothDevice device) throws IOException {
        BluetoothSocket socket = null;
        try {
            // Conexión segura: si no está emparejada, el sistema pide emparejarla
            socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE);
            socket.connect();
            return socket;
        } catch (IOException e) {
            closeQuietly(socket);
            throw new PrinterConnectionException("No se ha podido conectar con la impresora", e);
        }
    }

    @WorkerThread
    private static void waitForPrinterToRead() {
        try {
            Thread.sleep(SEND_SETTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(@Nullable BluetoothSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            Timber.w(e, "No se ha podido cerrar la conexión Bluetooth");
        }
    }

    private void stop(@NonNull BroadcastReceiver receiver, @NonNull AtomicBoolean stopped,
                      @NonNull BluetoothAdapter adapter) {
        if (stopped.getAndSet(true)) {
            return;
        }
        context.unregisterReceiver(receiver);
        try {
            adapter.cancelDiscovery();
        } catch (SecurityException e) {
            // Permiso revocado durante la búsqueda: no hay nada que detener
            Timber.w(e, "No se ha podido detener la búsqueda Bluetooth");
        }
    }

    /** Sin nombre no hay forma de que el usuario la reconozca: se descarta. */
    @Nullable
    private static Printer toPrinter(@NonNull BluetoothDevice device) {
        String name = device.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        BluetoothClass bluetoothClass = device.getBluetoothClass();
        boolean likelyPrinter = bluetoothClass != null
                && bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.IMAGING;
        return new Printer(name, device.getAddress(),
                device.getBondState() == BluetoothDevice.BOND_BONDED, likelyPrinter);
    }

    @Nullable
    private BluetoothAdapter adapter() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager != null ? manager.getAdapter() : null;
    }
}
