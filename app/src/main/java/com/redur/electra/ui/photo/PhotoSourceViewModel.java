package com.redur.electra.ui.photo;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.permission.PermissionStatus;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.repository.PhotoRepository;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Hacer una foto o elegir una imagen: pide el permiso del origen elegido, abre la cámara o la
 * galería y entrega la imagen. El origen pendiente y la captura en curso se guardan en
 * {@link SavedStateHandle}: la app de cámara puede hacer que el sistema mate este proceso.
 */
@HiltViewModel
public class PhotoSourceViewModel extends ViewModel {

    static final String KEY_PENDING_SOURCE = "photoSource.pendingSource";
    static final String KEY_CAPTURE_PATH = "photoSource.capturePath";
    static final String KEY_CAPTURE_URI = "photoSource.captureUri";

    private final MutableLiveData<PhotoSourceState> state =
            new MutableLiveData<>(new PhotoSourceState.Ready(null));

    private final SavedStateHandle savedState;
    private final PhotoRepository repository;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    // Solo accedido desde el hilo principal
    private boolean cleared;

    @Inject
    public PhotoSourceViewModel(SavedStateHandle savedState, PhotoRepository repository,
                                @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this.savedState = savedState;
        this.repository = repository;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    public LiveData<PhotoSourceState> getState() {
        return state;
    }

    /**
     * @return true si se acepta la elección: la UI debe pedir entonces {@code source.permission()}
     * y responder con {@link #onPermissionResult}. False si ya hay otra en curso (doble toque).
     */
    @MainThread
    public boolean onSourceSelected(@NonNull PhotoSource source) {
        if (!(state.getValue() instanceof PhotoSourceState.Ready)) {
            return false;
        }
        savedState.set(KEY_PENDING_SOURCE, source);
        state.setValue(new PhotoSourceState.Busy());
        return true;
    }

    @MainThread
    public void onPermissionResult(@NonNull PermissionStatus status) {
        PhotoSource source = pendingSource();
        if (source == null) {
            finish(null);
            return;
        }
        switch (status) {
            case GRANTED -> open(source);
            case DENIED -> finish(new UiText.Res(source.deniedMessage()));
            case PERMANENTLY_DENIED -> state.setValue(new PhotoSourceState.PermissionBlocked(source.permission()));
        }
    }

    /** La UI ya muestra el aviso de ir a ajustes. */
    @MainThread
    public void onSettingsPromptShown() {
        if (state.getValue() instanceof PhotoSourceState.PermissionBlocked) {
            state.setValue(new PhotoSourceState.Busy());
        }
    }

    @MainThread
    public void onSettingsOpened() {
        PhotoSource source = pendingSource();
        if (source == null) {
            finish(null);
            return;
        }
        state.setValue(new PhotoSourceState.WaitingForSettings(source.permission()));
    }

    /** "Ahora no": el usuario ya sabe por qué no puede continuar, no se insiste. */
    @MainThread
    public void onSettingsDeclined() {
        finish(null);
    }

    /**
     * Al volver de ajustes: si activó el permiso se continúa sin que tenga que pulsar otra vez;
     * si no, la hoja queda lista sin más avisos (ya decidió en ajustes).
     */
    @MainThread
    public void onReturnedFromSettings(boolean granted) {
        if (!(state.getValue() instanceof PhotoSourceState.WaitingForSettings)) {
            return;
        }
        if (granted) {
            onPermissionResult(PermissionStatus.GRANTED);
        } else {
            finish(null);
        }
    }

    /** La UI ha abierto la cámara; {@code captureUri} es el Uri compartible de la captura. */
    @MainThread
    public void onCameraOpened(@NonNull String captureUri) {
        savedState.set(KEY_CAPTURE_URI, captureUri);
        state.setValue(new PhotoSourceState.Busy());
    }

    @MainThread
    public void onGalleryOpened() {
        state.setValue(new PhotoSourceState.Busy());
    }

    /** No se pudo abrir la cámara o la galería (p. ej. no hay ninguna app instalada). */
    @MainThread
    public void onSourceOpenFailed() {
        PhotoSource source = pendingSource();
        discardCapture();
        finish(source != null ? new UiText.Res(source.openFailedMessage()) : null);
    }

    /**
     * La cámara se ha cerrado. Se mira el fichero en lugar del resultado: hay apps de cámara que
     * informan de cancelación aunque guardaron la foto.
     */
    @MainThread
    public void onCameraClosed() {
        String path = savedState.get(KEY_CAPTURE_PATH);
        String uri = savedState.get(KEY_CAPTURE_URI);
        clearCapture();
        if (path == null || uri == null) {
            finish(null);
            return;
        }
        File file = new File(path);
        ioExecutor.execute(() -> {
            boolean taken = repository.hasPhoto(file);
            if (!taken) {
                repository.discard(file);
            }
            mainExecutor.execute(() -> {
                if (cleared) {
                    return;
                }
                if (taken) {
                    deliver(uri);
                } else {
                    finish(null);
                }
            });
        });
    }

    /** @param imageUri null si el usuario salió de la galería sin elegir. */
    @MainThread
    public void onGalleryResult(@Nullable String imageUri) {
        if (imageUri != null) {
            deliver(imageUri);
        } else {
            finish(null);
        }
    }

    @Override
    protected void onCleared() {
        // Evita que una captura preparada en segundo plano llegue a un ViewModel ya destruido
        cleared = true;
    }

    private void open(@NonNull PhotoSource source) {
        if (source == PhotoSource.GALLERY) {
            state.setValue(new PhotoSourceState.LaunchGallery());
            return;
        }
        state.setValue(new PhotoSourceState.Busy());
        ioExecutor.execute(() -> {
            try {
                File file = repository.createCaptureFile();
                mainExecutor.execute(() -> onCaptureFileReady(file));
            } catch (IOException e) {
                Timber.e(e, "No se ha podido preparar el fichero de la foto");
                mainExecutor.execute(() -> {
                    if (!cleared) {
                        finish(new UiText.Res(R.string.photo_capture_failed));
                    }
                });
            }
        });
    }

    private void onCaptureFileReady(@NonNull File file) {
        if (cleared) {
            return;
        }
        savedState.set(KEY_CAPTURE_PATH, file.getAbsolutePath());
        state.setValue(new PhotoSourceState.LaunchCamera(file));
    }

    private void deliver(@NonNull String imageUri) {
        savedState.remove(KEY_PENDING_SOURCE);
        state.setValue(new PhotoSourceState.Picked(imageUri));
    }

    private void finish(@Nullable UiText notice) {
        savedState.remove(KEY_PENDING_SOURCE);
        state.setValue(new PhotoSourceState.Ready(notice));
    }

    private void discardCapture() {
        String path = savedState.get(KEY_CAPTURE_PATH);
        clearCapture();
        if (path != null) {
            File file = new File(path);
            ioExecutor.execute(() -> repository.discard(file));
        }
    }

    private void clearCapture() {
        savedState.remove(KEY_CAPTURE_PATH);
        savedState.remove(KEY_CAPTURE_URI);
    }

    @Nullable
    private PhotoSource pendingSource() {
        return savedState.get(KEY_PENDING_SOURCE);
    }
}
