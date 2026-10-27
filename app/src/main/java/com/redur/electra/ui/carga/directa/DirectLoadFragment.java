package com.redur.electra.ui.carga.directa;

import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDividerItemDecoration;
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
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.databinding.FragmentDirectLoadBinding;
import com.redur.electra.databinding.ViewDirectLoadReadingBinding;
import com.redur.electra.databinding.ViewDirectLoadSealBinding;
import com.redur.electra.ui.bulto.tipo.BultoTypePicker;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Carga con precinto: al entrar se pide el precinto (CB o tecleado) y después se leen los bultos
 * con el escáner. Precinto y tipo de bulto fijado quedan siempre a la vista en la cabecera; el tipo
 * se elige en la pantalla general de tipo de bulto, abierta como selector, y se quita desde la ficha.
 * El mismo selector, abierto desde una fila de la lista, corrige el tipo de un bulto ya leído.
 * <p>
 * El escáner hardware entrega el CB por broadcast (Honeywell, Datalogic) o tecleándolo en el campo
 * con el foco, con Intro al final. Si el terminal no tiene escáner compatible, tras aceptar el
 * precinto un botón flotante abre la cámara para leer los bultos.
 */
@AndroidEntryPoint
public class DirectLoadFragment extends Fragment {

    @Inject
    ScannerManagerFactory scannerManagerFactory;

    // Se registran al construir el Fragment: así el resultado llega aunque se recree mientras
    // está abierto el diálogo de permisos o la cámara
    private final PermissionRequester cameraPermissionRequester =
            new PermissionRequester(this, this::onCameraPermissionResult);
    private final ActivityResultLauncher<ScanOptions> cameraScan =
            registerForActivityResult(new ScanContract(), this::onCameraScanResult);

    @Nullable
    private FragmentDirectLoadBinding binding;
    private DirectLoadViewModel viewModel;
    @Nullable
    private DirectLoadReadingAdapter adapter;
    /** Para mover el foco solo al cambiar de fase, no en cada re-renderizado. */
    @Nullable
    private Class<? extends DirectLoadState> renderedPhase;
    /** Aspecto de la ficha de tipo sin tipo fijado: el del estilo outlined, para restaurarlo. */
    @Nullable
    private ColorStateList emptyTypeStroke;
    @Nullable
    private ColorStateList emptyTypeBackground;
    private int emptyTypeStrokeWidth;
    /** Sin escáner hardware: la cámara es la forma de leer los bultos. */
    private boolean cameraScanNeeded;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DirectLoadViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDirectLoadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewDirectLoadSealBinding seal = requireBinding().layoutDirectLoadSeal;
        ViewDirectLoadReadingBinding reading = requireBinding().layoutDirectLoadReading;

