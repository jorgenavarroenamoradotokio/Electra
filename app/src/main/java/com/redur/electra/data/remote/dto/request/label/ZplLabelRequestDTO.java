package com.redur.electra.data.remote.dto.request.label;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/** Petición de etiqueta ZPL. De momento el backend devuelve siempre una etiqueta fija. */
public record ZplLabelRequestDTO(
        @SerializedName("userName") String username,
        String password,
        String language,
        @SerializedName("plzs_id") String plzsId) {

    /** Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log. */
    @NonNull
    @Override
    public String toString() {
        return "ZplLabelRequestDTO[username=" + username + ", password=****, language=" + language
                + ", plzsId=" + plzsId + "]";
    }
}
