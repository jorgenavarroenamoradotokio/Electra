package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;

import java.util.List;

import retrofit2.Response;

/** Respuestas de plazas de ejemplo para los tests. */
public final class PlaceResponses {

    private PlaceResponses() {
    }

    public static Response<ApiResponseDTO<List<PlaceDTO>>> places(PlaceDTO... places) {
        return Response.success(new ApiResponseDTO<>(200, null, List.of(), List.of(places)));
    }

    public static Response<ApiResponseDTO<Boolean>> changed(boolean changed) {
        return Response.success(new ApiResponseDTO<>(200, null, List.of(), changed));
    }

    public static <T> Response<ApiResponseDTO<T>> apiError(String code, String description) {
        ApiErrorDetailResponseDTO error = new ApiErrorDetailResponseDTO(code, description, null, "es");
        return Response.success(new ApiResponseDTO<>(400, description, List.of(error), null));
    }
}
