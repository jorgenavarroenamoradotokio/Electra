package com.redur.electra.data.remote.dto.response;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record ApiResponseDTO<T>(
        @SerializedName("status") int status,
        @SerializedName("errorText") String errorText,
        @SerializedName("errorList") List<ApiErrorDetailResponseDTO> errorList,
        @SerializedName("data") T data) {
}
