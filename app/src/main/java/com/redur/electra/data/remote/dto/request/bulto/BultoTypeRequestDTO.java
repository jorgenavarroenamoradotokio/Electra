package com.redur.electra.data.remote.dto.request.bulto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public record BultoTypeRequestDTO(
        @SerializedName("userName") String username,
        String password,
        String language) {

    /** Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log. */
    @NonNull
    @Override
    public String toString() {
        return "BultoTypeRequestDTO[username=" + username + ", password=****, language=" + language + "]";
    }
}
