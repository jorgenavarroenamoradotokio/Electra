package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;

import java.util.List;

import retrofit2.Response;

/** Respuestas de tipos de bulto de ejemplo para los tests, tomadas del backend real. */
public final class BultoResponses {

    public static final BultoTypeDTO BU = new BultoTypeDTO("010", "BU", "BULTO PAQUETE", false);
    public static final BultoTypeDTO PL = new BultoTypeDTO("020", "PL", "PALET", false);
    public static final BultoTypeDTO MORE_THAN_3 = new BultoTypeDTO("030", "+3", "+ DE 3 METROS", true);

    private BultoResponses() {
    }

    public static Response<ApiResponseDTO<List<BultoTypeDTO>>> types(BultoTypeDTO... types) {
        return Response.success(new ApiResponseDTO<>(1, null, List.of(), List.of(types)));
    }
}
