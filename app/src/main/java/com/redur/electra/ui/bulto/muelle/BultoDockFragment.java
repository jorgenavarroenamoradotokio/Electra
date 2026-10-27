package com.redur.electra.ui.bulto.muelle;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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
import com.redur.electra.data.model.bulto.BultoDock;
import com.redur.electra.databinding.FragmentBultoDockBinding;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public class BultoDockFragment extends Fragment {

    @Inject
    ScannerManagerFactory scannerManagerFactory;

    // Se registran al construir el Fragment: así el resultado llega aunque se recree mientras
    // está abierto el diálogo de permisos o la cámara
    private final PermissionRequester cameraPermissionRequester =
            new PermissionRequester(this, this::onCameraPermissionResult);
    private final ActivityResultLauncher<ScanOptions> cameraScan =
            registerForActivityResult(new ScanContract(), this::onCameraScanResult);

    @Nullable
    private FragmentBultoDockBinding binding;
    private BultoDockViewModel viewModel;
    /** Para animar solo la llegada de un muelle nuevo, no cada re-renderizado. */
    @Nullable
    private BultoDockState renderedState;
    /** CB que aún hay que escribir en el campo; solo la primera vez que se crea la vista. */
    @Nullable
    private String initialBarcode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoDockViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoDockBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoDockBinding views = requireBinding();

        // Campo oculto que recibe el CB del escáner por teclado: tiene el foco sin abrir el teclado en pantalla
        views.inputBultoDockBarcode.setShowSoftInputOnFocus(false);
        views.inputBultoDockBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, this::onBarcodeScanned));
        views.buttonBultoDockRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        views.buttonBultoDockCancel.setOnClickListener(v -> cancel());

        // Solo entrega lecturas con la vista en RESUMED; el lifecycle de la vista lo libera
        HardwareScannerManager scanner = scannerManagerFactory.create(this::onBarcodeRead);
        getViewLifecycleOwner().getLifecycle().addObserver(scanner);
        if (!scanner.isAvailable()) {
            // Solo se anuncia al abrir la pantalla, no al recrearla
            showCameraScan(savedInstanceState == null);
        }

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        if (savedInstanceState == null) {
            views.inputBultoDockBarcode.requestFocus();
        }
        if (initialBarcode != null) {
            // Igual que tras una lectura: la siguiente sustituye al CB en lugar de añadirse
            views.inputBultoDockBarcode.setText(initialBarcode);
            views.inputBultoDockBarcode.selectAll();
            initialBarcode = null;
        }
    }

    /** Lectura que no llega tecleada en el campo (broadcast del escáner o cámara). */
    private void onBarcodeRead(@NonNull String barcode) {
        requireBinding().inputBultoDockBarcode.setText(barcode);
        onBarcodeScanned();
    }

    private void onBarcodeScanned() {
        FragmentBultoDockBinding views = requireBinding();
        CharSequence text = views.inputBultoDockBarcode.getText();
        viewModel.getMuelleBulto(text != null ? text.toString() : null);
        // La lectura siguiente del escáner sustituye al CB en lugar de añadirse
        views.inputBultoDockBarcode.selectAll();
    }

    /** Sin escáner hardware: la cámara es la forma de leer el CB. */
    private void showCameraScan(boolean animate) {
        FragmentBultoDockBinding views = requireBinding();
        View fab = views.fabBultoDockScanCamera;
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> onScanCameraClicked());
        // Hueco al final del contenido para que el botón no tape el último elemento al desplazar
        View content = views.layoutBultoDockContent;
        content.setPaddingRelative(content.getPaddingStart(), content.getPaddingTop(), content.getPaddingEnd(),
                getResources().getDimensionPixelSize(R.dimen.bulto_dock_fab_clearance));
        if (animate) {
            animateFabEnter(fab);
        }
    }

    /** Entra subiendo un poco desde su sitio: se percibe como parte de la pantalla, no como un aviso. */
    private void animateFabEnter(@NonNull View fab) {
        fab.setAlpha(0f);
        fab.setTranslationY(getResources().getDimension(R.dimen.spacing_m));
        fab.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(getResources().getInteger(R.integer.motion_duration_enter))
                .setInterpolator(new DecelerateInterpolator());
    }

    /** Se desactiva hasta cerrar la cámara o resolver el permiso: evita abrirla dos veces. */
    private void onScanCameraClicked() {
        requireBinding().fabBultoDockScanCamera.setEnabled(false);
        cameraPermissionRequester.request(AppPermission.CAMERA);
    }

    private void onCameraPermissionResult(@NonNull PermissionStatus status) {
        if (status == PermissionStatus.GRANTED) {
            launchCameraScan();
            return;
        }
        FragmentBultoDockBinding views = requireBinding();
        views.fabBultoDockScanCamera.setEnabled(true);
        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), AppPermission.CAMERA);
        } else {
            Snackbar.make(views.getRoot(), R.string.bulto_dock_camera_denied, Snackbar.LENGTH_LONG).show();
        }
    }

    private void launchCameraScan() {
        ScanOptions options = new ScanOptions()
                .setPrompt(getString(R.string.bulto_dock_scan_prompt))
                .setBeepEnabled(true)
                // El visor sigue la orientación del terminal (ver CaptureActivity en el manifest)
                .setOrientationLocked(false);
        try {
            Timber.i("Abrimos la cámara para escanear el bulto");
            cameraScan.launch(options);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No se puede abrir el visor de escaneo por cámara");
            requireBinding().fabBultoDockScanCamera.setEnabled(true);
        }
    }

    /** Sin contenido es que el operario cerró la cámara sin leer nada. */
    private void onCameraScanResult(@NonNull ScanIntentResult result) {
        FragmentBultoDockBinding views = requireBinding();
        views.fabBultoDockScanCamera.setEnabled(true);
        String barcode = result.getContents();
        if (barcode == null || barcode.isEmpty()) {
            Timber.d("Escaneo por cámara cancelado");
            return;
        }
        onBarcodeRead(barcode);
    }

    private void cancel() {
        viewModel.onCancelClicked();
        Toast.makeText(requireContext(), R.string.bulto_dock_canceled, Toast.LENGTH_SHORT).show();
        startNextReading();
    }

    /** Se vacía el campo y se espera el CB del bulto siguiente. */
    private void startNextReading() {
        FragmentBultoDockBinding views = requireBinding();
        views.inputBultoDockBarcode.setText(null);
        views.inputBultoDockBarcode.requestFocus();
    }

    private void render(@NonNull BultoDockState state) {
        FragmentBultoDockBinding views = requireBinding();
        views.layoutBultoDockWaiting.setVisibility(
                state instanceof BultoDockState.Waiting ? View.VISIBLE : View.GONE);
        views.layoutBultoDockLoading.setVisibility(
                state instanceof BultoDockState.Loading ? View.VISIBLE : View.GONE);
        views.layoutBultoDockFound.setVisibility(
                state instanceof BultoDockState.Found ? View.VISIBLE : View.GONE);
        views.layoutBultoDockError.setVisibility(
                state instanceof BultoDockState.NotFound || state instanceof BultoDockState.Failed
                        ? View.VISIBLE : View.GONE);

        renderBarcode(state.barcode());

        if (state instanceof BultoDockState.Found foundState) {
            renderDock(foundState.dock(), !state.equals(renderedState));
        } else if (state instanceof BultoDockState.NotFound) {
            views.textBultoDockErrorTitle.setText(R.string.bulto_dock_not_found_title);
            views.textBultoDockError.setText(R.string.bulto_dock_not_found);
            views.buttonBultoDockRetry.setVisibility(View.GONE);
        } else if (state instanceof BultoDockState.Failed failed) {
            views.textBultoDockErrorTitle.setText(R.string.bulto_dock_error_title);
            views.textBultoDockError.setText(failed.message().resolve(requireContext()));
            views.buttonBultoDockRetry.setVisibility(View.VISIBLE);
        }
        renderedState = state;
    }

    /** El CB leído solo se muestra con una lectura en curso o su muelle a la vista. */
    private void renderBarcode(@Nullable String barcode) {
        FragmentBultoDockBinding views = requireBinding();
        View barcodeView = views.layoutBultoDockBarcode;
        if (barcode == null) {
            barcodeView.animate().cancel();
            barcodeView.setVisibility(View.GONE);
            return;
        }
        views.textBultoDockBarcode.setText(barcode);
        // Fundido solo al aparecer tras una lectura; al recrear la vista ya estaba a la vista
        boolean appearing = barcodeView.getVisibility() != View.VISIBLE && renderedState != null;
        barcodeView.setVisibility(View.VISIBLE);
        if (appearing) {
            barcodeView.setAlpha(0f);
            barcodeView.animate()
                    .alpha(1f)
                    .setDuration(getResources().getInteger(R.integer.motion_duration_layout))
                    .setInterpolator(new DecelerateInterpolator());
        }
    }

    private void renderDock(@NonNull BultoDock dock, boolean isNew) {
        FragmentBultoDockBinding views = requireBinding();
        views.textBultoDockCode.setText(dock.code());
        // El destino va en minúsculas, salvo la inicial del país
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        views.textBultoDockDestination.setText(getString(R.string.bulto_dock_destination,
                dock.place(), dock.postalCode().toLowerCase(locale),
                capitalize(dock.country(), locale)));
        views.layoutBultoDockFound.setContentDescription(getString(R.string.bulto_dock_description,
                dock.code(), dock.place(), dock.postalCode(), dock.country()));
        if (isNew) {
            animateArrival(views.layoutBultoDockFound);
        }
    }

    @NonNull
    private static String capitalize(@NonNull String text, @NonNull Locale locale) {
        String lower = text.trim().toLowerCase(locale);
        if (lower.isEmpty()) {
            return lower;
        }
        return lower.substring(0, 1).toUpperCase(locale) + lower.substring(1);
    }

    /** Llegada breve y sutil: confirma que el muelle es de la lectura que se acaba de hacer. */
    private void animateArrival(@NonNull View dockView) {
        float startScale = getResources().getFloat(R.dimen.press_scale);
        dockView.animate().cancel();
        dockView.setAlpha(0f);
        dockView.setScaleX(startScale);
        dockView.setScaleY(startScale);
        dockView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(getResources().getInteger(R.integer.motion_duration_layout))
                .setInterpolator(new DecelerateInterpolator());
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
    private FragmentBultoDockBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la puerta de salida no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.layoutBultoDockFound.animate().cancel();
            binding.layoutBultoDockBarcode.animate().cancel();
            binding.fabBultoDockScanCamera.animate().cancel();
        }
        renderedState = null;
        binding = null;
    }
}
