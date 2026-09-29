package com.redur.electra.core.di;

import com.redur.electra.data.local.dao.plaza.PlaceDao;
import com.redur.electra.data.local.dao.plaza.SqlitePlaceDao;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class DatabaseModule {

    @Binds
    abstract PlaceDao bindPlaceDao(SqlitePlaceDao dao);
}
