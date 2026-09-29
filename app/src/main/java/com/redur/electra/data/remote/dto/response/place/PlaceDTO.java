package com.redur.electra.data.remote.dto.response.place;

import com.google.gson.annotations.SerializedName;

public record PlaceDTO(
        @SerializedName("plzs_id") String plzsId,
        @SerializedName("nomplaza") String description
) {
}
