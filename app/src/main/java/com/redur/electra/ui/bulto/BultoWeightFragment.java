package com.redur.electra.ui.bulto;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoMeasures;
import com.redur.electra.databinding.FragmentBultoWeightBinding;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Peso y medidas del bulto: el peso se teclea y el volumen se calcula a partir de alto, ancho y
 * profundo. Lo que pasa al aceptar o cancelar depende del origen con que se abra; de momento,
 * como en el tipo de bulto, ambas acciones solo lo confirman con un aviso.
 */
@AndroidEntryPoint
public class BultoWeightFragment extends Fragment {

    private static final String MEASURE_FORMAT = "%03d";

    @Nullable
    private FragmentBultoWeightBinding binding;
    private BultoWeightViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoWeightViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoWeightBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoWeightBinding views = requireBinding();
        views.textBultoWeightFullExpedition.setVisibility(
                viewModel.getOrigin() == BultoWeightOrigin.FULL_EXPEDITION ? View.VISIBLE : View.GONE);

        // Tras un cambio de configuración los campos restauran solos lo que se había tecleado
        BultoMeasures initial = viewModel.getInitialMeasures();
        if (savedInstanceState == null && initial != null) {
            fill(initial);
        }

        views.buttonBultoWeightConfirm.setOnClickListener(v -> confirm());
        views.buttonBultoWeightCancel.setOnClickListener(v -> cancel());

        viewModel.getFormState().observe(getViewLifecycleOwner(), this::renderForm);
        viewModel.getVolume().observe(getViewLifecycleOwner(), this::renderVolume);
    }

    /**
     * Los campos se escuchan una vez restaurado lo tecleado: restaurarlo no cuenta como una
     * corrección y no debe borrar los errores que ya se mostraban.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        FragmentBultoWeightBinding views = requireBinding();
        views.inputBultoWeight.addTextChangedListener(afterChanged(viewModel::onWeightChanged));
        Runnable measuresChanged = () -> viewModel.onMeasuresChanged(
                textOf(requireBinding().inputBultoHeight),
                textOf(requireBinding().inputBultoWidth),
                textOf(requireBinding().inputBultoDepth));
        views.inputBultoHeight.addTextChangedListener(afterChanged(measuresChanged));
        views.inputBultoWidth.addTextChangedListener(afterChanged(measuresChanged));
        views.inputBultoDepth.addTextChangedListener(afterChanged(measuresChanged));
        if (savedInstanceState != null) {
            viewModel.onMeasuresRestored(textOf(views.inputBultoHeight),
                    textOf(views.inputBultoWidth), textOf(views.inputBultoDepth));
        }
    }

    private void confirm() {
        FragmentBultoWeightBinding views = requireBinding();
        BultoWeightResult.Saved saved = viewModel.onConfirmClicked(
                textOf(views.inputBultoWeight),
                textOf(views.inputBultoHeight),
                textOf(views.inputBultoWidth),
                textOf(views.inputBultoDepth));
        // El foco solo se mueve como respuesta a aceptar, nunca al re-renderizar mientras se escribe
        if (saved == null) {
            focusFirstInvalidField();
            return;
        }
        hideKeyboard();
        showToast(R.string.bulto_weight_confirmed);
    }

    private void cancel() {
        hideKeyboard();
        BultoWeightResult.Canceled canceled = viewModel.onCancelClicked();
        showToast(switch (canceled.effect()) {
            case KEEP_UNCHANGED -> R.string.bulto_weight_canceled_unchanged;
            case KEEP_WITHOUT_WEIGHT -> R.string.bulto_weight_canceled_without_weight;
            case CANCEL_READING -> R.string.bulto_weight_canceled_reading;
        });
    }

    private void fill(@NonNull BultoMeasures measures) {
        FragmentBultoWeightBinding views = requireBinding();
        views.inputBultoWeight.setText(formatDecimal(measures.weightKg(), BultoMeasures.WEIGHT_SCALE));
        if (measures.hasMeasures()) {
            views.inputBultoHeight.setText(String.format(Locale.ROOT, MEASURE_FORMAT, measures.heightCm()));
            views.inputBultoWidth.setText(String.format(Locale.ROOT, MEASURE_FORMAT, measures.widthCm()));
            views.inputBultoDepth.setText(String.format(Locale.ROOT, MEASURE_FORMAT, measures.depthCm()));
        }
    }

    private void renderForm(@NonNull BultoWeightFormState state) {
        FragmentBultoWeightBinding views = requireBinding();
        showError(views.layoutBultoWeight, state.weightError());
        showError(views.layoutBultoVolume, state.volumeError());
    }

    private void renderVolume(@Nullable BigDecimal volume) {
        requireBinding().inputBultoVolume.setText(
                volume != null ? formatDecimal(volume, BultoMeasures.VOLUME_SCALE) : null);
    }

    private void focusFirstInvalidField() {
        BultoWeightFormState state = viewModel.getFormState().getValue();
        if (state == null || state.isValid()) {
            return;
        }
        FragmentBultoWeightBinding views = requireBinding();
        EditText field;
        if (state.weightError() != null) {
            field = views.inputBultoWeight;
        } else if (textOf(views.inputBultoHeight).isEmpty()) {
            field = views.inputBultoHeight;
        } else if (textOf(views.inputBultoWidth).isEmpty()) {
            field = views.inputBultoWidth;
        } else if (textOf(views.inputBultoDepth).isEmpty()) {
            field = views.inputBultoDepth;
        } else {
            field = views.inputBultoHeight;
        }
        field.requestFocus();
        field.setSelection(field.length());
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

    /** Con el separador decimal del idioma del usuario (12,0 en español) y sin separador de miles. */
    @NonNull
    private static String formatDecimal(@NonNull BigDecimal value, int decimals) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(value);
    }

    @NonNull
    private static String textOf(@NonNull EditText input) {
        Editable text = input.getText();
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
    private FragmentBultoWeightBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de peso y medidas no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
