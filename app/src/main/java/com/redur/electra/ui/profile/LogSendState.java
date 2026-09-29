package com.redur.electra.ui.profile;

import androidx.annotation.NonNull;

import com.redur.electra.core.ui.UiText;

import java.io.File;
import java.util.Objects;

/**
 * Envío del registro de actividad: Idle → Sending → Sent | Failed. Tras fallar, el usuario puede
 * reintentar o, si ya falló varias veces, pasar a ReadyToEmail para enviarlo por correo.
 * Sent, Failed y ReadyToEmail son de un solo uso: la UI los atiende y vuelve a Idle.
 */
public sealed interface LogSendState permits LogSendState.Idle, LogSendState.Sending,
        LogSendState.Sent, LogSendState.Failed, LogSendState.ReadyToEmail {

    /** Qué ofrece la UI al usuario tras un fallo. */
    enum Recovery {
        /** El fallo no se resuelve reintentando (p. ej. no hay registro que enviar). */
        NONE,
        RETRY,
        SEND_BY_EMAIL
    }

    record Idle() implements LogSendState {
    }

    /** {@code percent} de 0 a 100; en 100 los datos ya salieron y se espera al servidor. */
    record Sending(int percent) implements LogSendState {
        public static final int COMPLETE = 100;
    }

    record Sent() implements LogSendState {
    }

    record Failed(@NonNull UiText message, @NonNull Recovery recovery) implements LogSendState {
        public Failed {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(recovery, "recovery");
        }
    }

    record ReadyToEmail(@NonNull File file) implements LogSendState {
        public ReadyToEmail {
            Objects.requireNonNull(file, "file");
        }
    }
}
