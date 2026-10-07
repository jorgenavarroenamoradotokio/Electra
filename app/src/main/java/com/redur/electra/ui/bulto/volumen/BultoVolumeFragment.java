package com.redur.electra.ui.bulto.volumen;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoMeasures;
import com.redur.electra.databinding.FragmentBultoVolumeBinding;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Lectura en modo Volumen (sin peso): se lee el CB del bulto y después alto, ancho y profundo,
 * del escáner o el medidor o a mano. El foco avanza solo al dato que se espera a continuación y,
 * tras aceptar o cancelar, vuelve al CB para el bulto siguiente. De momento, como en el tipo de
 * bulto, aceptar y cancelar solo lo confirman con un aviso y las acciones rápidas aún no están
 * disponibles.
 */
@AndroidEntryPoint
public class BultoVolumeFragment extends Fragment {

    @Nullable
    private FragmentBultoVolumeBinding binding;
    private BultoVolumeViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoVolumeViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoVolumeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoVolumeBinding views = requireBinding();

        View.OnClickListener unavailable = v -> showToast(R.string.feature_unavailable);
        views.buttonBultoVolumePs.setOnClickListener(unavailable);
        views.buttonBultoVolumeTb.setOnClickListener(unavailable);
        views.buttonBultoVolumeKg.setOnClickListener(unavailable);
        views.buttonBultoVolumeInc.setOnClickListener(unavailable);

