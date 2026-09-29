package com.redur.electra.data.remote.dto.request.place;

import com.google.gson.annotations.SerializedName;

public record PlaceListRequestDTO(
        @SerializedName("userName") String username,
        String password,
        String language) {
}