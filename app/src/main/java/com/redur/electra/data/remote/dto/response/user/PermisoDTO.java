package com.redur.electra.data.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

public record PermisoDTO(
        @SerializedName("permisosId") int value) {
}
