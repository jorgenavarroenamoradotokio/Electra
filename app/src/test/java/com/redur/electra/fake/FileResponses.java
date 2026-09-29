package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiResponseDTO;

import java.util.List;

import retrofit2.Response;

/** Respuestas de subida de ficheros de ejemplo para los tests. */
public final class FileResponses {

    private FileResponses() {
    }

    public static Response<ApiResponseDTO<Boolean>> uploaded(boolean uploaded) {
        return Response.success(new ApiResponseDTO<>(200, null, List.of(), uploaded));
    }
}
