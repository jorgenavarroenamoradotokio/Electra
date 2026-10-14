package com.redur.electra.data.remote.dto.request.bulto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public record BultoDockRequestDT0(@SerializedName("userName") String username,
                                  String password,
                                  String language,
                                  String barcode) {

    @NonNull
    @Override
    public String toString() {
        return "BultoMuelleRequestDTO[username=" + username + ", password=****, language=" + language + ", barcode=" + barcode + "]";
    }
}

