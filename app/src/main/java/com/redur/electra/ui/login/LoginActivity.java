package com.redur.electra.ui.login;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.databinding.ActivityLoginBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupListeners();
        viewModel.getFormState().observe(this, this::renderForm);
        viewModel.getLoginState().observe(this, this::renderLogin);
    }

    private void setupListeners() {
        // Accion del btn de iniciar sesion
        binding.buttonLogin.setOnClickListener(view -> submitLogin());

        // Accion del teclado para enviar datos frm
        binding.inputPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin();
                return true;
            }
            return false;
        });

        // Animaciones para el focus
        binding.inputUsername.addTextChangedListener(afterChanged(viewModel::onUsernameChanged));
        binding.inputPassword.addTextChangedListener(afterChanged(viewModel::onPasswordChanged));

        // Accion para recordar olvidar password
        binding.buttonForgotPassword.setOnClickListener(view -> Snackbar.make(binding.getRoot(), R.string.login_forgot_password_unavailable, Snackbar.LENGTH_LONG).show());
    }

    private void submitLogin() {
        viewModel.onLoginClicked(textOf(binding.inputUsername.getText()), textOf(binding.inputPassword.getText()));

        // El foco solo se mueve como respuesta al envío, nunca al re-renderizar mientras se escribe
        LoginFormState state = viewModel.getFormState().getValue();
        if (state != null && !state.isValid()) {
            focusFirstInvalidField(state);
        } else {
            hideKeyboard();
        }
    }

    private void focusFirstInvalidField(LoginFormState state) {
        View field = state.usernameError() != null ? binding.inputUsername : binding.inputPassword;
        field.requestFocus();
        WindowCompat.getInsetsController(getWindow(), field).show(WindowInsetsCompat.Type.ime());
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void renderForm(LoginFormState state) {
        // Solo se anima la aparición de errores (tras enviar); nunca mientras el usuario escribe
        if (!state.isValid()) {
            TransitionManager.beginDelayedTransition(binding.containerForm, errorTransition());
        }
        showError(binding.layoutUsername, state.usernameError());
        showError(binding.layoutPassword, state.passwordError());
    }

    private void renderLogin(UiState state) {
        boolean isLoading = state instanceof UiState.Loading;
        binding.inputUsername.setEnabled(!isLoading);
        binding.inputPassword.setEnabled(!isLoading);
        binding.buttonLogin.setEnabled(!isLoading);
        // El indicador ocupa el centro del botón: su texto se oculta para que no se solapen
        binding.buttonLogin.setText(isLoading ? null : getString(R.string.login_button));
        binding.progress.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);

        if (state instanceof UiState.Error) {
            // Pendiente: traducir codeError a mensajes concretos cuando el backend defina sus códigos
            Snackbar.make(binding.getRoot(), R.string.login_error_generic, Snackbar.LENGTH_LONG).show();
        }

        // Pendiente: UiState.Success → navegar a la pantalla principal cuando exista
    }

    /**
     * setError(null) mantiene reservado el hueco del error: al corregir un campo el formulario no
     * salta bajo el dedo del usuario.
     */
    private void showError(TextInputLayout layout, @Nullable Integer errorRes) {
        layout.setError(errorRes != null ? getString(errorRes) : null);
    }

    private Transition errorTransition() {
        return new ChangeBounds()
                .setDuration(getResources().getInteger(R.integer.motion_duration_layout))
                .setInterpolator(new DecelerateInterpolator());
    }

    private static String textOf(@Nullable Editable editable) {
        return editable != null ? editable.toString() : "";
    }

    private TextWatcher afterChanged(Runnable action) {
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
}
