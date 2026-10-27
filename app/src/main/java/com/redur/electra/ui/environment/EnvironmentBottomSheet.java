package com.redur.electra.ui.environment;

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
import com.redur.electra.databinding.SheetEnvironmentBinding;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Indica a qué entorno (sistema) está conectado el usuario, leído de la sesión. Hoja inferior
 * informativa: mantiene visible la pantalla actual y se descarta con "atrás", deslizando o tocando fuera.
 */
@AndroidEntryPoint
public class EnvironmentBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "EnvironmentBottomSheet";

    @Nullable
    private SheetEnvironmentBinding binding;
    private EnvironmentViewModel viewModel;

    /** No apila una segunda hoja si ya hay una visible (p. ej. doble toque en el icono). */
    public static void showIfNotShown(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            Timber.i("Abrimos pantalla de entorno conectado");
            new EnvironmentBottomSheet().show(fragmentManager, TAG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EnvironmentViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetEnvironmentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetEnvironmentBinding views = requireBinding();
        views.buttonCloseEnvironment.setOnClickListener(v -> dismiss());
        renderEnvironment(viewModel.getEnvironment());
    }

    /** Sin entorno en la sesión se explica en lugar de dejar el nombre vacío. */
    private void renderEnvironment(@Nullable String environment) {
        SheetEnvironmentBinding views = requireBinding();
        boolean known = environment != null;
        views.textEnvironmentName.setText(known ? environment : getString(R.string.environment_unknown));
        views.textEnvironmentMessage.setVisibility(known ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private SheetEnvironmentBinding requireBinding() {
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
