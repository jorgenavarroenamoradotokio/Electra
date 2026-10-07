package com.redur.electra.data.remote.dto.request.login;

import androidx.annotation.NonNull;

public record LoginRequestDTO(
        String userName,
        String password,
        String language
) {

    /** Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log. */
    @NonNull
    @Override
    public String toString() {
        return "LoginRequestDTO[userName=" + userName + ", password=****, language=" + language + "]";
    }

}
