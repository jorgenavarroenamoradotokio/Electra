package com.redur.electra.fake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.data.bluetooth.BluetoothPrinterGateway;
import com.redur.electra.data.model.printer.Printer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth controlado por el test: la búsqueda queda abierta hasta que el test anuncia
 * impresoras con {@link #find} y la termina con {@link #finishDiscovery}.
 */
public final class FakeBluetoothPrinterGateway implements BluetoothPrinterGateway {

    public boolean supported = true;
    public boolean enabled = true;
    public boolean canDiscover = true;
    public List<Printer> paired = new ArrayList<>();
    /** Lo que lanza {@link #send}; null si el envío funciona. */
    @Nullable
    public Exception sendFailure;
    /** Simula haber perdido el permiso: listar o buscar lanzan SecurityException. */
    public boolean permissionRevoked;

    @Nullable
    private DiscoveryListener discoveryListener;
    private int discoveries;
    private boolean discoveryCanceled;
    @Nullable
    private String lastAddress;
    @Nullable
    private byte[] lastData;

    @Override
    public boolean isSupported() {
        return supported;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean canDiscover() {
        return canDiscover;
    }

    @NonNull
    @Override
    public List<Printer> pairedPrinters() {
        requirePermission();
        return paired;
    }

    @NonNull
    @Override
    public Cancellable discover(@NonNull DiscoveryListener listener) {
        requirePermission();
        discoveries++;
        discoveryCanceled = false;
        discoveryListener = listener;
        return () -> {
            discoveryCanceled = true;
            discoveryListener = null;
        };
    }

    @Override
    public void send(@NonNull String address, @NonNull byte[] data) throws IOException {
        lastAddress = address;
        lastData = data;
        if (sendFailure instanceof IOException io) {
            throw io;
        }
        if (sendFailure instanceof RuntimeException runtime) {
            throw runtime;
        }
    }

    private void requirePermission() {
        if (permissionRevoked) {
            throw new SecurityException("Permiso de Bluetooth revocado");
        }
    }

    public void find(@NonNull Printer printer) {
        if (discoveryListener != null) {
            discoveryListener.onPrinterFound(printer);
        }
    }

    public void finishDiscovery() {
        DiscoveryListener listener = discoveryListener;
        discoveryListener = null;
        if (listener != null) {
            listener.onDiscoveryFinished();
        }
    }

    public int discoveries() {
        return discoveries;
    }

    public boolean discoveryCanceled() {
        return discoveryCanceled;
    }

    @Nullable
    public String lastAddress() {
        return lastAddress;
    }

    @Nullable
    public byte[] lastData() {
        return lastData;
    }
}