        // Precinto: escáner o teclado
        seal.inputDirectLoadSeal.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, EditorInfo.IME_ACTION_GO, this::submitSeal));
        seal.inputDirectLoadSeal.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onSealEdited();
            }
        });
        seal.buttonDirectLoadStart.setOnClickListener(v -> submitSeal());

        // Bultos: campo oculto que recibe el CB del escáner por teclado; tiene el foco sin abrir el
        // teclado en pantalla
        reading.inputDirectLoadBarcode.setShowSoftInputOnFocus(false);
        reading.inputDirectLoadBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, EditorInfo.IME_ACTION_SEARCH, this::onBarcodeScanned));

        // Solo entrega lecturas con la vista en RESUMED; el lifecycle de la vista lo libera
        HardwareScannerManager scanner = scannerManagerFactory.create(this::onBarcodeRead);
        getViewLifecycleOwner().getLifecycle().addObserver(scanner);
        cameraScanNeeded = !scanner.isAvailable();
        reading.fabDirectLoadScanCamera.setOnClickListener(v -> onScanCameraClicked());

        adapter = new DirectLoadReadingAdapter(this::openTypePicker);
        reading.recyclerDirectLoadReadings.setAdapter(adapter);
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(requireContext(),
                MaterialDividerItemDecoration.VERTICAL);
        divider.setLastItemDecorated(false);
        reading.recyclerDirectLoadReadings.addItemDecoration(divider);

        emptyTypeStroke = reading.cardDirectLoadType.getStrokeColorStateList();
        emptyTypeBackground = reading.cardDirectLoadType.getCardBackgroundColor();
        emptyTypeStrokeWidth = reading.cardDirectLoadType.getStrokeWidth();
        reading.cardDirectLoadType.setOnClickListener(v -> openFixedTypePicker());
        reading.buttonDirectLoadType.setOnClickListener(v -> openFixedTypePicker());
        reading.buttonDirectLoadTypeClear.setOnClickListener(v -> viewModel.onBultoTypeCleared());

        observeTypePickerResult();
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    /** El selector deja el tipo elegido en la entrada de esta pantalla; se consume una sola vez. */
    private void observeTypePickerResult() {
        NavController navController = NavHostFragment.findNavController(this);
        SavedStateHandle handle = navController.getBackStackEntry(R.id.nav_direct_load).getSavedStateHandle();
        handle.<Bundle>getLiveData(BultoTypePicker.RESULT_KEY).observe(getViewLifecycleOwner(), result -> {
            BultoType type = BultoTypePicker.fromBundle(result);
            if (type != null) {
                handle.remove(BultoTypePicker.RESULT_KEY);
                viewModel.onBultoTypePicked(type);
            }
        });
    }

    private void openFixedTypePicker() {
        Timber.i("[ACCION] Abrir selector de tipo de bulto");
        viewModel.onFixedTypePickerRequested();
        navigateToTypePicker(viewModel.getFixedTypeCode());
    }

    /** Corrige el tipo de un bulto ya leído: el selector se abre con el suyo marcado. */
    private void openTypePicker(@NonNull DirectLoadReading reading) {
        viewModel.onReadingTypePickerRequested(reading.barcode());
        navigateToTypePicker(reading.type() != null ? reading.type().code() : null);
    }

    private void navigateToTypePicker(@Nullable String preselectedCode) {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        // Un doble toque rápido no abre el selector dos veces
        if (current == null || current.getId() != R.id.nav_direct_load) {
            return;
        }
        navController.navigate(R.id.nav_bulto_type_picker, BultoTypePicker.args(preselectedCode));
    }

    private void submitSeal() {
        CharSequence text = requireBinding().layoutDirectLoadSeal.inputDirectLoadSeal.getText();
        viewModel.onSealEntered(text != null ? text.toString() : null);
    }

    /**
     * Lectura que no llega tecleada en un campo (broadcast del escáner o cámara). Antes de aceptar
     * el precinto es el precinto: con la salida por broadcast su campo no recibe lo leído.
     */
    private void onBarcodeRead(@NonNull String barcode) {
        if (viewModel.getState().getValue() instanceof DirectLoadState.SealEntry) {
            requireBinding().layoutDirectLoadSeal.inputDirectLoadSeal.setText(barcode);
            submitSeal();
        } else {
            viewModel.onBarcodeScanned(barcode);
        }
    }

    private void onBarcodeScanned() {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        CharSequence text = views.inputDirectLoadBarcode.getText();
        viewModel.onBarcodeScanned(text != null ? text.toString() : null);
        // El CB se ve en la ficha del último leído; el campo se vacía para la lectura siguiente
        views.inputDirectLoadBarcode.setText(null);
    }

    /** Se desactiva hasta cerrar la cámara o resolver el permiso: evita abrirla dos veces. */
    private void onScanCameraClicked() {
        requireBinding().layoutDirectLoadReading.fabDirectLoadScanCamera.setEnabled(false);
        cameraPermissionRequester.request(AppPermission.CAMERA);
    }

    private void onCameraPermissionResult(@NonNull PermissionStatus status) {
        if (status == PermissionStatus.GRANTED) {
            launchCameraScan();
            return;
        }
        FragmentDirectLoadBinding views = requireBinding();
        views.layoutDirectLoadReading.fabDirectLoadScanCamera.setEnabled(true);
        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), AppPermission.CAMERA);
        } else {
            Snackbar.make(views.getRoot(), R.string.direct_load_camera_denied, Snackbar.LENGTH_LONG).show();
        }
    }

    private void launchCameraScan() {
        ScanOptions options = new ScanOptions()
                .setPrompt(getString(R.string.direct_load_scan_prompt))
                .setBeepEnabled(true)
                // El visor sigue la orientación del terminal (ver CaptureActivity en el manifest)
                .setOrientationLocked(false);
        try {
            Timber.i("Abrimos la cámara para escanear el bulto de la carga");
            cameraScan.launch(options);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No se puede abrir el visor de escaneo por cámara");
            requireBinding().layoutDirectLoadReading.fabDirectLoadScanCamera.setEnabled(true);
        }
    }

    /** Sin contenido es que el operario cerró la cámara sin leer nada. */
    private void onCameraScanResult(@NonNull ScanIntentResult result) {
        requireBinding().layoutDirectLoadReading.fabDirectLoadScanCamera.setEnabled(true);
        String barcode = result.getContents();
        if (barcode == null || barcode.isEmpty()) {
            Timber.d("Escaneo por cámara cancelado");
            return;
        }
        onBarcodeRead(barcode);
    }

    private void render(@NonNull DirectLoadState state) {
        FragmentDirectLoadBinding views = requireBinding();
        boolean phaseChanged = renderedPhase != state.getClass();
        views.layoutDirectLoadSeal.getRoot().setVisibility(
                state instanceof DirectLoadState.SealEntry ? View.VISIBLE : View.GONE);
        views.layoutDirectLoadReading.getRoot().setVisibility(
                state instanceof DirectLoadState.Reading ? View.VISIBLE : View.GONE);

        if (state instanceof DirectLoadState.SealEntry entry) {
            views.layoutDirectLoadSeal.layoutDirectLoadSealInput.setError(
                    entry.sealError() != null ? getString(entry.sealError()) : null);
            if (phaseChanged) {
                views.layoutDirectLoadSeal.inputDirectLoadSeal.requestFocus();
            }
        } else if (state instanceof DirectLoadState.Reading reading) {
            if (phaseChanged) {
                // Del teclado del precinto se pasa al escáner: el teclado ya no hace falta
                hideKeyboard(views.layoutDirectLoadSeal.inputDirectLoadSeal);
                views.layoutDirectLoadReading.inputDirectLoadBarcode.requestFocus();
                if (cameraScanNeeded) {
                    // Se anuncia al pasar el precinto, no al recrear la vista con la carga empezada
                    showCameraScan(renderedPhase == DirectLoadState.SealEntry.class);
                }
            }
            renderHeader(reading);
            renderLastBarcode(reading.lastBarcode());
            renderFeedback(reading.feedback());
            renderReadings(reading);
        }
        renderedPhase = state.getClass();
    }

    private void renderHeader(@NonNull DirectLoadState.Reading reading) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        views.textDirectLoadSeal.setText(reading.seal());
        views.cardDirectLoadSeal.setContentDescription(
                getString(R.string.direct_load_seal_description, reading.seal()));

        BultoType type = reading.fixedType();
        boolean hasType = type != null;
        views.buttonDirectLoadTypeClear.setVisibility(hasType ? View.VISIBLE : View.GONE);
        views.buttonDirectLoadType.setText(hasType ? R.string.direct_load_type_change : R.string.direct_load_type_choose);
        if (hasType) {
            views.cardDirectLoadType.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.selection_stroke_width));
            views.cardDirectLoadType.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(
                    views.cardDirectLoadType, androidx.appcompat.R.attr.colorPrimary)));
            views.cardDirectLoadType.setCardBackgroundColor(
                    ContextCompat.getColorStateList(requireContext(), R.color.icon_badge_background));
            views.textDirectLoadType.setText(type.type());
            views.textDirectLoadType.setTextColor(MaterialColors.getColor(views.textDirectLoadType,
                    com.google.android.material.R.attr.colorOnSurface));
            views.textDirectLoadTypeDescription.setText(type.description());
            views.textDirectLoadTypeDescription.setVisibility(type.description().isBlank() ? View.GONE : View.VISIBLE);
            views.cardDirectLoadType.setContentDescription(
                    getString(R.string.direct_load_type_description, type.type(), type.description()));
        } else {
            views.cardDirectLoadType.setStrokeWidth(emptyTypeStrokeWidth);
            views.cardDirectLoadType.setStrokeColor(emptyTypeStroke);
            views.cardDirectLoadType.setCardBackgroundColor(emptyTypeBackground);
            views.textDirectLoadType.setText(R.string.direct_load_type_none);
            views.textDirectLoadType.setTextColor(MaterialColors.getColor(views.textDirectLoadType,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            views.textDirectLoadTypeDescription.setText(R.string.direct_load_type_none_hint);
            views.textDirectLoadTypeDescription.setVisibility(View.VISIBLE);
            views.cardDirectLoadType.setContentDescription(getString(R.string.direct_load_type_description_none));
        }
    }

    private void renderLastBarcode(@Nullable String barcode) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        if (barcode != null) {
            views.textDirectLoadLastBarcode.setText(barcode);
            views.textDirectLoadLastBarcode.setTextColor(MaterialColors.getColor(views.textDirectLoadLastBarcode,
                    com.google.android.material.R.attr.colorOnSurface));
            views.cardDirectLoadLastBarcode.setContentDescription(
                    getString(R.string.direct_load_last_barcode_description, barcode));
        } else {
            views.textDirectLoadLastBarcode.setText(R.string.direct_load_last_barcode_none);
            views.textDirectLoadLastBarcode.setTextColor(MaterialColors.getColor(views.textDirectLoadLastBarcode,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            views.cardDirectLoadLastBarcode.setContentDescription(
                    getString(R.string.direct_load_last_barcode_description_none));
        }
    }

    /** Muestra el botón de cámara y deja hueco bajo la lista para que no tape la última fila. */
    private void showCameraScan(boolean animate) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        View fab = views.fabDirectLoadScanCamera;
        fab.setVisibility(View.VISIBLE);
        View list = views.recyclerDirectLoadReadings;
        list.setPaddingRelative(list.getPaddingStart(), list.getPaddingTop(), list.getPaddingEnd(),
                getResources().getDimensionPixelSize(R.dimen.direct_load_fab_clearance));
        if (animate) {
            // Entra subiendo un poco desde su sitio: se percibe como parte de la pantalla, no como un aviso
            fab.setAlpha(0f);
            fab.setTranslationY(getResources().getDimension(R.dimen.spacing_m));
            fab.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(getResources().getInteger(R.integer.motion_duration_enter))
                    .setInterpolator(new DecelerateInterpolator());
        }
    }

    private void renderFeedback(@Nullable DirectLoadState.Feedback feedback) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        views.cardDirectLoadFeedback.setVisibility(feedback != null ? View.VISIBLE : View.GONE);
        if (feedback instanceof DirectLoadState.Feedback.Added added) {
            BultoType type = added.reading().type();
            showFeedback(R.drawable.ic_check_24, R.color.msg_success_bg, R.color.msg_success_icon,
                    R.color.msg_success_text, type != null
                            ? getString(R.string.direct_load_feedback_added, type.type())
                            : getString(R.string.direct_load_feedback_added_no_type));
        } else if (feedback instanceof DirectLoadState.Feedback.Duplicate) {
            showFeedback(R.drawable.ic_report_problem_24, R.color.msg_warning_bg, R.color.msg_warning_icon,
                    R.color.msg_warning_text, getString(R.string.direct_load_feedback_duplicate));
        } else if (feedback instanceof DirectLoadState.Feedback.TypeChanged changed) {
            showFeedback(R.drawable.ic_check_24, R.color.msg_success_bg, R.color.msg_success_icon,
                    R.color.msg_success_text,
                    getString(R.string.direct_load_feedback_type_changed, changed.type().type()));
        }
    }

    private void showFeedback(@DrawableRes int icon, @ColorRes int background, @ColorRes int iconColor,
                              @ColorRes int textColor, @NonNull String message) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        views.cardDirectLoadFeedback.setCardBackgroundColor(ContextCompat.getColor(requireContext(), background));
        views.imageDirectLoadFeedback.setImageResource(icon);
        views.imageDirectLoadFeedback.setImageTintList(ContextCompat.getColorStateList(requireContext(), iconColor));
        views.textDirectLoadFeedback.setTextColor(ContextCompat.getColor(requireContext(), textColor));
        views.textDirectLoadFeedback.setText(message);
    }

    private void renderReadings(@NonNull DirectLoadState.Reading reading) {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        int count = reading.readings().size();
        views.textDirectLoadCount.setText(
                getResources().getQuantityString(R.plurals.direct_load_count, count, count));
        views.layoutDirectLoadEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        views.textDirectLoadListHint.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        views.recyclerDirectLoadReadings.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        if (adapter != null) {
            // La lectura nueva entra arriba: solo entonces se lleva la lista al principio
            boolean added = count > adapter.getItemCount();
            adapter.submitList(reading.readings(), () -> {
                if (added && binding != null) {
                    binding.layoutDirectLoadReading.recyclerDirectLoadReadings.scrollToPosition(0);
                }
            });
        }
    }

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Acción del teclado o Intro del escáner, que llega sin acción IME.
     *
     * @return si se ha consumido el evento
     */
    private static boolean onEditorAction(int actionId, @Nullable KeyEvent event, int expectedAction,
                                          @NonNull Runnable action) {
        if (actionId == expectedAction) {
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
    private FragmentDirectLoadBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la carga con precinto no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.layoutDirectLoadReading.recyclerDirectLoadReadings.setAdapter(null);
            binding.layoutDirectLoadReading.fabDirectLoadScanCamera.animate().cancel();
        }
        adapter = null;
        renderedPhase = null;
        emptyTypeStroke = null;
        emptyTypeBackground = null;
        binding = null;
    }
}
