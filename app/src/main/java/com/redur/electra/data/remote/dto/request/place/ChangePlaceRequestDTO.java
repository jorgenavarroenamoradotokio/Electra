package com.redur.electra.data.remote.dto.request.place;

import com.google.gson.annotations.SerializedName;

public record ChangePlaceRequestDTO (
        @SerializedName("userName") String username,
        String password,
        String language,
        @SerializedName("plzs_id") String plzsId){
}