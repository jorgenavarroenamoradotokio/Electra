package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.LabelApiService;
import com.redur.electra.data.remote.dto.request.label.ZplLabelRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;

import retrofit2.Call;

public final class FakeLabelApiService implements LabelApiService {

    private FakeCall<ApiResponseDTO<ZplLabelDTO>> nextCreateCall;
    @Nullable
    private ZplLabelRequestDTO lastRequest;
    private int createCalls;

    public void willReturnCreate(FakeCall<ApiResponseDTO<ZplLabelDTO>> call) {
        this.nextCreateCall = call;
    }

    @Override
    public Call<ApiResponseDTO<ZplLabelDTO>> createZpl(ZplLabelRequestDTO request) {
        createCalls++;
        lastRequest = request;
        return nextCreateCall;
    }

    @Nullable
    public ZplLabelRequestDTO lastRequest() {
        return lastRequest;
    }

    public int createCalls() {
        return createCalls;
    }
}