        views.checkBultoVolumeMode.setOnCheckedChangeListener(
                (button, checked) -> viewModel.onVolumeModeChanged(checked));
        views.inputBultoVolumeBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, EditorInfo.IME_ACTION_NEXT,
                        () -> moveFocusTo(requireBinding().inputBultoVolumeHeight)));
        views.inputBultoVolumeDepth.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, EditorInfo.IME_ACTION_DONE, this::confirm));
        views.buttonBultoVolumeConfirm.setOnClickListener(v -> confirm());
        views.buttonBultoVolumeCancel.setOnClickListener(v -> cancel());

        viewModel.getVolumeMode().observe(getViewLifecycleOwner(), this::renderVolumeMode);
        viewModel.getFormState().observe(getViewLifecycleOwner(), this::renderForm);
        viewModel.getVolume().observe(getViewLifecycleOwner(), this::renderVolume);
    }

    /**
     * Los campos se escuchan una vez restaurado lo tecleado: restaurarlo no cuenta como una
     * corrección, no debe borrar los errores que ya se mostraban ni mover el foco.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        FragmentBultoVolumeBinding views = requireBinding();
        views.inputBultoVolumeBarcode.addTextChangedListener(afterChanged(viewModel::onBarcodeChanged));
        Runnable measuresChanged = () -> viewModel.onMeasuresChanged(
                textOf(requireBinding().inputBultoVolumeHeight),
                textOf(requireBinding().inputBultoVolumeWidth),
                textOf(requireBinding().inputBultoVolumeDepth));
        views.inputBultoVolumeHeight.addTextChangedListener(afterChanged(measuresChanged));
        views.inputBultoVolumeWidth.addTextChangedListener(afterChanged(measuresChanged));
        views.inputBultoVolumeDepth.addTextChangedListener(afterChanged(measuresChanged));
        // Las medidas llegan con sus 3 dígitos: completa una, se espera la siguiente
        views.inputBultoVolumeHeight.addTextChangedListener(
                afterChanged(() -> advanceWhenComplete(requireBinding().inputBultoVolumeHeight,
                        requireBinding().inputBultoVolumeWidth)));
        views.inputBultoVolumeWidth.addTextChangedListener(
                afterChanged(() -> advanceWhenComplete(requireBinding().inputBultoVolumeWidth,
                        requireBinding().inputBultoVolumeDepth)));
        if (savedInstanceState != null) {
            viewModel.onMeasuresRestored(textOf(views.inputBultoVolumeHeight),
                    textOf(views.inputBultoVolumeWidth), textOf(views.inputBultoVolumeDepth));
        } else {
            views.inputBultoVolumeBarcode.requestFocus();
        }
    }

    private void confirm() {
        FragmentBultoVolumeBinding views = requireBinding();
        BultoVolumeReading reading = viewModel.onConfirmClicked(
                textOf(views.inputBultoVolumeBarcode),
                textOf(views.inputBultoVolumeHeight),
                textOf(views.inputBultoVolumeWidth),
                textOf(views.inputBultoVolumeDepth));
        // El foco solo se mueve como respuesta a aceptar, nunca al re-renderizar mientras se escribe
        if (reading == null) {
            focusFirstInvalidField();
            return;
        }
        showToast(R.string.bulto_volume_saved);
        startNextReading();
    }

    private void cancel() {
        viewModel.onCancelClicked();
        showToast(R.string.bulto_volume_canceled);
        startNextReading();
    }

    /** Bucle de lectura: se vacían los campos y se espera el CB del bulto siguiente. */
    private void startNextReading() {
        FragmentBultoVolumeBinding views = requireBinding();
        views.inputBultoVolumeBarcode.setText(null);
        views.inputBultoVolumeHeight.setText(null);
        views.inputBultoVolumeWidth.setText(null);
        views.inputBultoVolumeDepth.setText(null);
        if (viewModel.isVolumeMode()) {
            views.inputBultoVolumeBarcode.requestFocus();
        }
    }

    private void renderVolumeMode(@Nullable Boolean enabled) {
        FragmentBultoVolumeBinding views = requireBinding();
        boolean volumeMode = Boolean.TRUE.equals(enabled);
        views.checkBultoVolumeMode.setChecked(volumeMode);
        views.containerBultoVolumeReading.setVisibility(volumeMode ? View.VISIBLE : View.GONE);
        views.buttonBultoVolumeConfirm.setEnabled(volumeMode);
        if (!volumeMode) {
            hideKeyboard();
        }
    }

    private void renderForm(@NonNull BultoVolumeFormState state) {
        FragmentBultoVolumeBinding views = requireBinding();
        showError(views.layoutBultoVolumeBarcode, state.barcodeError());
        showError(views.layoutBultoVolumeResult, state.volumeError());
    }

    private void renderVolume(@Nullable BigDecimal volume) {
        requireBinding().inputBultoVolumeResult.setText(
                volume != null ? formatVolume(volume) : null);
    }

    private void focusFirstInvalidField() {
        BultoVolumeFormState state = viewModel.getFormState().getValue();
        if (state == null || state.isValid()) {
            return;
        }
        FragmentBultoVolumeBinding views = requireBinding();
        EditText field;
        if (state.barcodeError() != null) {
            field = views.inputBultoVolumeBarcode;
        } else if (textOf(views.inputBultoVolumeHeight).isEmpty()) {
            field = views.inputBultoVolumeHeight;
        } else if (textOf(views.inputBultoVolumeWidth).isEmpty()) {
            field = views.inputBultoVolumeWidth;
        } else if (textOf(views.inputBultoVolumeDepth).isEmpty()) {
            field = views.inputBultoVolumeDepth;
        } else {
            field = views.inputBultoVolumeHeight;
        }
        moveFocusTo(field);
    }

    /** Solo avanza si el usuario está en el campo: un texto puesto por código no mueve el foco. */
    private void advanceWhenComplete(@NonNull EditText current, @NonNull EditText next) {
        if (current.hasFocus() && current.length() == getResources().getInteger(R.integer.bulto_measure_max_length)) {
            moveFocusTo(next);
        }
    }

    private static void moveFocusTo(@NonNull EditText field) {
        field.requestFocus();
        field.setSelection(field.length());
    }

    /**
     * Acción del teclado o Intro de un escáner/teclado físico, que llega sin acción IME.
     *
     * @return si se ha consumido el evento
     */
    private static boolean onEditorAction(int actionId, @Nullable KeyEvent event, int expectedAction,
                                          @NonNull Runnable action) {
        boolean imeAction = actionId == expectedAction;
        boolean enterKey = actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                && event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
        if (!imeAction && !enterKey) {
            return false;
        }
        // De Intro llegan pulsación y suelta: se actúa una sola vez
        if (imeAction || event.getAction() == KeyEvent.ACTION_DOWN) {
            action.run();
        }
        return true;
    }

    private void hideKeyboard() {
        View focus = requireActivity().getCurrentFocus();
        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (focus != null && imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    /**
     * setError(null) mantiene reservado el hueco del error: al corregir un campo el formulario no
     * salta bajo el dedo del usuario.
     */
    private void showError(@NonNull TextInputLayout layout, @Nullable @StringRes Integer errorRes) {
        layout.setError(errorRes != null ? getString(errorRes) : null);
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /** Con el separador decimal del idioma del usuario (0,04 en español) y sin separador de miles. */
    @NonNull
    private static String formatVolume(@NonNull BigDecimal value) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(BultoMeasures.VOLUME_SCALE);
        format.setMaximumFractionDigits(BultoMeasures.VOLUME_SCALE);
        return format.format(value);
    }

    @NonNull
    private static String textOf(@NonNull TextView input) {
        CharSequence text = input.getText();
        return text != null ? text.toString().trim() : "";
    }

    @NonNull
    private static TextWatcher afterChanged(@NonNull Runnable action) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                action.run();
            }
        };
    }

    @NonNull
    private FragmentBultoVolumeBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de volumen no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
