package com.redur.electra.data.remote.dto.request.incidencia;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public record IncidenciaRequestDTO(@SerializedName("userName") String username,
                                   String password,
                                   String language,
                                   String codigoAplicacion) {
    @NonNull
    @Override
    public String toString() {
        return "IncidenciaRequestDTO[username=" + username + ", password=****, language=" + language + ", codigoAplicacion=" + codigoAplicacion + "]";
    }
}
