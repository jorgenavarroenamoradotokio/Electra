package com.redur.electra.ui.label;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.redur.electra.R;
import com.redur.electra.databinding.FragmentLabelPreviewBinding;
import com.redur.electra.databinding.ItemLabelPageBinding;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Vista previa de la etiqueta tal como saldrá impresa. Mientras el backend la genera avisa de
 * ello; si falla explica qué ha pasado y permite reintentar.
 */
@AndroidEntryPoint
public class LabelPreviewFragment extends Fragment {

    @Nullable
    private FragmentLabelPreviewBinding binding;
    private LabelPreviewViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LabelPreviewViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLabelPreviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireBinding().buttonLabelRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@NonNull LabelPreviewState state) {
        FragmentLabelPreviewBinding views = requireBinding();
        boolean wasGenerating = views.layoutLabelGenerating.getVisibility() == View.VISIBLE;
        views.layoutLabelGenerating.setVisibility(
                state instanceof LabelPreviewState.Generating ? View.VISIBLE : View.GONE);
        views.layoutLabelError.setVisibility(
                state instanceof LabelPreviewState.Failed ? View.VISIBLE : View.GONE);

        if (state instanceof LabelPreviewState.Failed failed) {
            views.textLabelError.setText(failed.message().resolve(requireContext()));
        }
        if (state instanceof LabelPreviewState.Ready ready) {
            showPages(ready.pages(), wasGenerating);
        } else {
            views.scrollLabelPages.setVisibility(View.GONE);
            views.containerLabelPages.removeAllViews();
        }
    }

    /**
     * Recién generada, la etiqueta aparece con un fundido corto: llega, no salta. Al recrear la
     * vista (p. ej. al girar) se muestra directamente.
     */
    private void showPages(@NonNull List<Bitmap> pages, boolean animate) {
        FragmentLabelPreviewBinding views = requireBinding();
        if (views.scrollLabelPages.getVisibility() == View.VISIBLE) {
            return;
        }
        views.containerLabelPages.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < pages.size(); i++) {
            ItemLabelPageBinding page = ItemLabelPageBinding.inflate(inflater, views.containerLabelPages, true);
            page.imageLabelPage.setImageBitmap(pages.get(i));
            page.imageLabelPage.setContentDescription(
                    getString(R.string.label_preview_page_description, i + 1, pages.size()));
        }
        views.scrollLabelPages.setVisibility(View.VISIBLE);
        if (!animate) {
            return;
        }
        views.scrollLabelPages.setAlpha(0f);
        views.scrollLabelPages.animate()
                .alpha(1f)
                .setDuration(getResources().getInteger(R.integer.motion_duration_layout))
                .start();
    }

    @NonNull
    private FragmentLabelPreviewBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la vista previa no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.scrollLabelPages.animate().cancel();
        }
        binding = null;
    }
}
