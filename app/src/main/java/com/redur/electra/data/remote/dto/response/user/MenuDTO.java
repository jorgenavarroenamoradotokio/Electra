package com.redur.electra.data.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

import java.util.Collection;

public record MenuDTO(
        @SerializedName("menuId") int menuID,
        @SerializedName("menuText") String text,
        @SerializedName("menuParentId") Integer parentMenuID,
        @SerializedName("permisosMenu") Collection<PermisoDTO> permission) {
}
