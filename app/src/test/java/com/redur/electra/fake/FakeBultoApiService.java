package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.dto.request.bulto.BultoTypeRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;

import java.util.List;

import retrofit2.Call;

public final class FakeBultoApiService implements BultoApiService {

    private FakeCall<ApiResponseDTO<List<BultoTypeDTO>>> nextTypesCall;
    @Nullable
    private BultoTypeRequestDTO lastTypesRequest;
    private int typesCalls;

    public void willReturnTypes(FakeCall<ApiResponseDTO<List<BultoTypeDTO>>> call) {
        this.nextTypesCall = call;
    }

    @Override
    public Call<ApiResponseDTO<List<BultoTypeDTO>>> getBultoTypes(BultoTypeRequestDTO request) {
        typesCalls++;
        lastTypesRequest = request;
        return nextTypesCall;
    }

    @Nullable
    public BultoTypeRequestDTO lastTypesRequest() {
        return lastTypesRequest;
    }

    public int typesCalls() {
        return typesCalls;
    }
}
