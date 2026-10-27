package com.redur.electra.ui.bulto.incidencia;

import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import com.redur.electra.R;
import com.redur.electra.core.permission.AppPermission;
import com.redur.electra.core.permission.PermissionRequester;
import com.redur.electra.core.permission.PermissionStatus;
import com.redur.electra.core.scanner.HardwareScannerManager;
import com.redur.electra.core.scanner.ScannerManagerFactory;
import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.model.bulto.OperationType;
import com.redur.electra.databinding.FragmentBultoIncidenceBinding;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;
import com.redur.electra.ui.photo.PhotoSourceBottomSheet;

import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Incidencia del último bulto leído: se elige una de las del tipo de operación y, si la incidencia
 * lo exige, se escriben observaciones y se hace una foto con la hoja de foto. De momento, como en
 * el tipo de bulto, aceptar y cancelar solo lo confirman con un aviso y dejan la pantalla lista
 * para otra incidencia.
 * <p>
 * El bulto se lee con el escáner hardware, por broadcast (Honeywell, Datalogic) o tecleándolo en un
 * campo invisible con Intro al final. Si el terminal no tiene escáner compatible, un botón junto al
 * bulto lo lee con la cámara.
 */
@AndroidEntryPoint
public class BultoIncidenceFragment extends Fragment {

    private static final String PHOTO_SHEET_TAG = "BultoIncidenceFragment.photo";

    @Inject
    ScannerManagerFactory scannerManagerFactory;

    // Se registran al construir el Fragment: así el resultado llega aunque se recree mientras
    // está abierto el diálogo de permisos o la cámara
    private final PermissionRequester cameraPermissionRequester =
            new PermissionRequester(this, this::onCameraPermissionResult);
    private final ActivityResultLauncher<ScanOptions> cameraScan =
            registerForActivityResult(new ScanContract(), this::onCameraScanResult);

