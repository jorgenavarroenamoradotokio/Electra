package com.redur.electra.fake;

import android.database.SQLException;

import androidx.annotation.NonNull;

import com.redur.electra.data.local.dao.plaza.PlaceDao;
import com.redur.electra.data.model.place.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tabla de plazas en memoria con la misma semántica que SQLite: clave por código, orden por código. */
public final class FakePlaceDao implements PlaceDao {

    private final Map<String, Place> rows = new TreeMap<>();
    private boolean failing;

    public void seed(Place... places) {
        for (Place place : places) {
            rows.put(place.id(), place);
        }
    }

    /** Simula una base de datos inaccesible (disco lleno, fichero corrupto...). */
    public void failWith(boolean failing) {
        this.failing = failing;
    }

    @NonNull
    @Override
    public List<Place> getAll() {
        checkAvailable();
        return new ArrayList<>(rows.values());
    }

    @Override
    public int insertMissing(@NonNull List<Place> places) {
        checkAvailable();
        int inserted = 0;
        for (Place place : places) {
            if (rows.putIfAbsent(place.id(), place) == null) {
                inserted++;
            }
        }
        return inserted;
    }

    private void checkAvailable() {
        if (failing) {
            throw new SQLException("database unavailable");
        }
    }
}
