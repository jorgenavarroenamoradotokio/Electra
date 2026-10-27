package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;

import java.util.List;

import retrofit2.Response;

/** Respuestas de tipos de bulto e incidencias de ejemplo para los tests. */
public final class BultoResponses {

    public static final BultoTypeDTO BU = new BultoTypeDTO("010", "BU", "BULTO PAQUETE", false);
    public static final BultoTypeDTO PL = new BultoTypeDTO("020", "PL", "PALET", false);
    public static final BultoTypeDTO MORE_THAN_3 = new BultoTypeDTO("030", "+3", "+ DE 3 METROS", true);

    public static final IncidenciaDTO A16 = new IncidenciaDTO("A16", "BULTO DAÑADO", false);
    public static final IncidenciaDTO A21 = new IncidenciaDTO("A21", "BULTO ABIERTO", true);

    private BultoResponses() {
    }

    public static Response<ApiResponseDTO<List<BultoTypeDTO>>> types(BultoTypeDTO... types) {
        return Response.success(new ApiResponseDTO<>(1, null, List.of(), List.of(types)));
    }

    public static Response<ApiResponseDTO<List<IncidenciaDTO>>> incidencias(IncidenciaDTO... incidencias) {
        return Response.success(new ApiResponseDTO<>(1, null, List.of(), List.of(incidencias)));
    }
}
