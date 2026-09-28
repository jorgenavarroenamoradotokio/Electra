package com.redur.electra.ui;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.UserSession;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MainViewModel extends ViewModel {

    private final UserSession session;

    @Inject
    public MainViewModel(UserSession session) {
        this.session = session;
    }

    /** Nombre con el que se presenta al usuario: el completo si existe, si no su usuario. */
    @Nullable
    public String getUserDisplayName() {
        User user = session.getUser();
        if (user == null) {
            return null;
        }
        String fullName = user.fullName();
        return fullName != null && !fullName.isBlank() ? fullName : user.username();
    }
}
