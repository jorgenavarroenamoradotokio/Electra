package com.redur.electra.data.local.dao.plaza;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.redur.electra.data.local.ElectraDatabaseHelper;
import com.redur.electra.data.model.place.Place;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import timber.log.Timber;

public class SqlitePlaceDao implements PlaceDao {

    static final String TABLE = "place";
    static final String COLUMN_ID = "plzs_id";
    static final String COLUMN_DESCRIPTION = "description";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE + " ("
            + COLUMN_ID + " TEXT PRIMARY KEY NOT NULL, "
            + COLUMN_DESCRIPTION + " TEXT NOT NULL)";

    private static final String[] COLUMNS = {COLUMN_ID, COLUMN_DESCRIPTION};
    private static final String ORDER_BY_ID = COLUMN_ID + " ASC";

    private final ElectraDatabaseHelper database;

    @Inject
    public SqlitePlaceDao(ElectraDatabaseHelper database) {
        this.database = database;
    }

    @WorkerThread
    @NonNull
    @Override
    public List<Place> getAll() {
        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE, COLUMNS, null, null, null, null, ORDER_BY_ID)) {
            int idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID);
            int descriptionIndex = cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION);
            List<Place> places = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                places.add(new Place(cursor.getString(idIndex), cursor.getString(descriptionIndex)));
            }
            return places;
        }
    }

    @WorkerThread
    @Override
    public int insertMissing(@NonNull List<Place> places) {
        SQLiteDatabase db = database.getWritableDatabase();
        ContentValues values = new ContentValues();
        int inserted = 0;
        // Una sola transacción: o se guardan todas las plazas nuevas o ninguna
        db.beginTransaction();
        try {
            for (Place place : places) {
                values.clear();
                values.put(COLUMN_ID, place.id());
                values.put(COLUMN_DESCRIPTION, place.description());
                // IGNORE: una plaza que ya existe en el terminal no se toca
                if (db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                    inserted++;
                }
            }
            db.setTransactionSuccessful();
        }catch(Exception ex){
            Timber.e(ex);
        } finally {
            db.endTransaction();
        }
        return inserted;
    }
}
