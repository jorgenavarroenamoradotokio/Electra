package com.redur.electra.ui.photo;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.redur.electra.R;
import com.redur.electra.core.permission.AppPermission;

/** De dónde sale la imagen, el permiso que necesita y qué se dice si no se puede usar. */
public enum PhotoSource {

    CAMERA(AppPermission.CAMERA, R.string.photo_camera_denied, R.string.photo_camera_open_failed),
    GALLERY(AppPermission.GALLERY, R.string.photo_gallery_denied, R.string.photo_gallery_open_failed);

    @NonNull
    private final AppPermission permission;
    @StringRes
    private final int deniedMessage;
    @StringRes
    private final int openFailedMessage;

    PhotoSource(@NonNull AppPermission permission, @StringRes int deniedMessage, @StringRes int openFailedMessage) {
        this.permission = permission;
        this.deniedMessage = deniedMessage;
        this.openFailedMessage = openFailedMessage;
    }

    @NonNull
    public AppPermission permission() {
        return permission;
    }

    /** Denegado una vez: se puede volver a pedir. */
    @StringRes
    public int deniedMessage() {
        return deniedMessage;
    }

    /** No hay app de cámara/galería que atienda la petición. */
    @StringRes
    public int openFailedMessage() {
        return openFailedMessage;
    }
}
