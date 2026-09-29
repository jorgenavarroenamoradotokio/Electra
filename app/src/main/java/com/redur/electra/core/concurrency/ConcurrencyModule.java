package com.redur.electra.core.concurrency;

import android.os.Handler;
import android.os.Looper;

import com.redur.electra.core.di.IoExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class ConcurrencyModule {

    /** Hilos bajo demanda: se reutilizan entre tareas y se liberan tras un rato sin uso. */
    @Provides
    @Singleton
    @IoExecutor
    static Executor provideIoExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Provides
    @Singleton
    @MainExecutor
    static Executor provideMainExecutor() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        return mainHandler::post;
    }
}
