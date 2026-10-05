package com.redur.electra.data.remote.api;

import com.redur.electra.data.remote.dto.request.label.ZplLabelRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface LabelApiService {

    String CREATE_ZPL_PATH = "ElectraWS/mobile/label/zpl/create";

    /** Genera la etiqueta ZPL y su imagen de previsualización. */
    @POST(CREATE_ZPL_PATH)
    Call<ApiResponseDTO<ZplLabelDTO>> createZpl(@Body ZplLabelRequestDTO request);
}
