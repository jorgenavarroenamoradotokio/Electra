package com.redur.electra.core.permission;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.redur.electra.R;

import java.util.Map;

/**
 * Permisos de sistema que la app pide en tiempo de ejecución, con los textos para recordar al
 * usuario cómo activarlos desde ajustes si los ha denegado para siempre. Para pedir uno nuevo
 * basta con añadirlo aquí (y declararlo en el manifest).
 */
public enum AppPermission {

    CAMERA(R.drawable.ic_photo_camera_24,
            R.string.permission_camera_blocked_title, R.string.permission_camera_blocked_message),

    /** Fotos del dispositivo. Desde Android 14 también vale el acceso parcial ("Seleccionar fotos"). */
    GALLERY(R.drawable.ic_photo_library_24,
            R.string.permission_gallery_blocked_title, R.string.permission_gallery_blocked_message);

    @DrawableRes
    private final int icon;
    @StringRes
    private final int blockedTitle;
    @StringRes
    private final int blockedMessage;

    AppPermission(@DrawableRes int icon, @StringRes int blockedTitle, @StringRes int blockedMessage) {
        this.icon = icon;
        this.blockedTitle = blockedTitle;
        this.blockedMessage = blockedMessage;
    }

    @DrawableRes
    public int icon() {
        return icon;
    }

    @StringRes
    public int blockedTitle() {
        return blockedTitle;
    }

    @StringRes
    public int blockedMessage() {
        return blockedMessage;
    }

    /** Permisos del manifest que se piden en el dispositivo actual. */
    @NonNull
    public String[] manifestPermissions() {
        return manifestPermissions(Build.VERSION.SDK_INT);
    }

    /**
     * Permisos del manifest que corresponden a una versión de Android. Los nombres son constantes
     * de texto y cada uno solo se devuelve en las versiones que lo definen.
     */
    @SuppressLint("InlinedApi")
    @NonNull
    public String[] manifestPermissions(int sdkInt) {
        return switch (this) {
            case CAMERA -> new String[]{Manifest.permission.CAMERA};
            case GALLERY -> {
                if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    yield new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
                }
                if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                    yield new String[]{Manifest.permission.READ_MEDIA_IMAGES};
                }
                yield new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
            }
        };
    }

    /**
     * Resultado de una petición: basta con que se conceda uno de sus permisos (en la galería, el
     * acceso parcial a fotos concretas permite igualmente elegir imagen).
     */
    public boolean isGrantedIn(@NonNull Map<String, Boolean> results) {
        for (String permission : manifestPermissions()) {
            if (Boolean.TRUE.equals(results.get(permission))) {
                return true;
            }
        }
        return false;
    }

    /** Permiso al que pertenece el resultado de una petición, o null si no es de ninguno. */
    @Nullable
    public static AppPermission fromResults(@NonNull Map<String, Boolean> results) {
        for (AppPermission permission : values()) {
            for (String manifestPermission : permission.manifestPermissions()) {
                if (results.containsKey(manifestPermission)) {
                    return permission;
                }
            }
        }
        return null;
    }
}
