package com.redur.electra.ui.bulto.tipo;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.data.model.bulto.BultoType;

/**
 * La pantalla general de tipo de bulto usada como selector desde otra pantalla (destino
 * {@code nav_bulto_type_picker}): se abre con el tipo que ya estaba fijado marcado y, al asignar,
 * devuelve el elegido en el {@link SavedStateHandle} de la entrada anterior bajo {@link #RESULT_KEY}.
 * Cancelar vuelve sin resultado.
 */
public final class BultoTypePicker {

    public static final String RESULT_KEY = "bultoTypePicker.result";

    private static final String KEY_PICKER = "bultoTypePicker.picker";
    private static final String KEY_PRESELECTED_CODE = "bultoTypePicker.preselectedCode";

    private static final String KEY_CODE = "bultoType.code";
    private static final String KEY_TYPE = "bultoType.type";
    private static final String KEY_DESCRIPTION = "bultoType.description";
    private static final String KEY_PHOTO_REQUIRED = "bultoType.photoRequired";

    private BultoTypePicker() {
    }

    /** Argumentos para abrir el selector con {@code preselectedCode} ya marcado, si lo hay. */
    @NonNull
    public static Bundle args(@Nullable String preselectedCode) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_PICKER, true);
        bundle.putString(KEY_PRESELECTED_CODE, preselectedCode);
        return bundle;
    }

    /** Si la pantalla se abrió como selector y no desde el menú lateral. */
    static boolean isPicker(@NonNull SavedStateHandle handle) {
        return Boolean.TRUE.equals(handle.get(KEY_PICKER));
    }

    @Nullable
    static String preselectedCode(@NonNull SavedStateHandle handle) {
        return handle.get(KEY_PRESELECTED_CODE);
    }

    @NonNull
    public static Bundle toBundle(@NonNull BultoType type) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_CODE, type.code());
        bundle.putString(KEY_TYPE, type.type());
        bundle.putString(KEY_DESCRIPTION, type.description());
        bundle.putBoolean(KEY_PHOTO_REQUIRED, type.photoRequired());
        return bundle;
    }

    /** @return el tipo guardado con {@link #toBundle(BultoType)}, o null si el bundle no lo contiene. */
    @Nullable
    public static BultoType fromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String code = bundle.getString(KEY_CODE);
        String type = bundle.getString(KEY_TYPE);
        String description = bundle.getString(KEY_DESCRIPTION);
        if (code == null || type == null || description == null) {
            return null;
        }
        return new BultoType(code, type, description, bundle.getBoolean(KEY_PHOTO_REQUIRED));
    }
}
