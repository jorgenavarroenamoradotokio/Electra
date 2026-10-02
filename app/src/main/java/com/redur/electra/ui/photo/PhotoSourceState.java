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
        PhotoSourceState.Picked {

    /** Se puede elegir origen. {@code notice} explica por qué no se pudo completar el intento anterior. */
    record Ready(@Nullable UiText notice) implements PhotoSourceState {
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

    /** Imagen lista; {@code imageUri} es un content:// legible por la app. */
    record Picked(@NonNull String imageUri) implements PhotoSourceState {
        public Picked {
            Objects.requireNonNull(imageUri, "imageUri");
        }
    }
}
