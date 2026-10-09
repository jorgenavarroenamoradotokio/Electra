package com.redur.electra.core.log;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import timber.log.Timber;

/**
 * Rastro de las Activities en el log: qué pantalla se abre, cuándo la app pasa a segundo plano
 * (cámara, galería, ajustes...) y si una Activity se recrea tras matar el sistema el proceso.
 * La navegación entre Fragments la registra cada Activity con su NavController.
 */
public final class ActivityLifecycleLogger implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Timber.i("[NAV] %s recreada desde estado guardado", name(activity));
        } else {
            Timber.i("[NAV] %s abierta", name(activity));
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // Sin log: onActivityResumed ya indica que es visible
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Timber.i("[NAV] %s en primer plano", name(activity));
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // Sin log: onActivityStopped indica cuándo deja de verse
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Timber.i("[NAV] %s en segundo plano", name(activity));
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // Sin log: no es una acción del usuario
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity.isChangingConfigurations()) {
            Timber.i("[NAV] %s se recrea por cambio de configuración", name(activity));
        } else {
            Timber.i("[NAV] %s cerrada", name(activity));
        }
    }

    @NonNull
    private static String name(@NonNull Activity activity) {
        return activity.getClass().getSimpleName();
    }
}
