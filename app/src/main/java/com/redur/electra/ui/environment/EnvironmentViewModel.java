package com.redur.electra.ui.environment;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.UserSession;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class EnvironmentViewModel extends ViewModel {

    private final UserSession session;

    @Inject
    public EnvironmentViewModel(UserSession session) {
        this.session = session;
    }

    /** Entorno al que está conectado el usuario según el login; null sin sesión o si no vino informado. */
    @Nullable
    public String getEnvironment() {
        User user = session.getUser();
        if (user == null || user.environment() == null || user.environment().isBlank()) {
            return null;
        }
        return user.environment().strip();
    }
}
