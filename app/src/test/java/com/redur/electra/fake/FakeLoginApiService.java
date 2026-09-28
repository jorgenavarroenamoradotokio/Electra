package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.LoginApiService;
import com.redur.electra.data.remote.dto.request.login.LoginRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;

import retrofit2.Call;

public final class FakeLoginApiService implements LoginApiService {

    private FakeCall<ApiResponseDTO<UserDTO>> nextCall;
    @Nullable
    private LoginRequestDTO lastRequest;
    private int calls;

    public void willReturn(FakeCall<ApiResponseDTO<UserDTO>> call) {
        this.nextCall = call;
    }

    @Override
    public Call<ApiResponseDTO<UserDTO>> login(LoginRequestDTO loginRequest) {
        calls++;
        lastRequest = loginRequest;
        return nextCall;
    }

    @Nullable
    public LoginRequestDTO lastRequest() {
        return lastRequest;
    }

    public int calls() {
        return calls;
    }
}
