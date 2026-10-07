package com.redur.electra.ui.logout;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.databinding.SheetLogoutBinding;
import com.redur.electra.ui.login.LoginActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Confirmación de cierre de sesión. Se presenta como hoja inferior: mantiene visible el contexto
 * de la pantalla actual y se descarta con "atrás", deslizando o tocando fuera.
 */
@AndroidEntryPoint
public class LogoutBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "LogoutBottomSheet";

    @Nullable
    private SheetLogoutBinding binding;
    private LogoutViewModel viewModel;

    /** No apila una segunda hoja si ya hay una visible (p. ej. "atrás" pulsado dos veces). */
    public static void showIfNotShown(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            new LogoutBottomSheet().show(fragmentManager, TAG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LogoutViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetLogoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetLogoutBinding views = requireBinding();
        views.buttonConfirmLogout.setOnClickListener(v -> viewModel.onLogoutConfirmed());
        views.buttonCancelLogout.setOnClickListener(v -> dismiss());
        viewModel.getLogoutState().observe(getViewLifecycleOwner(), this::renderLogout);
    }

    private void renderLogout(UiState state) {
        if (state instanceof UiState.Success) {
            // Evita toques adicionales durante la transición al login
            SheetLogoutBinding views = requireBinding();
            views.buttonConfirmLogout.setEnabled(false);
            views.buttonCancelLogout.setEnabled(false);
            navigateToLogin();
        }
    }

    /** Se vacía la pila: tras cerrar sesión, "atrás" no puede devolver a pantallas autenticadas. */
    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @NonNull
    private SheetLogoutBinding requireBinding() {
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
