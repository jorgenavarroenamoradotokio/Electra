package com.redur.electra.data.remote.dto.response.incidencia;

import com.google.gson.annotations.SerializedName;

public record IncidenciaDTO(
        @SerializedName("codIncidencia") String codigoIncidencia,
        @SerializedName("descIncidencia") String descripcion,
        @SerializedName("photoRequired") boolean photoRequired

) {
}
