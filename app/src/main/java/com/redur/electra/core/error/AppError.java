package com.redur.electra.core.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Error de una operación, independiente de cómo se muestre. Lo crea la capa de datos
 * (Repository) y la UI lo traduce a texto con {@code ErrorUiMapper}.
 * Solo se añaden subtipos nuevos si la UI debe reaccionar de forma distinta.
 */
public sealed interface AppError permits AppError.Network, AppError.Api, AppError.Printer {

    /** Fallo de comunicación: no se llegó a obtener respuesta del servidor. */
    record Network(@NonNull NetworkType type) implements AppError {
        public Network {
            Objects.requireNonNull(type, "type");
        }
    }

    /** El servidor respondió con un error de negocio identificado por {@code code}. */
    record Api(@Nullable String code, @Nullable String serverMessage) implements AppError {
    }

    /** No se pudo imprimir: la impresora no respondió o la conexión Bluetooth falló. */
    record Printer(@NonNull PrinterFailure failure) implements AppError {
        public Printer {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
