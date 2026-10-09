package com.redur.electra.ui.label;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.permission.PermissionStatus;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.bluetooth.BluetoothPrinterGateway;
import com.redur.electra.data.model.label.ZplLabel;
import com.redur.electra.data.model.printer.Printer;
import com.redur.electra.data.repository.LabelRepository;
import com.redur.electra.data.repository.PrinterRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Imprimir la etiqueta por Bluetooth: pide el permiso, comprueba que el Bluetooth está activo,
 * muestra las impresoras emparejadas mientras busca otras cercanas y, al elegir una, genera la
 * etiqueta en el backend y le envía su ZPL. Si falla se puede volver a elegir (la misma u otra).
 */
@HiltViewModel
public class PrintLabelViewModel extends ViewModel {

    private final MutableLiveData<PrintLabelState> state =
            new MutableLiveData<>(new PrintLabelState.RequestPermission());

    private final PrinterRepository printerRepository;
    private final LabelRepository labelRepository;

    // Solo accedidos desde el hilo principal
    /** Impresoras mostradas: se conservan mientras se imprime para volver a ellas si falla. */
    private final List<Printer> printers = new ArrayList<>();
    private boolean searching;
    @Nullable
    private Cancellable discovery;
    @Nullable
    private Cancellable pendingRequest;

    @Inject
    public PrintLabelViewModel(PrinterRepository printerRepository, LabelRepository labelRepository) {
        this.printerRepository = printerRepository;
        this.labelRepository = labelRepository;
    }

    public LiveData<PrintLabelState> getState() {
        return state;
    }

    /** La UI ya ha lanzado la petición de permiso; responderá con {@link #onPermissionResult}. */
    @MainThread
    public void onPermissionRequested() {
        if (state.getValue() instanceof PrintLabelState.RequestPermission) {
            state.setValue(new PrintLabelState.Busy());
        }
    }

    @MainThread
    public void onPermissionResult(@NonNull PermissionStatus status) {
        Timber.i("[ESTADO] Permiso de Bluetooth para imprimir: %s", status);
        switch (status) {
            case GRANTED -> checkBluetooth();
            case DENIED -> state.setValue(new PrintLabelState.PermissionDenied());
            case PERMANENTLY_DENIED -> state.setValue(new PrintLabelState.PermissionBlocked());
        }
    }

    /** "Permitir acceso" tras una denegación: se vuelve a preguntar. */
    @MainThread
    public void onAllowClicked() {
        if (state.getValue() instanceof PrintLabelState.PermissionDenied) {
            Timber.i("[ACCION] Permitir acceso al Bluetooth tras denegarlo");
            state.setValue(new PrintLabelState.RequestPermission());
        }
    }

    /** La UI ya muestra el aviso de ir a ajustes. */
    @MainThread
    public void onSettingsPromptShown() {
        if (state.getValue() instanceof PrintLabelState.PermissionBlocked) {
            state.setValue(new PrintLabelState.Busy());
        }
    }

    @MainThread
    public void onSettingsOpened() {
        Timber.i("[ACCION] Ir a ajustes para conceder el permiso de Bluetooth");
        state.setValue(new PrintLabelState.WaitingForSettings());
    }

    /** "Ahora no": se explica por qué no se puede imprimir y se deja volver a intentarlo. */
    @MainThread
    public void onSettingsDeclined() {
        Timber.i("[ACCION] No ir a ajustes para conceder el permiso de Bluetooth");
        state.setValue(new PrintLabelState.PermissionDenied());
    }

    /** Al volver de ajustes: si activó el permiso se continúa sin que tenga que pulsar nada. */
    @MainThread
    public void onReturnedFromSettings(boolean granted) {
        if (!(state.getValue() instanceof PrintLabelState.WaitingForSettings)) {
            return;
        }
        Timber.i("[ESTADO] Vuelta de ajustes (permiso de Bluetooth concedido: %b)", granted);
        if (granted) {
            checkBluetooth();
        } else {
            state.setValue(new PrintLabelState.PermissionDenied());
        }
    }

    @MainThread
    public void onEnableBluetoothClicked() {
        if (state.getValue() instanceof PrintLabelState.BluetoothOff) {
            Timber.i("[ACCION] Activar Bluetooth");
            state.setValue(new PrintLabelState.EnableBluetooth());
        }
    }

    @MainThread
    public void onEnableBluetoothLaunched() {
        if (state.getValue() instanceof PrintLabelState.EnableBluetooth) {
            state.setValue(new PrintLabelState.Busy());
        }
    }

    /**
     * El diálogo del sistema se ha cerrado. Se comprueba el Bluetooth en lugar de fiarse del
     * resultado: el usuario puede haberlo activado por otra vía.
     */
    @MainThread
    public void onEnableBluetoothResult() {
        checkBluetooth();
    }

