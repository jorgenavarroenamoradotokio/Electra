package com.redur.electra.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

/**
 * Ficheros donde la app de cámara guarda las fotos que se hacen desde Electra. Viven en la caché
 * (carpeta {@value #PHOTO_DIR}, compartida en file_paths.xml): el sistema puede liberarla si
 * necesita espacio. Todos los métodos tocan disco: no deben llamarse desde el hilo principal.
 */
public class PhotoRepository {

    static final String PHOTO_DIR = "photos";
    private static final String PHOTO_PREFIX = "IMG_";
    private static final String PHOTO_EXTENSION = ".jpg";

    private final Supplier<File> photoDir;

    @Inject
    public PhotoRepository(@ApplicationContext Context context) {
        this(() -> new File(context.getCacheDir(), PHOTO_DIR));
    }

    @VisibleForTesting
    public PhotoRepository(@NonNull Supplier<File> photoDir) {
        this.photoDir = photoDir;
    }

    /** Fichero vacío y con nombre único donde la cámara escribirá la foto. */
    @WorkerThread
    @NonNull
    public File createCaptureFile() throws IOException {
        File dir = photoDir.get();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("No se puede crear la carpeta de fotos");
        }
        return File.createTempFile(PHOTO_PREFIX, PHOTO_EXTENSION, dir);
    }

    /**
     * Si la cámara llegó a escribir la foto. Algunas apps de cámara informan de cancelación aunque
     * hayan guardado la imagen, así que se comprueba el propio fichero.
     */
    @WorkerThread
    public boolean hasPhoto(@NonNull File file) {
        return file.isFile() && file.length() > 0;
    }

    /** Borra una captura descartada para no acumular ficheros vacíos en la caché. */
    @WorkerThread
    public void discard(@NonNull File file) {
        if (file.exists() && !file.delete()) {
            Timber.w("No se ha podido borrar la captura descartada %s", file.getName());
        }
    }
}
