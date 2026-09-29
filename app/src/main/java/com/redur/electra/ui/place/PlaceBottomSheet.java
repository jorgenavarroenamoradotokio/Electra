package com.redur.electra.ui.place;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.data.model.place.Place;
import com.redur.electra.databinding.SheetChangePlazaBinding;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Cambio de plaza como hoja inferior. Al aplicarse el cambio publica {@link #RESULT_KEY} con la
 * nueva plaza en {@link #RESULT_PLAZA_ID} y se cierra; quien la abre refresca sus datos.
 */
@AndroidEntryPoint
public class PlaceBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "PlaceBottomSheet.result";
    public static final String RESULT_PLAZA_ID = "plazaId";

    private static final String TAG = "PlaceBottomSheet";

    @Nullable
    private SheetChangePlazaBinding binding;
    private PlaceViewModel viewModel;
    @Nullable
    private PlaceAdapter adapter;

    /** No apila una segunda hoja si ya hay una visible (p. ej. doble toque en el botón). */
    public static void showIfNotShown(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            new PlaceBottomSheet().show(fragmentManager, TAG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PlaceViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetChangePlazaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetChangePlazaBinding views = requireBinding();

        views.textChangePlazaMessage.setText(getString(R.string.change_plaza_message, currentPlazaLabel()));

        PlaceAdapter placeAdapter = new PlaceAdapter(requireContext());
        adapter = placeAdapter;
        views.inputPlaza.setAdapter(placeAdapter);
        views.inputPlaza.setOnItemClickListener((parent, item, position, id) -> {
            Place place = placeAdapter.getItem(position);
            if (place != null) {
                viewModel.onPlaceSelected(place);
            }
        });
        // Cerrar el desplegable sin elegir deja el campo vacío: sin foco, el hint vuelve a su tamaño
        views.inputPlaza.setOnDismissListener(() -> {
            if (views.inputPlaza.length() == 0) {
                views.inputPlaza.clearFocus();
            }
        });
        views.buttonCancelPlaza.setOnClickListener(v -> dismiss());

        viewModel.getPlaces().observe(getViewLifecycleOwner(), this::renderPlaces);
        viewModel.getLoadState().observe(getViewLifecycleOwner(), state -> render());
        viewModel.getSelectedPlace().observe(getViewLifecycleOwner(), this::renderSelection);
        viewModel.getChangePlaceState().observe(getViewLifecycleOwner(), this::renderChange);
    }

    @NonNull
    private String currentPlazaLabel() {
        String current = viewModel.getCurrentPlazaId();
        return current != null && !current.isBlank() ? current : getString(R.string.profile_plaza_empty);
    }

    private void renderPlaces(@NonNull List<Place> places) {
        if (adapter != null) {
            adapter.setPlaces(places);
        }
    }

    private void renderSelection(@Nullable Place place) {
        if (place != null && adapter != null) {
            // Sin filtrar: el desplegable debe seguir ofreciendo todas las plazas
            requireBinding().inputPlaza.setText(adapter.format(place), false);
        }
        render();
    }

    private void renderChange(@NonNull UiState state) {
        if (state instanceof UiState.Success) {
            Bundle result = new Bundle();
            result.putString(RESULT_PLAZA_ID, viewModel.getCurrentPlazaId());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            dismiss();
            return;
        }
        render();
    }

    /** Pinta controles, avisos y acción principal a partir del estado de carga y de cambio. */
    private void render() {
        SheetChangePlazaBinding views = requireBinding();
        UiState load = viewModel.getLoadState().getValue();
        UiState change = viewModel.getChangePlaceState().getValue();
        boolean loadingPlaces = load instanceof UiState.Loading;
        boolean loadFailed = load instanceof UiState.Error;
        boolean changing = change instanceof UiState.Loading;

        views.layoutPlaza.setEnabled(!loadingPlaces && !loadFailed && !changing);
        views.layoutPlaza.setHelperText(loadingPlaces ? getString(R.string.change_plaza_loading_list) : null);
        if (load instanceof UiState.Error error) {
            views.layoutPlaza.setError(error.message().resolve(requireContext()));
        } else if (change instanceof UiState.Error error) {
            views.layoutPlaza.setError(error.message().resolve(requireContext()));
        } else {
            views.layoutPlaza.setError(null);
        }

        // Si la carga falla la acción principal pasa a ser reintentar, en el mismo sitio
        if (loadFailed) {
            views.buttonConfirmPlaza.setText(R.string.change_plaza_retry);
            views.buttonConfirmPlaza.setEnabled(true);
            views.buttonConfirmPlaza.setOnClickListener(v -> viewModel.onRetryLoadClicked());
        } else {
            views.buttonConfirmPlaza.setText(changing ? null : getString(R.string.change_plaza_confirm));
            views.buttonConfirmPlaza.setEnabled(viewModel.canConfirm());
            views.buttonConfirmPlaza.setOnClickListener(v -> viewModel.onConfirmClicked());
        }
        views.progressChangePlaza.setVisibility(changing ? View.VISIBLE : View.INVISIBLE);

        // Con el cambio en curso no se puede cerrar: el resultado debe llegar a la sesión
        views.buttonCancelPlaza.setEnabled(!changing);
        setCancelable(!changing);
    }

    @NonNull
    private SheetChangePlazaBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la hoja no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        binding = null;
    }
}
