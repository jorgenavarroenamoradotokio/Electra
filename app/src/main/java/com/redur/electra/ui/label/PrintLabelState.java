package com.redur.electra.ui.label;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.printer.Printer;

import java.util.List;
import java.util.Objects;

/**
 * Estado de la hoja de impresión. {@link RequestPermission}, {@link PermissionBlocked} y
 * {@link EnableBluetooth} son órdenes de un solo uso: la UI las ejecuta y avisa al ViewModel para
 * que no se repitan al recrear la vista.
 */
public sealed interface PrintLabelState permits
        PrintLabelState.RequestPermission,
        PrintLabelState.Busy,
        PrintLabelState.PermissionDenied,
        PrintLabelState.PermissionBlocked,
        PrintLabelState.WaitingForSettings,
        PrintLabelState.BluetoothUnsupported,
        PrintLabelState.BluetoothOff,
        PrintLabelState.EnableBluetooth,
        PrintLabelState.Choosing,
        PrintLabelState.Printing,
        PrintLabelState.Printed {

    /** Hay que pedir el permiso de Bluetooth. */
    record RequestPermission() implements PrintLabelState {
    }

    /** Esperando al sistema (diálogo de permiso o de activar Bluetooth). */
    record Busy() implements PrintLabelState {
    }

    /** Denegado, pero se puede volver a pedir. */
    record PermissionDenied() implements PrintLabelState {
    }

    /** Denegado para siempre: hay que ofrecer ir a ajustes. */
    record PermissionBlocked() implements PrintLabelState {
    }

    /** El usuario ha ido a ajustes: al volver se comprueba si activó el permiso. */
    record WaitingForSettings() implements PrintLabelState {
    }

    record BluetoothUnsupported() implements PrintLabelState {
    }

    /** El Bluetooth está apagado: se ofrece activarlo. */
    record BluetoothOff() implements PrintLabelState {
    }

    /** Pedir al sistema que active el Bluetooth. */
    record EnableBluetooth() implements PrintLabelState {
    }

    /**
     * Eligiendo impresora. {@code searching}: buscando impresoras nuevas. {@code notice} explica por
     * qué falló el último intento o por qué la búsqueda no puede encontrar nada.
     */
    record Choosing(@NonNull List<Printer> printers, boolean searching, @Nullable UiText notice)
            implements PrintLabelState {
        public Choosing {
            printers = List.copyOf(printers);
        }
    }

    /** Imprimiendo en {@code printer}: generando la etiqueta o, con {@code sending}, enviándola. */
    record Printing(@NonNull Printer printer, boolean sending) implements PrintLabelState {
        public Printing {
            Objects.requireNonNull(printer, "printer");
        }
    }

    /** La impresora ha recibido la etiqueta. */
    record Printed(@NonNull Printer printer) implements PrintLabelState {
        public Printed {
            Objects.requireNonNull(printer, "printer");
        }
    }
}
