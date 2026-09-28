package com.redur.electra.ui.login;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.util.Validations;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.LoginRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final MutableLiveData<LoginFormState> formState = new MutableLiveData<>(LoginFormState.EMPTY);
    private final MutableLiveData<UiState> loginState = new MutableLiveData<>(new UiState.Idle());

    private final LoginRepository repository;

    @Nullable
    private Cancellable pendingLogin;

    @Inject
    public LoginViewModel(LoginRepository repository) {
        this.repository = repository;
    }

    /** Estado del formulario */
    public LiveData<LoginFormState> getFormState() {
        return formState;
    }

    /** Estado de la petición (Idle → Loading → Success/Error). */
    public LiveData<UiState> getLoginState() {
        return loginState;
    }

    public void onUsernameChanged() {
        LoginFormState current = currentState();
        if (current.usernameError() != null) {
            formState.setValue(new LoginFormState(null, current.passwordError()));
        }
    }

    public void onPasswordChanged() {
        LoginFormState current = currentState();
        if (current.passwordError() != null) {
            formState.setValue(new LoginFormState(current.usernameError(), null));
        }
    }

    public void onLoginClicked(@Nullable String username, @Nullable String password) {
        // Evita peticiones duplicadas mientras hay una en curso
        if (loginState.getValue() instanceof UiState.Loading) {
            return;
        }

        Timber.d("Iniciamos la validacion de los datos del form");
        // Comprobamos el status del formulario para comprobar si estan los campos obligatorios correctos
        LoginFormState validated = new LoginFormState(
               username == null || Validations.isBlank(username) ? R.string.login_error_username_required : null,
                password == null || Validations.isBlank(password) ? R.string.login_error_password_required : null
        );
        formState.setValue(validated);

        // Si la validacion es incorrecta marcamos el estado como pendiente
        if (!validated.isValid()) {
            loginState.setValue(new UiState.Idle());
            return;
        }

        // Cambiamos el estado a loading para indicar que se esta ejecutando la llamada
        loginState.setValue(new UiState.Loading());

        pendingLogin = repository.login(username.trim(), password, new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull User user) {
                pendingLogin = null;
                loginState.setValue(new UiState.Success());
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingLogin = null;
                loginState.setValue(new UiState.Error(ErrorUiMapper.toUiText(error)));
            }
        });
    }

    @Override
    protected void onCleared() {
        // Evita que la respuesta llegue a un ViewModel ya destruido
        if (pendingLogin != null) {
            pendingLogin.cancel();
            pendingLogin = null;
        }
    }

    private LoginFormState currentState() {
        LoginFormState state = formState.getValue();
        return state != null ? state : LoginFormState.EMPTY;
    }
}
