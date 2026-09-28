package com.redur.electra.ui.logout;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.core.ui.UiState;
import com.redur.electra.data.repository.LoginRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LogoutViewModel extends ViewModel {

    private final MutableLiveData<UiState> logoutState = new MutableLiveData<>(new UiState.Idle());

    private final LoginRepository repository;

    @Inject
    public LogoutViewModel(LoginRepository repository) {
        this.repository = repository;
    }

    /** Estado del cierre de sesión (Idle → Success). */
    public LiveData<UiState> getLogoutState() {
        return logoutState;
    }

    public void onLogoutConfirmed() {
        // Una pulsación repetida no debe volver a disparar la navegación
        if (logoutState.getValue() instanceof UiState.Success) {
            return;
        }
        repository.logout();
        logoutState.setValue(new UiState.Success());
    }
}
