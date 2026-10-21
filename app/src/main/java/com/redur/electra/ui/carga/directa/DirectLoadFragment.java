package com.redur.electra.ui.carga.directa;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.databinding.FragmentDirectLoadBinding;
import com.redur.electra.databinding.ViewDirectLoadReadingBinding;
import com.redur.electra.databinding.ViewDirectLoadSealBinding;
import com.redur.electra.ui.bulto.tipo.BultoTypePicker;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Carga con precinto: al entrar se pide el precinto (CB o tecleado) y después se leen los bultos
 * con el escáner. Precinto y tipo de bulto fijado quedan siempre a la vista en la cabecera; el tipo
 * se elige en la pantalla general de tipo de bulto, abierta como selector, y se quita desde la ficha.
 */
@AndroidEntryPoint
public class DirectLoadFragment extends Fragment {

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

        // Bultos: solo con el escáner, el campo recibe el foco sin abrir el teclado en pantalla
        reading.inputDirectLoadBarcode.setShowSoftInputOnFocus(false);
        reading.inputDirectLoadBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, EditorInfo.IME_ACTION_SEARCH, this::onBarcodeScanned));

        adapter = new DirectLoadReadingAdapter();
        reading.recyclerDirectLoadReadings.setAdapter(adapter);
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(requireContext(),
                MaterialDividerItemDecoration.VERTICAL);
        divider.setLastItemDecorated(false);
        reading.recyclerDirectLoadReadings.addItemDecoration(divider);

        emptyTypeStroke = reading.cardDirectLoadType.getStrokeColorStateList();
        emptyTypeBackground = reading.cardDirectLoadType.getCardBackgroundColor();
        emptyTypeStrokeWidth = reading.cardDirectLoadType.getStrokeWidth();
        reading.cardDirectLoadType.setOnClickListener(v -> openTypePicker());
        reading.buttonDirectLoadType.setOnClickListener(v -> openTypePicker());
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
                viewModel.onBultoTypeFixed(type);
            }
        });
    }

    private void openTypePicker() {
        Timber.i("[ACCION] Abrir selector de tipo de bulto");
        NavHostFragment.findNavController(this).navigate(R.id.nav_bulto_type_picker,
                BultoTypePicker.args(viewModel.getFixedTypeCode()));
    }

    private void submitSeal() {
        CharSequence text = requireBinding().layoutDirectLoadSeal.inputDirectLoadSeal.getText();
        viewModel.onSealEntered(text != null ? text.toString() : null);
    }

    private void onBarcodeScanned() {
        ViewDirectLoadReadingBinding views = requireBinding().layoutDirectLoadReading;
        CharSequence text = views.inputDirectLoadBarcode.getText();
        viewModel.onBarcodeScanned(text != null ? text.toString() : null);
        // El CB queda a la vista; la lectura siguiente del escáner lo sustituye en lugar de añadirse
        views.inputDirectLoadBarcode.selectAll();
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
            }
            renderHeader(reading);
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
        }
        adapter = null;
        renderedPhase = null;
        emptyTypeStroke = null;
        emptyTypeBackground = null;
        binding = null;
    }
}
