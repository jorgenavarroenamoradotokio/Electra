package com.redur.electra.data.remote.dto.response.bulto;

import com.google.gson.annotations.SerializedName;

public record BultoTypeDTO(
        @SerializedName("codigo") String code,
        @SerializedName("tipo") String type,
        @SerializedName("descripcion") String description,
        @SerializedName("fotoNecesaria") boolean photoRequired
) {
}
