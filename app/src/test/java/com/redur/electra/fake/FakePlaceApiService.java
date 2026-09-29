package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.PlaceApiService;
import com.redur.electra.data.remote.dto.request.place.ChangePlaceRequestDTO;
import com.redur.electra.data.remote.dto.request.place.PlaceListRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;

import java.util.List;

import retrofit2.Call;

public final class FakePlaceApiService implements PlaceApiService {

    private FakeCall<ApiResponseDTO<List<PlaceDTO>>> nextPlacesCall;
    private FakeCall<ApiResponseDTO<Boolean>> nextChangeCall;
    @Nullable
    private PlaceListRequestDTO lastPlacesRequest;
    @Nullable
    private ChangePlaceRequestDTO lastChangeRequest;
    private int placesCalls;
    private int changeCalls;

    public void willReturnPlaces(FakeCall<ApiResponseDTO<List<PlaceDTO>>> call) {
        this.nextPlacesCall = call;
    }

    public void willReturnChange(FakeCall<ApiResponseDTO<Boolean>> call) {
        this.nextChangeCall = call;
    }

    @Override
    public Call<ApiResponseDTO<Boolean>> changePlace(ChangePlaceRequestDTO changePlaceRequest) {
        changeCalls++;
        lastChangeRequest = changePlaceRequest;
        return nextChangeCall;
    }

    @Override
    public Call<ApiResponseDTO<List<PlaceDTO>>> getPlaces(PlaceListRequestDTO placeListRequest) {
        placesCalls++;
        lastPlacesRequest = placeListRequest;
        return nextPlacesCall;
    }

    @Nullable
    public PlaceListRequestDTO lastPlacesRequest() {
        return lastPlacesRequest;
    }

    @Nullable
    public ChangePlaceRequestDTO lastChangeRequest() {
        return lastChangeRequest;
    }

    public int placesCalls() {
        return placesCalls;
    }

    public int changeCalls() {
        return changeCalls;
    }
}
