package com.redur.electra.data.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record UserDTO(
        @SerializedName("userName") String username,
        @SerializedName("nombre") String fullname,
        @SerializedName("plzsId") String plzsId,
        @SerializedName("menu") List<MenuDTO> menu
) {
}
