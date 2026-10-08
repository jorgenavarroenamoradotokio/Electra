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
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.redur.electra.BuildConfig;
import com.redur.electra.R;
import com.redur.electra.core.permission.PermissionRequester;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.databinding.SheetPhotoSourceBinding;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;

import java.io.File;
import java.text.NumberFormat;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Hacer una foto o elegir una imagen de la galería, como hoja inferior. Pide el permiso del origen
 * elegido (el sistema ofrece "Mientras se usa la app", "Solo esta vez" o "No permitir"); si está
 * denegado para siempre, ofrece ir a ajustes. Con la imagen lista la envía al backend mostrando el
 * progreso; si falla, permite reenviarla o elegir otra. Una vez enviada publica {@link #RESULT_KEY}
 * con su content:// en {@link #RESULT_IMAGE_URI} y se cierra.
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
        views.buttonRetryUpload.setOnClickListener(v -> viewModel.onRetryUploadClicked());
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
        boolean canChoose = state.canChooseSource();
        views.buttonTakePhoto.setEnabled(canChoose);
        views.buttonPickImage.setEnabled(canChoose);

        // El aviso del intento anterior deja de ser cierto en cuanto empieza otro
        renderNotice(noticeOf(state));
        views.buttonRetryUpload.setVisibility(state instanceof PhotoSourceState.UploadFailed ? View.VISIBLE : View.GONE);
        renderUploadProgress(state);

        if (state instanceof PhotoSourceState.LaunchCamera launch) {
            launchCamera(launch.output());
        } else if (state instanceof PhotoSourceState.LaunchGallery) {
            launchGallery();
        } else if (state instanceof PhotoSourceState.PermissionBlocked blocked) {
            Timber.i("El permiso esta bloqueado mostramos pantalla para que seleccione ir a ajuste el usuario");
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), blocked.permission());
            viewModel.onSettingsPromptShown();
        } else if (state instanceof PhotoSourceState.Uploaded uploaded) {
            Bundle result = new Bundle();
            result.putString(RESULT_IMAGE_URI, uploaded.imageUri());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            dismiss();
        }
    }

    @Nullable
    private static UiText noticeOf(@NonNull PhotoSourceState state) {
        if (state instanceof PhotoSourceState.Ready ready) {
            return ready.notice();
        }
        if (state instanceof PhotoSourceState.UploadFailed failed) {
            return failed.message();
        }
        return null;
    }

    /** Al 100 % la barra pasa a indeterminada mientras responde el servidor. */
    private void renderUploadProgress(@NonNull PhotoSourceState state) {
        SheetPhotoSourceBinding views = requireBinding();
        if (!(state instanceof PhotoSourceState.Uploading uploading)) {
            views.layoutUploadProgress.setVisibility(View.GONE);
            return;
        }
        LinearProgressIndicator progress = views.progressUpload;
        boolean confirming = uploading.percent() >= PhotoSourceState.Uploading.COMPLETE;
        views.textUploadProgress.setText(confirming
                ? getString(R.string.photo_upload_confirming)
                : getString(R.string.photo_upload_sending,
                        NumberFormat.getPercentInstance().format(uploading.percent() / 100.0)));
        if (confirming) {
            progress.setIndeterminate(true);
        } else {
            // Solo se anima el avance de una barra que ya mostraba progreso: al aparecer, al
            // recrearse la vista o al reintentar, salta directamente al valor
            boolean animate = views.layoutUploadProgress.getVisibility() == View.VISIBLE
                    && !progress.isIndeterminate();
            progress.setIndeterminate(false);
            progress.setProgressCompat(uploading.percent(), animate);
        }
        views.layoutUploadProgress.setVisibility(View.VISIBLE);
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
            Timber.i("Abrimos la camara del terminal");
            Uri uri = FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX, output);
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
            Timber.i("Abrimos la galeria del terminal");
            viewModel.onGalleryOpened();
            pickImage.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
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
