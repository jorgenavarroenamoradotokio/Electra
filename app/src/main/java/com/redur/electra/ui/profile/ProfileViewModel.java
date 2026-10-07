package com.redur.electra.ui.profile;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.LogRepository;
import com.redur.electra.data.repository.LogUploadRepository;
import com.redur.electra.data.session.UserSession;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

@HiltViewModel
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<LogSendState> logSendState = new MutableLiveData<>(new LogSendState.Idle());

    private static final int MENU_CAMBIO_PLAZA = 4;
    private static final int PERMISO_EJECUTAR = 1;

    private static final List<String> PLAZAS_FIJAS = List.of("CTR");

    /** Fallos seguidos de subida tras los que se ofrece el correo en lugar de reintentar. */
    private static final int UPLOAD_ATTEMPTS_BEFORE_EMAIL = 2;

    private final UserSession session;
    private final LogRepository logRepository;
    private final LogUploadRepository logUploadRepository;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    // Solo accedidos desde el hilo principal
    @Nullable
    private File logFile;
    @Nullable
    private Cancellable pendingUpload;
    private int failedUploads;
    private boolean cleared;

    @Inject
    public ProfileViewModel(UserSession session, LogRepository logRepository,
                            LogUploadRepository logUploadRepository,
                            @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this.session = session;
        this.logRepository = logRepository;
        this.logUploadRepository = logUploadRepository;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /** Usuario conectado. Null si la sesión se ha perdido (p. ej. el sistema mató el proceso). */
    @Nullable
    public User getUser() {
        return session.getUser();
    }

    /** Ver {@link LogSendState}. Tras atender un estado de un solo uso, llamar a {@link #onLogSendHandled()}. */
    public LiveData<LogSendState> getLogSendState() {
        return logSendState;
    }

    /** Sube el registro al servidor. También es la acción de "Reintentar". */
    @MainThread
    public void onSendLogClicked() {
        Timber.i("Iniciamos envio del log por API");
        // Una pulsación repetida durante el envío no lanza una segunda subida
        if (logSendState.getValue() instanceof LogSendState.Sending) {
            return;
        }
        logSendState.setValue(new LogSendState.Sending(0));
        ioExecutor.execute(() -> {
            File file = logRepository.findCurrentLogFile();
            mainExecutor.execute(() -> upload(file));
        });
    }

    /** Alternativa ofrecida cuando la subida ya ha fallado varias veces. */
    @MainThread
    public void onSendByEmailClicked() {
        Timber.i("Blindamos opcion de enviar por otro canal el Log");
        File file = logFile;
        if (file == null) {
            return;
        }
        failedUploads = 0;
        logSendState.setValue(new LogSendState.ReadyToEmail(file));
    }

    public void onLogSendHandled() {
        logSendState.setValue(new LogSendState.Idle());
    }

    public boolean canChangePlaza() {
        User user = session.getUser();
        return user != null && (user.hasMenuActive(MENU_CAMBIO_PLAZA) || user.hasPermission(PERMISO_EJECUTAR) || PLAZAS_FIJAS.contains(user.plazaId()));
    }

    @Override
    protected void onCleared() {
        // Evita que el resultado llegue a un ViewModel ya destruido
        cleared = true;
        if (pendingUpload != null) {
            pendingUpload.cancel();
            pendingUpload = null;
        }
    }

    @MainThread
    private void upload(@Nullable File file) {
        Timber.i("Iniciamos el proceso de subida del fichero log");
        if (cleared) {
            return;
        }
        if (file == null) {
            Timber.e("No se ha podido cargar el fichero log para enviar");
            logSendState.setValue(new LogSendState.Failed(new UiText.Res(R.string.profile_log_unavailable), LogSendState.Recovery.NONE));
            return;
        }

        logFile = file;
        pendingUpload = logUploadRepository.upload(file, this::onUploadProgress, new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull Boolean result) {
                pendingUpload = null;
                failedUploads = 0;
                logSendState.setValue(new LogSendState.Sent());
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingUpload = null;
                onUploadFailed(error);
            }
        });
    }

    private void onUploadProgress(int percent) {
        if (logSendState.getValue() instanceof LogSendState.Sending) {
            logSendState.setValue(new LogSendState.Sending(percent));
        }
    }

    private void onUploadFailed(@NonNull AppError error) {
        failedUploads++;
        if (failedUploads >= UPLOAD_ATTEMPTS_BEFORE_EMAIL) {
            logSendState.setValue(new LogSendState.Failed(new UiText.Res(R.string.profile_log_upload_failed_email), LogSendState.Recovery.SEND_BY_EMAIL));
        } else {
            logSendState.setValue(new LogSendState.Failed(ErrorUiMapper.toUiText(error), LogSendState.Recovery.RETRY));
        }
    }
}
