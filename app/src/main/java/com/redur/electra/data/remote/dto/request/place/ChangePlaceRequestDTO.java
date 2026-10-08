package com.redur.electra.data.remote.dto.request.place;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public record ChangePlaceRequestDTO (
        @SerializedName("userName") String username,
        String password,
        String language,
        @SerializedName("plzs_id") String plzsId){

    /**
     * Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log.
     */
    @NonNull
    @Override
    public String toString() {
        return "ChangePlaceRequestDTO[userName=" + username + ", password=****, language=" + language + ", plzsId=" + plzsId + "]";
    }
}