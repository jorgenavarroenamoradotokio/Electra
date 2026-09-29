package com.redur.electra.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.redur.electra.data.local.dao.plaza.SqlitePlaceDao;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Base de datos local de la app. Se abre de forma perezosa en el primer acceso, que los DAO
 * hacen siempre desde un hilo de I/O.
 */
@Singleton
public class ElectraDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "electra.db";
    private static final int DATABASE_VERSION = 1;

    @Inject
    public ElectraDatabaseHelper(@ApplicationContext Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(SqlitePlaceDao.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versión inicial: cada cambio de esquema debe añadir aquí su migración
    }
}
