package com.redur.electra.ui.bulto.tipo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.databinding.FragmentBultoTypeBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Tipo de bulto: el usuario elige uno de la lista y puede fijarlo para las lecturas siguientes.
 * Mientras se cargan los tipos lo indica; si fallan o no hay ninguno lo explica y permite reintentar.
 * De momento asignar y cancelar solo confirman la acción con un aviso.
 */
@AndroidEntryPoint
public class BultoTypeFragment extends Fragment {

    @Nullable
    private FragmentBultoTypeBinding binding;
    private BultoTypeViewModel viewModel;
    @Nullable
    private BultoTypeAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoTypeViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoTypeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoTypeBinding views = requireBinding();
        adapter = new BultoTypeAdapter(viewModel::onTypeSelected);
        views.recyclerBultoTypes.setAdapter(adapter);

        views.buttonBultoTypesRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        views.checkBultoTypeFixed.setOnCheckedChangeListener(
                (button, checked) -> viewModel.onFixedChanged(checked));
        views.buttonBultoTypeConfirm.setOnClickListener(v -> {
            if (viewModel.canConfirm()) {
                showToast(R.string.bulto_type_confirmed);
            }
        });
        views.buttonBultoTypeCancel.setOnClickListener(v -> showToast(R.string.bulto_type_canceled));

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSelectedType().observe(getViewLifecycleOwner(), this::renderSelection);
        viewModel.getFixedForNextReadings().observe(getViewLifecycleOwner(),
                fixed -> requireBinding().checkBultoTypeFixed.setChecked(Boolean.TRUE.equals(fixed)));
    }

    private void render(@NonNull BultoTypeState state) {
        FragmentBultoTypeBinding views = requireBinding();
        views.layoutBultoTypesLoading.setVisibility(
                state instanceof BultoTypeState.Loading ? View.VISIBLE : View.GONE);
        views.cardBultoTypes.setVisibility(
                state instanceof BultoTypeState.Ready ? View.VISIBLE : View.GONE);
        views.layoutBultoTypesError.setVisibility(
                state instanceof BultoTypeState.Failed || state instanceof BultoTypeState.Empty
                        ? View.VISIBLE : View.GONE);
        views.checkBultoTypeFixed.setEnabled(state instanceof BultoTypeState.Ready);

        if (state instanceof BultoTypeState.Ready ready && adapter != null) {
            adapter.submitList(ready.types());
        } else if (state instanceof BultoTypeState.Failed failed) {
            views.textBultoTypesErrorTitle.setText(R.string.bulto_type_error_title);
            views.textBultoTypesError.setText(failed.message().resolve(requireContext()));
        } else if (state instanceof BultoTypeState.Empty) {
            views.textBultoTypesErrorTitle.setText(R.string.bulto_type_empty_title);
            views.textBultoTypesError.setText(R.string.bulto_type_empty);
        }
        views.buttonBultoTypeConfirm.setEnabled(viewModel.canConfirm());
    }

    private void renderSelection(@Nullable BultoType selected) {
        if (adapter != null) {
            adapter.setSelectedCode(selected != null ? selected.code() : null);
        }
        requireBinding().buttonBultoTypeConfirm.setEnabled(viewModel.canConfirm());
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private FragmentBultoTypeBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista del tipo de bulto no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerBultoTypes.setAdapter(null);
        }
        adapter = null;
        binding = null;
    }
}
