package com.redur.electra.data.remote.api;

import com.redur.electra.data.remote.dto.request.bulto.BultoDockRequestDT0;
import com.redur.electra.data.remote.dto.request.bulto.BultoTypeRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoDockDT0;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface BultoApiService {

    String BULTO_TYPE_LIST_PATH = "ElectraWS/mobile/bulto/internacional/tipo";
    String BULTO_MUELLE_PATH = "ElectraWS/mobile/bulto/internacional/muelle";

    /** Tipos de bulto activos, con la descripción traducida al idioma pedido. */
    @POST(BULTO_TYPE_LIST_PATH)
    Call<ApiResponseDTO<List<BultoTypeDTO>>> getBultoTypes(@Body BultoTypeRequestDTO request);

    @POST(BULTO_MUELLE_PATH)
    Call<ApiResponseDTO<BultoDockDT0>> getMuelleBulto(@Body BultoDockRequestDT0 request);
}
