package com.redur.electra.data.remote.dto.response.bulto;

import com.google.gson.annotations.SerializedName;

public record BultoDockDT0(
        @SerializedName("plzsid") String plzsId,
        @SerializedName("muelle") String muelle,
        @SerializedName("cp") String postalCode,
        @SerializedName("pais") String country

) {
}
