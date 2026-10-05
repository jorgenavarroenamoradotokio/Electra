package com.redur.electra.ui.label;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.concurrency.IoExecutor;
import com.redur.electra.core.concurrency.MainExecutor;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.label.ZplLabel;
import com.redur.electra.data.repository.LabelRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import timber.log.Timber;

/**
 * Genera la etiqueta en el backend al abrirse la pantalla y la convierte en imágenes que se puedan
 * mostrar. Sobrevive a los cambios de configuración sin repetir la petición; salir de la pantalla
 * la cancela.
 */
@HiltViewModel
public class LabelPreviewViewModel extends ViewModel {

    /** Convierte una página PNG en imagen; null si no es una imagen válida. */
    @VisibleForTesting
    public interface PageDecoder {
        @WorkerThread
        @Nullable
        Bitmap decode(@NonNull byte[] png);
    }

    private final MutableLiveData<LabelPreviewState> state = new MutableLiveData<>();

    private final LabelRepository repository;
    private final PageDecoder decoder;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    // Solo accedidos desde el hilo principal
    private boolean cleared;
    @Nullable
    private Cancellable pendingRequest;

    @Inject
    public LabelPreviewViewModel(LabelRepository repository,
                                 @IoExecutor Executor ioExecutor, @MainExecutor Executor mainExecutor) {
        this(repository, png -> BitmapFactory.decodeByteArray(png, 0, png.length), ioExecutor, mainExecutor);
    }

    @VisibleForTesting
    public LabelPreviewViewModel(LabelRepository repository, @NonNull PageDecoder decoder,
                                 Executor ioExecutor, Executor mainExecutor) {
        this.repository = repository;
        this.decoder = decoder;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
        generate();
    }

    public LiveData<LabelPreviewState> getState() {
        return state;
    }

    /** Solo tras un fallo: con la etiqueta generándose o ya mostrada no se repite la petición. */
    @MainThread
    public void onRetryClicked() {
        if (state.getValue() instanceof LabelPreviewState.Failed) {
            generate();
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (pendingRequest != null) {
            pendingRequest.cancel();
            pendingRequest = null;
        }
    }

    private void generate() {
        state.setValue(new LabelPreviewState.Generating());
        pendingRequest = repository.createZplLabel(new ResultCallback<>() {
            @Override
            public void onSuccess(@NonNull ZplLabel label) {
                pendingRequest = null;
                decodePages(label);
            }

            @Override
            public void onError(@NonNull AppError error) {
                pendingRequest = null;
                state.setValue(new LabelPreviewState.Failed(ErrorUiMapper.toUiText(error)));
            }
        });
    }

    private void decodePages(@NonNull ZplLabel label) {
        ioExecutor.execute(() -> {
            List<Bitmap> pages = decodeAll(label.pages());
            mainExecutor.execute(() -> {
                if (cleared) {
                    return;
                }
                state.setValue(pages != null
                        ? new LabelPreviewState.Ready(pages)
                        : new LabelPreviewState.Failed(new UiText.Res(R.string.label_preview_unreadable)));
            });
        });
    }

    /** Null si alguna página no se puede mostrar: una etiqueta incompleta no sirve para revisarla. */
    @WorkerThread
    @Nullable
    private List<Bitmap> decodeAll(@NonNull List<byte[]> pngPages) {
        List<Bitmap> pages = new ArrayList<>(pngPages.size());
        for (byte[] png : pngPages) {
            Bitmap page = decoder.decode(png);
            if (page == null) {
                Timber.e("Página de etiqueta que no es una imagen válida");
                return null;
            }
            pages.add(page);
        }
        return pages;
    }
}
