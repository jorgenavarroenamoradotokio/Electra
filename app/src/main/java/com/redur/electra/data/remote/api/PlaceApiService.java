package com.redur.electra.data.remote.api;

import com.redur.electra.data.remote.dto.request.place.ChangePlaceRequestDTO;
import com.redur.electra.data.remote.dto.request.place.PlaceListRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PlaceApiService {

    String CHANGE_PLZS_PATH = "ElectraWS/mobile/user/place/update/";
    String PLACE_LIST_PATH = "ElectraWS/mobile/user/place/list/";

    @POST(CHANGE_PLZS_PATH)
    Call<ApiResponseDTO<Boolean>> changePlace(@Body ChangePlaceRequestDTO changePlaceRequest);

    @POST(PLACE_LIST_PATH)
    Call<ApiResponseDTO<List<PlaceDTO>>> getPlaces(@Body PlaceListRequestDTO placeListRequest);
}
