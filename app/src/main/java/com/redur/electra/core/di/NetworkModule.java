package com.redur.electra.core.di;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.redur.electra.BuildConfig;
import com.redur.electra.core.log.SensitiveAwareHttpLoggingInterceptor;
import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.api.FileApiService;
import com.redur.electra.data.remote.api.LabelApiService;
import com.redur.electra.data.remote.api.LoginApiService;
import com.redur.electra.data.remote.api.PlaceApiService;

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
            // Estas peticiones viajan con contraseña (y el login devuelve datos de usuario):
            // nunca registrar sus cuerpos. Además, volcar el del log lo leería entero antes de
            // enviarlo y el progreso de la subida saltaría al 100 %
            builder.addInterceptor(new SensitiveAwareHttpLoggingInterceptor(
                    message -> Timber.tag("OkHttp").d(message),
                    Set.of(LoginApiService.LOGIN_PATH,
                            PlaceApiService.CHANGE_PLZS_PATH,
                            PlaceApiService.PLACE_LIST_PATH,
                            FileApiService.UPLOAD_LOG_PATH,
                            FileApiService.UPLOAD_IMG_PATH,
                            LabelApiService.CREATE_ZPL_PATH,
                            BultoApiService.BULTO_TYPE_LIST_PATH)));
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

    @Provides
    static PlaceApiService providePlaceApiService(Retrofit retrofit) {
        return retrofit.create(PlaceApiService.class);
    }

    @Provides
    static FileApiService provideFileApiService(Retrofit retrofit) {
        return retrofit.create(FileApiService.class);
    }

    @Provides
    static LabelApiService provideLabelApiService(Retrofit retrofit) {
        return retrofit.create(LabelApiService.class);
    }

    @Provides
    static BultoApiService provideBultoApiService(Retrofit retrofit) {
        return retrofit.create(BultoApiService.class);
    }
}