    /** Vuelve a buscar impresoras cercanas, conservando las ya encontradas. */
    @MainThread
    public void onSearchAgainClicked() {
        if (state.getValue() instanceof PrintLabelState.Choosing && !searching) {
            Timber.i("[ACCION] Buscar impresoras de nuevo");
            startDiscovery();
        }
    }

    /** Genera la etiqueta y la envía a {@code printer}. Se ignora si ya se está imprimiendo. */
    @MainThread
    public void onPrinterSelected(@NonNull Printer printer) {
        if (!(state.getValue() instanceof PrintLabelState.Choosing)) {
            Timber.d("[ACCION] Impresora %s pulsada mientras ya se imprime: se ignora", printer.address());
            return;
        }
        Timber.i("[ACCION] Impresora elegida: %s (%s, emparejada: %b)",
                printer.name(), printer.address(), printer.paired());
        stopDiscovery();
        state.setValue(new PrintLabelState.Printing(printer, false));
        pendingRequest = labelRepository.createZplLabel(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull ZplLabel label) {
                send(printer, label.zpl());
            }

            @Override
            public void onError(@NonNull AppError error) {
                onPrintFailed(error);
            }
        });
    }

    @Override
    protected void onCleared() {
        stopDiscovery();
        if (pendingRequest != null) {
            pendingRequest.cancel();
            pendingRequest = null;
        }
    }

    private void checkBluetooth() {
        if (!printerRepository.isBluetoothSupported()) {
            Timber.w("[ESTADO] El terminal no tiene Bluetooth: no se puede imprimir");
            state.setValue(new PrintLabelState.BluetoothUnsupported());
        } else if (!printerRepository.isBluetoothEnabled()) {
            Timber.i("[ESTADO] Bluetooth desactivado");
            state.setValue(new PrintLabelState.BluetoothOff());
        } else {
            printers.clear();
            printers.addAll(printerRepository.getPairedPrinters());
            Timber.i("[ESTADO] Impresoras emparejadas: %d", printers.size());
            startDiscovery();
        }
    }

    private void startDiscovery() {
        if (!printerRepository.canDiscoverPrinters()) {
            Timber.i("[ESTADO] Sin ubicación activa: no se buscan impresoras cercanas");
            showChoosing(new UiText.Res(R.string.print_location_off));
            return;
        }
        searching = true;
        showChoosing(null);
        discovery = printerRepository.discoverPrinters(new BluetoothPrinterGateway.DiscoveryListener() {
            @Override
            public void onPrinterFound(@NonNull Printer printer) {
                if (addIfNew(printer) && state.getValue() instanceof PrintLabelState.Choosing choosing) {
                    showChoosing(choosing.notice());
                }
            }

            @Override
            public void onDiscoveryFinished() {
                Timber.i("[ESTADO] Búsqueda de impresoras terminada: %d en la lista", printers.size());
                searching = false;
                discovery = null;
                if (state.getValue() instanceof PrintLabelState.Choosing choosing) {
                    showChoosing(choosing.notice());
                }
            }
        });
    }

    private void stopDiscovery() {
        searching = false;
        if (discovery != null) {
            discovery.cancel();
            discovery = null;
        }
    }

    /** La misma impresora puede anunciarse varias veces en una búsqueda o estar ya emparejada. */
    private boolean addIfNew(@NonNull Printer found) {
        for (Printer printer : printers) {
            if (printer.address().equals(found.address())) {
                return false;
            }
        }
        printers.add(found);
        printers.sort((a, b) -> Boolean.compare(b.likelyPrinter(), a.likelyPrinter()));
        return true;
    }

    private void send(@NonNull Printer printer, @NonNull String zpl) {
        state.setValue(new PrintLabelState.Printing(printer, true));
        pendingRequest = printerRepository.print(printer, zpl, new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull Boolean result) {
                pendingRequest = null;
                state.setValue(new PrintLabelState.Printed(printer));
            }

            @Override
            public void onError(@NonNull AppError error) {
                onPrintFailed(error);
            }
        });
    }

    /** Se vuelve a la lista con el motivo: tocar la impresora de nuevo reintenta. */
    private void onPrintFailed(@NonNull AppError error) {
        Timber.i("[ESTADO] Impresión fallida (%s): se vuelve a la lista de impresoras", error);
        pendingRequest = null;
        showChoosing(ErrorUiMapper.toUiText(error));
    }

    private void showChoosing(@Nullable UiText notice) {
        state.setValue(new PrintLabelState.Choosing(printers, searching, notice));
    }
}
