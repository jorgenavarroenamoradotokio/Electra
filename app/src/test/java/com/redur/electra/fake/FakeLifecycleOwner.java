package com.redur.electra.fake;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

/** LifecycleOwner cuyo estado se mueve a mano desde el test (hilo principal de Robolectric). */
public class FakeLifecycleOwner implements LifecycleOwner {

    private final LifecycleRegistry registry = new LifecycleRegistry(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    public void moveTo(@NonNull Lifecycle.State state) {
        registry.setCurrentState(state);
    }
}
