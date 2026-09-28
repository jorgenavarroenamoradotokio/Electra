package com.redur.electra.core.di;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.redur.electra.BuildConfig;
import com.redur.electra.core.log.SensitiveAwareHttpLoggingInterceptor;
import com.redur.electra.data.remote.api.LoginApiService;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    @Provides
    @Singleton
    static Gson provideGson() {
        return new GsonBuilder().create();
    }

    @Provides
    @Singleton
    static OkHttpClient provideOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            // El login viaja con contraseña y devuelve datos de usuario: nunca registrar sus cuerpos
            builder.addInterceptor(new SensitiveAwareHttpLoggingInterceptor(
                    message -> Timber.tag("OkHttp").d(message),
                    Set.of(LoginApiService.LOGIN_PATH)));
        }

        return builder.build();
    }

    @Provides
    @Singleton
    static Retrofit provideRetrofit(OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    @Provides
    static LoginApiService provideLoginApiService(Retrofit retrofit) {
        return retrofit.create(LoginApiService.class);
    }
}
