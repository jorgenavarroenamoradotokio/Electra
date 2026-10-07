package com.redur.electra.ui.login;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.redur.electra.BuildConfig;
import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.databinding.ActivityLoginBinding;
import com.redur.electra.ui.MainActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // La franja de marca es oscura en ambos temas: iconos de la status bar siempre claros
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Vinculamos la vista para controlar estados y eventos
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        // Iniciamos la transicion de entrada del formulario
        if (savedInstanceState == null) {
            playEnterAnimation();
        }

        // Insertamos la version de la app que esta usando el usuario
        binding.textVersion.setText(getString(R.string.login_version, BuildConfig.VERSION_NAME));

        // Ajustamos las dimensiones y comportamiento de scroll cuando aparece el teclado
        applyWindowInsets();

        // Asociamos los eventos a los diferentes btn de click de la pantalla
        setupListeners();

        // Eventos de errores de los formularios
        viewModel.getFormState().observe(this, this::renderForm);
        viewModel.getLoginState().observe(this, this::renderLogin);
    }

    /**
     * Pantalla de borde a borde: la franja de marca se extiende bajo la status bar y el scroll se
     * acorta con el teclado. Se usa margen y no padding porque NestedScrollView calcula lo visible
     * con su altura total; al abrirse el teclado se desplaza además el campo con foco completo.
     */
    private void applyWindowInsets() {
        int headerPaddingTop = binding.header.getPaddingTop();
        int formPaddingBottom = binding.containerForm.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) root.getLayoutParams();
            boolean imeOpened = ime.bottom > 0 && params.bottomMargin == 0;

            params.bottomMargin = ime.bottom;
            root.setLayoutParams(params);
            root.setPadding(bars.left, 0, bars.right, 0);

            binding.header.setPadding(
                    binding.header.getPaddingLeft(),
                    headerPaddingTop + bars.top,
                    binding.header.getPaddingRight(),
                    binding.header.getPaddingBottom());

            // Con el teclado abierto, su inset ya cubre la barra de navegación
            binding.containerForm.setPadding(
                    binding.containerForm.getPaddingLeft(),
                    binding.containerForm.getPaddingTop(),
                    binding.containerForm.getPaddingRight(),
                    formPaddingBottom + Math.max(0, bars.bottom - ime.bottom));

            // Tras el layout con el nuevo margen: antes, el scroll aún no conoce su nueva altura
            if (imeOpened) {
                root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        view.removeOnLayoutChangeListener(this);
                        scrollFocusedFieldIntoView();
                    }
                });
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Muestra el campo completo (incluida su línea de error), no solo el cursor. */
    private void scrollFocusedFieldIntoView() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        View field = focus;
        while (field.getParent() instanceof View parent && !(field instanceof TextInputLayout)) {
            field = parent;
        }
        View target = field instanceof TextInputLayout ? field : focus;
        target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()));
    }

    /**
     *  Animacion de entrada de panel, llega desde abajo, breve y con ease-out (Solo en la primera apertura).
     */
    private void playEnterAnimation() {
        View panel = binding.containerForm;
        panel.setAlpha(0f);
        panel.setTranslationY(getResources().getDimension(R.dimen.motion_enter_offset));
        panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(getResources().getInteger(R.integer.motion_duration_enter))
                .setInterpolator(new DecelerateInterpolator());
    }

    /**
     * Vinculamos los elementos de la pantalla con sus posibles eventos
     */
    private void setupListeners() {
        // Accion del btn de iniciar sesion
        binding.buttonLogin.setOnClickListener(view -> submitLogin());

        // Control del btn siguiente del teclado, para que cuando sea terminar se envie la informacion
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

    /**
     * Ocultamos el teclado
     */
    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    /**
     * Animacion del los campos cuando existe errores. nunca mientras el usuario escribe
     */
    private void renderForm(LoginFormState state) {

        if (!state.isValid()) {
            TransitionManager.beginDelayedTransition(binding.containerForm, layoutTransition());
        }
        showError(binding.layoutUsername, state.usernameError());
        showError(binding.layoutPassword, state.passwordError());
    }

    /**
     * Control de los campos del frm para definir el comportamiento cuando se detectas el estado de Loading
     */
    private void renderLogin(UiState state) {
        boolean isLoading = state instanceof UiState.Loading;

        // Desbloqueamos los componentes cuando el estado no sea Loading
        binding.inputUsername.setEnabled(!isLoading);
        binding.inputPassword.setEnabled(!isLoading);
        binding.buttonLogin.setEnabled(!isLoading);

        // Mostramos el icono de cargando cuando el estado sea Loading, en caso contrario mostramos de nuevo el texto de inicio sesion
        binding.buttonLogin.setText(isLoading ? null : getString(R.string.login_button));
        binding.progress.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);

        // Durante un reintento el aviso anterior se mantiene hasta conocer el nuevo resultado
        if (state instanceof UiState.Error error) {
            showLoginError(error.message());
        } else if (!isLoading) {
            showLoginError(null);
        }

        // Navegamos a la pantalla de incio cuando la peticion es correcta
        if (state instanceof UiState.Success) {
            navigateToMain();
        }
    }

    /**
     * Cerramos la activity del login y navegamos directamente a Main
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * setError(null) mantiene reservado el hueco del error: al corregir un campo el formulario no
     * salta bajo el dedo del usuario.
     */
    private void showError(TextInputLayout layout, @Nullable Integer errorRes) {
        layout.setError(errorRes != null ? getString(errorRes) : null);
    }

    /**
     * El aviso permanece mientras se reintenta: ocultarlo en Loading lanzaba un fade-out que, si la
     * respuesta llegaba antes de terminar, quedaba interrumpido y dejaba la tarjeta invisible.
     */
    private void showLoginError(@Nullable UiText message) {
        // El texto se fija antes de mostrarse para que TalkBack anuncie el mensaje correcto
        if (message != null) {
            binding.textLoginError.setText(message.resolve(this));
        }
        int visibility = message != null ? View.VISIBLE : View.GONE;
        if (binding.cardLoginError.getVisibility() != visibility) {
            TransitionManager.beginDelayedTransition(binding.containerForm, layoutTransition());
            binding.cardLoginError.setVisibility(visibility);
        }
    }

    private Transition layoutTransition() {
        return new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(new Fade())
                .addTransition(new ChangeBounds())
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
