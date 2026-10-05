package com.redur.electra.core.permission;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Map;

/**
 * Pide un {@link AppPermission} desde un Fragment y devuelve la decisión del usuario ya
 * interpretada: concedido ("Mientras se usa la app" / "Solo esta vez"), denegado o denegado para
 * siempre (entonces solo queda llevarle a ajustes, ver {@code PermissionSettingsBottomSheet}).
 * <p>
 * Debe crearse antes de que el Fragment llegue a CREATED (como campo o en {@code onCreate}): así
 * el resultado llega también si el Fragment se recrea mientras el diálogo del sistema está abierto.
 */
public final class PermissionRequester {

    @FunctionalInterface
    public interface Callback {
        void onPermissionResult(@NonNull PermissionStatus status);
    }

    @NonNull
    private final Fragment fragment;
    @NonNull
    private final Callback callback;
    @NonNull
    private final ActivityResultLauncher<String[]> launcher;

    public PermissionRequester(@NonNull Fragment fragment, @NonNull Callback callback) {
        this.fragment = fragment;
        this.callback = callback;
        this.launcher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> callback.onPermissionResult(resolve(results)));
    }

    /** Comprueba el permiso sin preguntar al usuario. */
    public static boolean isGranted(@NonNull Context context, @NonNull AppPermission permission) {
        return permission.isGranted(manifestPermission ->
                ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Si ya está concedido responde al momento; si no, muestra el diálogo del sistema. Si el
     * usuario lo denegó para siempre, el sistema responde sin mostrarlo y se informa
     * {@link PermissionStatus#PERMANENTLY_DENIED}.
     */
    public void request(@NonNull AppPermission permission) {
        if (isGranted(fragment.requireContext(), permission)) {
            callback.onPermissionResult(PermissionStatus.GRANTED);
        } else {
            launcher.launch(permission.manifestPermissions());
        }
    }

    /**
     * Sin resultados (la petición se interrumpió) se trata como una denegación normal: se puede
     * volver a preguntar.
     */
    @NonNull
    private PermissionStatus resolve(@NonNull Map<String, Boolean> results) {
        AppPermission permission = AppPermission.fromResults(results);
        if (permission == null) {
            return PermissionStatus.DENIED;
        }
        return PermissionStatus.from(permission.isGrantedIn(results), canAskAgain(permission));
    }

    private boolean canAskAgain(@NonNull AppPermission permission) {
        for (String manifestPermission : permission.manifestPermissions()) {
            if (fragment.shouldShowRequestPermissionRationale(manifestPermission)) {
                return true;
            }
        }
        return false;
    }
}
