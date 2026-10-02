package com.redur.electra.ui.photo;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.BuildConfig;
import com.redur.electra.core.permission.PermissionRequester;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.databinding.SheetPhotoSourceBinding;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Hacer una foto o elegir una imagen de la galería, como hoja inferior. Pide el permiso del origen
 * elegido (el sistema ofrece "Mientras se usa la app", "Solo esta vez" o "No permitir"); si está
 * denegado para siempre, ofrece ir a ajustes. Con la imagen lista publica {@link #RESULT_KEY} con
 * su content:// en {@link #RESULT_IMAGE_URI} y se cierra.
 */
@AndroidEntryPoint
public class PhotoSourceBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "PhotoSourceBottomSheet.result";
    public static final String RESULT_IMAGE_URI = "imageUri";

    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";

    // Se asigna en onCreate; los resultados de abajo siempre llegan después
    private PhotoSourceViewModel viewModel;

    // Se registran al construir el Fragment: así el resultado llega aunque se recree mientras
    // está abierto el diálogo de permisos, la cámara o la galería
    private final PermissionRequester permissionRequester =
            new PermissionRequester(this, status -> viewModel.onPermissionResult(status));
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), saved -> viewModel.onCameraClosed());
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> viewModel.onGalleryResult(uri != null ? uri.toString() : null));

    @Nullable
    private SheetPhotoSourceBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PhotoSourceViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetPhotoSourceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetPhotoSourceBinding views = requireBinding();
        views.buttonTakePhoto.setOnClickListener(v -> onSourceClicked(PhotoSource.CAMERA));
        views.buttonPickImage.setOnClickListener(v -> onSourceClicked(PhotoSource.GALLERY));
        views.buttonCancelPhoto.setOnClickListener(v -> dismiss());

        getChildFragmentManager().setFragmentResultListener(PermissionSettingsBottomSheet.RESULT_KEY,
                getViewLifecycleOwner(), (key, result) -> {
                    if (result.getBoolean(PermissionSettingsBottomSheet.RESULT_OPENED_SETTINGS)) {
                        viewModel.onSettingsOpened();
                    } else {
                        viewModel.onSettingsDeclined();
                    }
                });
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    /** Al volver de ajustes se comprueba si el usuario activó el permiso. */
    @Override
    public void onResume() {
        super.onResume();
        if (viewModel.getState().getValue() instanceof PhotoSourceState.WaitingForSettings waiting) {
            viewModel.onReturnedFromSettings(PermissionRequester.isGranted(requireContext(), waiting.permission()));
        }
    }

    private void onSourceClicked(@NonNull PhotoSource source) {
        if (viewModel.onSourceSelected(source)) {
            permissionRequester.request(source.permission());
        }
    }

    private void render(@NonNull PhotoSourceState state) {
        SheetPhotoSourceBinding views = requireBinding();
        boolean ready = state instanceof PhotoSourceState.Ready;
        views.buttonTakePhoto.setEnabled(ready);
        views.buttonPickImage.setEnabled(ready);

        // El aviso del intento anterior deja de ser cierto en cuanto empieza otro
        renderNotice(state instanceof PhotoSourceState.Ready readyState ? readyState.notice() : null);

        if (state instanceof PhotoSourceState.LaunchCamera launch) {
            launchCamera(launch.output());
        } else if (state instanceof PhotoSourceState.LaunchGallery) {
            launchGallery();
        } else if (state instanceof PhotoSourceState.PermissionBlocked blocked) {
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), blocked.permission());
            viewModel.onSettingsPromptShown();
        } else if (state instanceof PhotoSourceState.Picked picked) {
            Bundle result = new Bundle();
            result.putString(RESULT_IMAGE_URI, picked.imageUri());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            dismiss();
        }
    }

    /** El aviso se mantiene hasta el siguiente intento: explica qué ha pasado y qué hacer. */
    private void renderNotice(@Nullable UiText notice) {
        SheetPhotoSourceBinding views = requireBinding();
        if (notice != null) {
            views.textPhotoNotice.setText(notice.resolve(requireContext()));
            views.cardPhotoNotice.setVisibility(View.VISIBLE);
        } else {
            views.cardPhotoNotice.setVisibility(View.GONE);
        }
    }

    private void launchCamera(@NonNull File output) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX, output);
            viewModel.onCameraOpened(uri.toString());
            takePicture.launch(uri);
        } catch (IllegalArgumentException e) {
            // La carpeta de fotos no está declarada en file_paths.xml
            Timber.e(e, "No se puede compartir el fichero de la foto con la cámara");
            viewModel.onSourceOpenFailed();
        } catch (ActivityNotFoundException e) {
            Timber.w(e, "No hay app de cámara");
            viewModel.onSourceOpenFailed();
        }
    }

    private void launchGallery() {
        try {
            viewModel.onGalleryOpened();
            pickImage.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (ActivityNotFoundException e) {
            Timber.w(e, "No hay app de galería");
            viewModel.onSourceOpenFailed();
        }
    }

    @NonNull
    private SheetPhotoSourceBinding requireBinding() {
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
