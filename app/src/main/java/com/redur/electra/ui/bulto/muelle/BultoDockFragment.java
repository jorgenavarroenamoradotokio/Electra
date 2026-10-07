package com.redur.electra.ui.bulto.muelle;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoDock;
import com.redur.electra.databinding.FragmentBultoDockBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Puerta de salida: el operario lee el CB del bulto con el escáner y ve en grande el muelle por
 * el que sale. El muelle queda a la vista hasta leer el bulto siguiente; cancelar lo descarta y
 * vacía el campo.
 */
@AndroidEntryPoint
public class BultoDockFragment extends Fragment {

    @Nullable
    private FragmentBultoDockBinding binding;
    private BultoDockViewModel viewModel;
    /** Para animar solo la llegada de un muelle nuevo, no cada re-renderizado. */
    @Nullable
    private BultoDockState renderedState;

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

        // El CB solo se lee con el escáner: el campo recibe el foco sin abrir el teclado en pantalla
        views.inputBultoDockBarcode.setShowSoftInputOnFocus(false);
        views.inputBultoDockBarcode.setOnEditorActionListener((v, actionId, event) ->
                onEditorAction(actionId, event, this::onBarcodeScanned));
        views.buttonBultoDockRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        views.buttonBultoDockCancel.setOnClickListener(v -> cancel());

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        if (savedInstanceState == null) {
            views.inputBultoDockBarcode.requestFocus();
        }
    }

    private void onBarcodeScanned() {
        FragmentBultoDockBinding views = requireBinding();
        CharSequence text = views.inputBultoDockBarcode.getText();
        viewModel.onBarcodeScanned(text != null ? text.toString() : null);
        // El CB queda a la vista; la lectura siguiente del escáner lo sustituye en lugar de añadirse
        views.inputBultoDockBarcode.selectAll();
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
        views.cardBultoDock.setVisibility(
                state instanceof BultoDockState.Found ? View.VISIBLE : View.GONE);
        views.layoutBultoDockError.setVisibility(
                state instanceof BultoDockState.NotFound || state instanceof BultoDockState.Failed
                        ? View.VISIBLE : View.GONE);

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

    private void renderDock(@NonNull BultoDock dock, boolean isNew) {
        FragmentBultoDockBinding views = requireBinding();
        views.textBultoDockCode.setText(dock.code());
        views.textBultoDockDestination.setText(getString(R.string.bulto_dock_destination,
                dock.place(), dock.postalCode(), dock.country()));
        views.cardBultoDock.setContentDescription(getString(R.string.bulto_dock_description,
                dock.code(), dock.place(), dock.postalCode(), dock.country()));
        if (isNew) {
            animateArrival(views.cardBultoDock);
        }
    }

    /** Llegada breve y sutil: confirma que el muelle es de la lectura que se acaba de hacer. */
    private void animateArrival(@NonNull View card) {
        float startScale = getResources().getFloat(R.dimen.press_scale);
        card.animate().cancel();
        card.setAlpha(0f);
        card.setScaleX(startScale);
        card.setScaleY(startScale);
        card.animate()
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
        boolean imeAction = actionId == EditorInfo.IME_ACTION_SEARCH;
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
            binding.cardBultoDock.animate().cancel();
        }
        renderedState = null;
        binding = null;
    }
}
