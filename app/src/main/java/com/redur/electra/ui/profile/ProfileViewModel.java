package com.redur.electra.ui.profile;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.di.IoExecutor;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.LogRepository;
import com.redur.electra.data.session.UserSession;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<UiState> logShareState = new MutableLiveData<>(new UiState.Idle());

    private static final int MENU_CAMBIO_PLAZA = 4;
    private static final int PERMISO_EJECUTAR = 1;

    private static final List<String> PLAZAS_FIJAS = List.of("CTR");

    private final UserSession session;
    private final LogRepository logRepository;
    private final Executor ioExecutor;

    /** Fichero listo para compartir mientras el estado es Success. */
    @Nullable
    private volatile File logFile;

    @Inject
    public ProfileViewModel(UserSession session, LogRepository logRepository,
                            @IoExecutor Executor ioExecutor) {
        this.session = session;
        this.logRepository = logRepository;
        this.ioExecutor = ioExecutor;
    }

    /** Usuario conectado. Null si la sesión se ha perdido (p. ej. el sistema mató el proceso). */
    @Nullable
    public User getUser() {
        return session.getUser();
    }

    /**
     * Preparación del envío del log: Idle → Loading → Success | Error. Success y Error son de un
     * solo uso: la UI los atiende y llama a {@link #onLogShareHandled()}.
     */
    public LiveData<UiState> getLogShareState() {
        return logShareState;
    }

    public void onSendLogClicked() {
        // Una pulsación repetida mientras se busca el fichero no lanza una segunda búsqueda
        if (logShareState.getValue() instanceof UiState.Loading) {
            return;
        }
        logShareState.setValue(new UiState.Loading());
        ioExecutor.execute(() -> {
            File file = logRepository.findCurrentLogFile();
            if (file == null) {
                logShareState.postValue(new UiState.Error(new UiText.Res(R.string.profile_log_unavailable)));
                return;
            }
            logFile = file;
            logShareState.postValue(new UiState.Success());
        });
    }

    public boolean canChangePlaza() {
        User user = session.getUser();
        return user != null && (user.hasMenuActive(MENU_CAMBIO_PLAZA) || user.hasPermission(PERMISO_EJECUTAR) || PLAZAS_FIJAS.contains(user.plazaId()));
    }

    @Nullable
    public File getLogFile() {
        return logFile;
    }

    public void onLogShareHandled() {
        logFile = null;
        logShareState.setValue(new UiState.Idle());
    }
}
