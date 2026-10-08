package com.redur.electra.ui.permission;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.core.permission.AppPermission;
import com.redur.electra.databinding.SheetPermissionSettingsBinding;

import timber.log.Timber;

/**
 * Recuerda al usuario que denegó un permiso para siempre que sin él no puede continuar, y le
 * lleva a los ajustes de la app para activarlo. Publica {@link #RESULT_KEY} con
 * {@link #RESULT_OPENED_SETTINGS}: true si fue a ajustes (quien la abre puede comprobar el permiso
 * al volver), false si la descartó.
 */
public class PermissionSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "PermissionSettingsBottomSheet.result";
    public static final String RESULT_OPENED_SETTINGS = "openedSettings";

    private static final String TAG = "PermissionSettingsBottomSheet";
    private static final String ARG_PERMISSION = "permission";
    private static final String PACKAGE_SCHEME = "package";

    @Nullable
    private SheetPermissionSettingsBinding binding;
    /** Evita publicar dos resultados (p. ej. pulsar un botón y que después se cancele la hoja). */
    private boolean resultSent;

    /** No apila una segunda hoja si ya hay una visible. */
    public static void showIfNotShown(@NonNull FragmentManager fragmentManager, @NonNull AppPermission permission) {
        Timber.i("Abrimos la pantalla de solicitud de permisos de %s", permission.name());
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            PermissionSettingsBottomSheet sheet = new PermissionSettingsBottomSheet();
            Bundle args = new Bundle();
            args.putString(ARG_PERMISSION, permission.name());
            sheet.setArguments(args);
            sheet.show(fragmentManager, TAG);

        }
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetPermissionSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppPermission permission = AppPermission.valueOf(requireArguments().getString(ARG_PERMISSION));
        SheetPermissionSettingsBinding views = requireBinding();
        views.imagePermission.setImageResource(permission.icon());
        views.textPermissionTitle.setText(permission.blockedTitle());
        views.textPermissionMessage.setText(permission.blockedMessage());
        views.buttonOpenSettings.setOnClickListener(v -> openAppSettings());
        views.buttonNotNow.setOnClickListener(v -> {
            Timber.w("El usuario ha cancelado el dar permisos de %s", permission.name());
            sendResult(false);
            dismiss();
        });
    }

    /** Descartada deslizando, con "atrás" o tocando fuera. */
    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendResult(false);
    }

    /** Ficha de la app en ajustes; si el fabricante no la ofrece, los ajustes generales. */
    private void openAppSettings() {
        Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts(PACKAGE_SCHEME, requireContext().getPackageName(), null));
        try {
            Timber.i("Mostramos el activar o no el permisos desde ajustes del terminal");
            startActivity(appDetails);
        } catch (ActivityNotFoundException e) {
            Timber.w(e, "Sin pantalla de ajustes de la app; se abren los ajustes generales");
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
        sendResult(true);
        dismiss();
    }

    private void sendResult(boolean openedSettings) {
        if (resultSent) {
            return;
        }
        resultSent = true;
        Bundle result = new Bundle();
        result.putBoolean(RESULT_OPENED_SETTINGS, openedSettings);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    }

    @NonNull
    private SheetPermissionSettingsBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la hoja no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
