package com.redur.electra.data.remote.api;

import com.redur.electra.data.remote.dto.request.login.LoginRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface LoginApiService {

    String LOGIN_PATH = "ElectraWS/mobile/login/";

    @POST(LOGIN_PATH)
    Call<ApiResponseDTO<UserDTO>> login(@Body LoginRequestDTO loginRequest);

}
