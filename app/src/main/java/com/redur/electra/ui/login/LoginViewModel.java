package com.redur.electra.ui.login;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.util.Validations;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final MutableLiveData<LoginFormState> formState = new MutableLiveData<>(LoginFormState.EMPTY);
    private final MutableLiveData<UiState> loginState = new MutableLiveData<>(new UiState.Idle());

    @Inject
    public LoginViewModel() {
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

        // Comprobamos el status del formulario para comprobar si estan los campos obligatorios correctos
        LoginFormState validated = new LoginFormState(
                Validations.isBlank(username) ? R.string.login_error_username_required : null,
                Validations.isBlank(password) ? R.string.login_error_password_required : null
        );
        formState.setValue(validated);

        // En caso de no estarlo marcamos el proceso global como pendiente
        if (!validated.isValid()) {
            loginState.setValue(new UiState.Idle());
            return;
        }

        // Cambiamos el estado a loading para indicar que se esta ejecutando la llamada
        loginState.setValue(new UiState.Loading());
        // Pendiente: autenticar contra el backend cuando exista el contrato de la API. La llamada del
        // Repository irá fuera del hilo principal y publicará Success o
        // Error(ErrorUiMapper.toUiText(appError)) con postValue.
        // Mientras no exista, se resuelve al instante y el progreso no llega a verse.

        // Cambiamos a estado correcto porque la llamada fue correcta
        loginState.setValue(new UiState.Success());
    }

    private LoginFormState currentState() {
        LoginFormState state = formState.getValue();
        return state != null ? state : LoginFormState.EMPTY;
    }


}
