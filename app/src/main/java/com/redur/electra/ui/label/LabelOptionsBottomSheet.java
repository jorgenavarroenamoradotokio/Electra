package com.redur.electra.ui.label;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.R;
import com.redur.electra.databinding.SheetLabelOptionsBinding;

import timber.log.Timber;

/**
 * Qué hacer con la etiqueta, como hoja inferior: previsualizarla o enviarla a la impresora.
 * Previsualizar abre la pantalla de vista previa e imprimir la hoja de impresión Bluetooth; las
 * dos generan la etiqueta.
 */
public class LabelOptionsBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "LabelOptionsBottomSheet.result";

    private static final NavOptions REPLACE_SHEET = new NavOptions.Builder()
            .setPopUpTo(R.id.nav_label_options, true)
            .build();

    @Nullable
    private SheetLabelOptionsBinding binding;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetLabelOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetLabelOptionsBinding views = requireBinding();
        views.buttonLabelPreview.setOnClickListener(v -> {
            Timber.i("[ACCION] Etiqueta: vista previa");
            openFromSheet(R.id.nav_label_preview);
        });
        views.buttonLabelPrint.setOnClickListener(v -> {
            Timber.i("[ACCION] Etiqueta: imprimir");
            openFromSheet(R.id.nav_label_print);
        });
        views.buttonLabelCancel.setOnClickListener(v -> {
            Timber.i("[ACCION] Etiqueta: cancelar");
            dismiss();
        });
    }

    /**
     * Abre la vista previa o la hoja de impresión en lugar de esta (no se apilan dos hojas). Un
     * doble toque no abre dos destinos.
     */
    private void openFromSheet(@IdRes int destinationId) {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.nav_label_options) {
            navController.navigate(destinationId, null, REPLACE_SHEET);
        }
    }

    @NonNull
    private SheetLabelOptionsBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la hoja no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
