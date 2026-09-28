package com.redur.electra.data.remote.dto.response;

import com.google.gson.annotations.SerializedName;

public record ApiErrorDetailResponseDTO(
        @SerializedName("errorCode") String code,
        @SerializedName("errorDescription") String description,
        @SerializedName("affectedField") String field,
        @SerializedName("idioma") String language
) {
}
