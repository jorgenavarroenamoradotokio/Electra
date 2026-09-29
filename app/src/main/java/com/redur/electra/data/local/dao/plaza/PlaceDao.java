package com.redur.electra.data.local.dao.plaza;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.redur.electra.data.model.place.Place;

import java.util.List;

/** Plazas guardadas en el terminal. Accede a disco: nunca desde el hilo principal. */
public interface PlaceDao {

    /** Todas las plazas guardadas, ordenadas por código. */
    @WorkerThread
    @NonNull
    List<Place> getAll();

    /**
     * Guarda las plazas cuyo código aún no existe en el terminal; las existentes no se modifican.
     *
     * @return número de plazas nuevas guardadas.
     */
    @WorkerThread
    int insertMissing(@NonNull List<Place> places);
}
