package com.redur.electra.ui.photo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.permission.AppPermission;
import com.redur.electra.core.ui.UiText;

import java.io.File;
import java.util.Objects;

/**
 * Estado de la hoja de foto. {@link LaunchCamera}, {@link LaunchGallery} y
 * {@link PermissionBlocked} son órdenes de un solo uso: la UI las ejecuta y avisa al ViewModel
 * (p. ej. {@code onCameraOpened}) para que no se repitan al recrear la vista.
 */
public sealed interface PhotoSourceState permits
        PhotoSourceState.Ready,
        PhotoSourceState.Busy,
        PhotoSourceState.LaunchCamera,
        PhotoSourceState.LaunchGallery,
        PhotoSourceState.PermissionBlocked,
        PhotoSourceState.WaitingForSettings,
        PhotoSourceState.Uploading,
        PhotoSourceState.UploadFailed,
        PhotoSourceState.Uploaded {

    /** Si se puede elegir cámara o galería en este estado. */
    default boolean canChooseSource() {
        return false;
    }

    /** Se puede elegir origen. {@code notice} explica por qué no se pudo completar el intento anterior. */
    record Ready(@Nullable UiText notice) implements PhotoSourceState {
        @Override
        public boolean canChooseSource() {
            return true;
        }
    }

    /** Pidiendo permiso, preparando la captura o con la cámara/galería abierta. */
    record Busy() implements PhotoSourceState {
    }

    /** Abrir la cámara para que guarde la foto en {@code output}. */
    record LaunchCamera(@NonNull File output) implements PhotoSourceState {
        public LaunchCamera {
            Objects.requireNonNull(output, "output");
        }
    }

    record LaunchGallery() implements PhotoSourceState {
    }

    /** Permiso denegado para siempre: hay que ofrecer ir a ajustes. */
    record PermissionBlocked(@NonNull AppPermission permission) implements PhotoSourceState {
        public PermissionBlocked {
            Objects.requireNonNull(permission, "permission");
        }
    }

    /** El usuario ha ido a ajustes: al volver se comprueba si ya activó el permiso. */
    record WaitingForSettings(@NonNull AppPermission permission) implements PhotoSourceState {
        public WaitingForSettings {
            Objects.requireNonNull(permission, "permission");
        }
    }

    /** Enviando la imagen: {@code percent} de 0 a 100; en 100 los datos ya salieron y se espera al servidor. */
    record Uploading(int percent) implements PhotoSourceState {
        public static final int COMPLETE = 100;
    }

    /**
     * No se pudo enviar la imagen. Se puede reintentar con la misma imagen o elegir otro origen;
     * {@code message} explica qué ha pasado.
     */
    record UploadFailed(@NonNull UiText message) implements PhotoSourceState {
        public UploadFailed {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public boolean canChooseSource() {
            return true;
        }
    }

    /** Imagen enviada; {@code imageUri} es el content:// de la imagen, legible por la app. */
    record Uploaded(@NonNull String imageUri) implements PhotoSourceState {
        public Uploaded {
            Objects.requireNonNull(imageUri, "imageUri");
        }
    }
}