    @Nullable
    private FragmentBultoIncidenceBinding binding;
    private BultoIncidenceViewModel viewModel;
    @Nullable
    private BultoIncidenceAdapter adapter;
    /** Bulto ya pintado en esta vista: solo se anima la llegada de uno nuevo, no cada re-renderizado. */
    @Nullable
    private String renderedBarcode;
    private boolean barcodeRendered;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoIncidenceViewModel.class);
        // La hoja de foto publica su resultado en este FragmentManager; se escucha desde onCreate
        // para recibirlo aunque llegue mientras se recrea la vista (p. ej. al volver de la cámara)
        getChildFragmentManager().setFragmentResultListener(PhotoSourceBottomSheet.RESULT_KEY, this,
                (key, result) -> {
                    String photoUri = result.getString(PhotoSourceBottomSheet.RESULT_IMAGE_URI);
                    if (photoUri != null) {
                        viewModel.onPhotoAttached(photoUri);
                    }
                });
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoIncidenceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoIncidenceBinding views = requireBinding();
        views.textBultoIncidenceOperation.setText(titleOf(viewModel.getArgs().operationType()));

        // Campo invisible que recibe el CB del escáner por teclado: tiene el foco sin abrir el teclado en pantalla
        views.inputBultoIncidenceBarcode.setShowSoftInputOnFocus(false);
        views.inputBultoIncidenceBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, this::onBarcodeTyped));

        // Solo entrega lecturas con la vista en RESUMED; el lifecycle de la vista lo libera
        HardwareScannerManager scanner = scannerManagerFactory.create(viewModel::onBarcodeScanned);
        getViewLifecycleOwner().getLifecycle().addObserver(scanner);
        if (!scanner.isAvailable()) {
            views.buttonBultoIncidenceScanCamera.setVisibility(View.VISIBLE);
            views.buttonBultoIncidenceScanCamera.setOnClickListener(v -> onScanCameraClicked());
        }

        views.layoutBultoIncidenceObservations.setCounterMaxLength(BultoIncidencia.OBSERVATIONS_MAX_LENGTH);
        views.inputBultoIncidenceObservations.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(BultoIncidencia.OBSERVATIONS_MAX_LENGTH)});

        adapter = new BultoIncidenceAdapter(viewModel::onIncidenceSelected);
        views.recyclerBultoIncidences.setAdapter(adapter);

        views.buttonBultoIncidencesRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        views.buttonBultoIncidencePhoto.setOnClickListener(v -> showPhotoSheet());
        views.buttonBultoIncidenceConfirm.setOnClickListener(v -> confirm());
        views.buttonBultoIncidenceCancel.setOnClickListener(v -> cancel());

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getBarcode().observe(getViewLifecycleOwner(), this::renderBarcode);
        viewModel.getSelectedIncidence().observe(getViewLifecycleOwner(), this::renderSelection);
        viewModel.getPhotoUri().observe(getViewLifecycleOwner(), photoUri -> renderPhoto());
        viewModel.getFormState().observe(getViewLifecycleOwner(), this::renderForm);
        if (savedInstanceState == null) {
            views.inputBultoIncidenceBarcode.requestFocus();
        }
    }

    /**
     * Las observaciones se escuchan una vez restaurado lo tecleado: restaurarlo no cuenta como una
     * corrección y no debe borrar el error que ya se mostraba.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        requireBinding().inputBultoIncidenceObservations.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onObservationsChanged();
            }
        });
    }

    private void confirm() {
        BultoIncidenceEntry entry = viewModel.onConfirmClicked(observationsText());
        if (entry == null) {
            BultoIncidenceFormState form = viewModel.getFormState().getValue();
            if (form != null && form.observationsError() != null) {
                requireBinding().inputBultoIncidenceObservations.requestFocus();
            }
            return;
        }
        requireBinding().inputBultoIncidenceObservations.setText(null);
        showToast(R.string.bulto_incidence_saved);
        waitForReading();
    }

    private void cancel() {
        viewModel.onCancelClicked();
        requireBinding().inputBultoIncidenceObservations.setText(null);
        showToast(R.string.bulto_incidence_canceled);
        waitForReading();
    }

    /** El foco vuelve al campo del escáner: la lectura siguiente no acaba en las observaciones. */
    private void waitForReading() {
        requireBinding().inputBultoIncidenceBarcode.requestFocus();
    }

    /** CB tecleado por el escáner en el campo invisible; se vacía para la lectura siguiente. */
    private void onBarcodeTyped() {
        TextView input = requireBinding().inputBultoIncidenceBarcode;
        CharSequence text = input.getText();
        viewModel.onBarcodeScanned(text != null ? text.toString() : null);
        input.setText(null);
    }

    /** Se desactiva hasta cerrar la cámara o resolver el permiso: evita abrirla dos veces. */
    private void onScanCameraClicked() {
        requireBinding().buttonBultoIncidenceScanCamera.setEnabled(false);
        cameraPermissionRequester.request(AppPermission.CAMERA);
    }

    private void onCameraPermissionResult(@NonNull PermissionStatus status) {
        if (status == PermissionStatus.GRANTED) {
            launchCameraScan();
            return;
        }
        FragmentBultoIncidenceBinding views = requireBinding();
        views.buttonBultoIncidenceScanCamera.setEnabled(true);
        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), AppPermission.CAMERA);
        } else {
            Snackbar.make(views.getRoot(), R.string.bulto_incidence_camera_denied, Snackbar.LENGTH_LONG).show();
        }
    }

    private void launchCameraScan() {
        ScanOptions options = new ScanOptions()
                .setPrompt(getString(R.string.bulto_incidence_scan_prompt))
                .setBeepEnabled(true)
                // El visor sigue la orientación del terminal (ver CaptureActivity en el manifest)
                .setOrientationLocked(false);
        try {
            Timber.i("Abrimos la cámara para escanear el bulto de la incidencia");
            cameraScan.launch(options);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No se puede abrir el visor de escaneo por cámara");
            requireBinding().buttonBultoIncidenceScanCamera.setEnabled(true);
        }
    }

    /** Sin contenido es que el operario cerró la cámara sin leer nada. */
    private void onCameraScanResult(@NonNull ScanIntentResult result) {
        requireBinding().buttonBultoIncidenceScanCamera.setEnabled(true);
        String barcode = result.getContents();
        if (barcode == null || barcode.isEmpty()) {
            Timber.d("Escaneo por cámara cancelado");
            return;
        }
        viewModel.onBarcodeScanned(barcode);
    }

    /** Hoja de foto ya existente: hace o elige la imagen, la envía y devuelve su content://. */
    private void showPhotoSheet() {
        FragmentManager fragmentManager = getChildFragmentManager();
        if (viewModel.canTakePhoto()
                && fragmentManager.findFragmentByTag(PHOTO_SHEET_TAG) == null
                && !fragmentManager.isStateSaved()) {
            new PhotoSourceBottomSheet().show(fragmentManager, PHOTO_SHEET_TAG);
        }
    }

    private void render(@NonNull BultoIncidenceState state) {
        FragmentBultoIncidenceBinding views = requireBinding();
        views.layoutBultoIncidencesWaiting.setVisibility(
                state instanceof BultoIncidenceState.WaitingForBarcode ? View.VISIBLE : View.GONE);
        views.layoutBultoIncidencesLoading.setVisibility(
                state instanceof BultoIncidenceState.Loading ? View.VISIBLE : View.GONE);
        // La lista y lo que depende de ella (observaciones y foto) solo con las incidencias cargadas
        int loadedVisibility = state instanceof BultoIncidenceState.Ready ? View.VISIBLE : View.GONE;
        views.cardBultoIncidences.setVisibility(loadedVisibility);
        views.textBultoIncidenceListLabel.setVisibility(loadedVisibility);
        views.layoutBultoIncidenceObservations.setVisibility(loadedVisibility);
        views.layoutBultoIncidencePhotoRow.setVisibility(loadedVisibility);
        views.layoutBultoIncidencesError.setVisibility(
                state instanceof BultoIncidenceState.Failed || state instanceof BultoIncidenceState.Empty
                        ? View.VISIBLE : View.GONE);

        if (state instanceof BultoIncidenceState.Ready ready && adapter != null) {
            adapter.submitList(ready.incidences());
        } else if (state instanceof BultoIncidenceState.Failed failed) {
            views.textBultoIncidencesErrorTitle.setText(R.string.bulto_incidence_error_title);
            views.textBultoIncidencesError.setText(failed.message().resolve(requireContext()));
        } else if (state instanceof BultoIncidenceState.Empty) {
            views.textBultoIncidencesErrorTitle.setText(R.string.bulto_incidence_empty_title);
            views.textBultoIncidencesError.setText(R.string.bulto_incidence_empty);
        }
        renderActions();
    }

    private void renderBarcode(@Nullable String barcode) {
        FragmentBultoIncidenceBinding views = requireBinding();
        views.textBultoIncidenceBarcode.setText(barcode != null
                ? barcode
                : getString(R.string.bulto_incidence_no_barcode));
        // Fundido breve solo al leer otro bulto: confirma la lectura; al recrear la vista ya estaba
        boolean isNew = barcodeRendered && !Objects.equals(barcode, renderedBarcode);
        renderedBarcode = barcode;
        barcodeRendered = true;
        if (isNew) {
            View barcodeView = views.layoutBultoIncidenceBarcode;
            barcodeView.animate().cancel();
            barcodeView.setAlpha(0f);
            barcodeView.animate()
                    .alpha(1f)
                    .setDuration(getResources().getInteger(R.integer.motion_duration_layout))
                    .setInterpolator(new DecelerateInterpolator());
        }
    }

    private void renderSelection(@Nullable BultoIncidencia selected) {
        FragmentBultoIncidenceBinding views = requireBinding();
        if (adapter != null) {
            adapter.setSelectedCode(selected != null ? selected.codigoIncidencia() : null);
        }
        boolean observationsRequired = selected != null && selected.observationsRequired();
        // Solo se graban si la incidencia las exige: no se deja texto que no se va a guardar
        if (!observationsRequired) {
            views.inputBultoIncidenceObservations.setText(null);
        }
        views.layoutBultoIncidenceObservations.setEnabled(observationsRequired);
        views.layoutBultoIncidenceObservations.setHelperText(getString(observationsRequired
                ? R.string.bulto_incidence_observations_required
                : R.string.bulto_incidence_observations_helper));
        renderActions();
        renderPhoto();
    }

    private void renderForm(@NonNull BultoIncidenceFormState form) {
        requireBinding().layoutBultoIncidenceObservations.setError(
                form.observationsError() != null ? getString(form.observationsError()) : null);
        renderPhoto();
    }

    private void renderActions() {
        FragmentBultoIncidenceBinding views = requireBinding();
        views.buttonBultoIncidenceConfirm.setEnabled(viewModel.canConfirm());
        views.buttonBultoIncidencePhoto.setEnabled(viewModel.canTakePhoto());
    }

    /** Qué pasa con la foto, en texto e icono: el color solo refuerza. */
    private void renderPhoto() {
        FragmentBultoIncidenceBinding views = requireBinding();
        BultoIncidencia selected = viewModel.getSelectedIncidence().getValue();
        BultoIncidenceFormState form = viewModel.getFormState().getValue();
        boolean attached = viewModel.getPhotoUri().getValue() != null;

        if (form != null && form.photoError() != null) {
            showPhotoStatus(R.drawable.ic_error_24, form.photoError(), androidx.appcompat.R.attr.colorError);
        } else if (attached) {
            showPhotoStatus(R.drawable.ic_check_24, R.string.bulto_incidence_photo_attached,
                    androidx.appcompat.R.attr.colorPrimary);
        } else if (selected == null) {
            showPhotoStatus(R.drawable.ic_photo_camera_24, R.string.bulto_incidence_photo_choose_first,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
        } else {
            showPhotoStatus(R.drawable.ic_photo_camera_24, selected.photoRequired()
                            ? R.string.bulto_incidence_photo_required
                            : R.string.bulto_incidence_photo_optional,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        views.buttonBultoIncidencePhoto.setText(attached
                ? R.string.bulto_incidence_retake_photo
                : R.string.bulto_incidence_take_photo);
    }

    private void showPhotoStatus(@DrawableRes int icon, @StringRes int text, @AttrRes int colorAttr) {
        FragmentBultoIncidenceBinding views = requireBinding();
        TextView status = views.textBultoIncidencePhotoStatus;
        int color = MaterialColors.getColor(status, colorAttr);
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(status, ColorStateList.valueOf(color));
        status.setText(text);
        status.setTextColor(color);
    }

    @StringRes
    private static int titleOf(@NonNull OperationType operationType) {
        return switch (operationType) {
            case CARGA -> R.string.bulto_incidence_operation_carga;
            case RECOGIDAS -> R.string.bulto_incidence_operation_recogidas;
        };
    }

    /**
     * Acción del teclado o Intro del escáner, que llega sin acción IME.
     *
     * @return si se ha consumido el evento
     */
    private static boolean onEditorAction(int actionId, @Nullable KeyEvent event, @NonNull Runnable action) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            action.run();
            return true;
        }
        if (actionId != EditorInfo.IME_ACTION_UNSPECIFIED
                || event == null
                || event.getKeyCode() != KeyEvent.KEYCODE_ENTER) {
            return false;
        }
        // De Intro llegan pulsación y suelta: se actúa una sola vez
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            action.run();
        }
        return true;
    }

    @NonNull
    private String observationsText() {
        CharSequence text = requireBinding().inputBultoIncidenceObservations.getText();
        return text != null ? text.toString() : "";
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private FragmentBultoIncidenceBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de incidencias no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerBultoIncidences.setAdapter(null);
            binding.layoutBultoIncidenceBarcode.animate().cancel();
        }
        adapter = null;
        renderedBarcode = null;
        barcodeRendered = false;
        binding = null;
    }
}
