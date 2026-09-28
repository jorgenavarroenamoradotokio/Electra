package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.MenuDTO;
import com.redur.electra.data.remote.dto.response.user.PermisoDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Respuestas de login de ejemplo para los tests. */
public final class LoginResponses {

    private LoginResponses() {
    }

    public static UserDTO user() {
        return new UserDTO("jperez", "Juan Pérez", "P01", List.of(
                new MenuDTO(1, "Recepción", null, List.of(new PermisoDTO(10), new PermisoDTO(11))),
                new MenuDTO(2, "Descarga", 1, List.of(new PermisoDTO(20)))));
    }

    public static Response<ApiResponseDTO<UserDTO>> ok(UserDTO user) {
        return Response.success(new ApiResponseDTO<>(200, null, List.of(), user));
    }

    public static Response<ApiResponseDTO<UserDTO>> apiError(String code, String description) {
        ApiErrorDetailResponseDTO error = new ApiErrorDetailResponseDTO(code, description, null, "es");
        return Response.success(new ApiResponseDTO<>(400, description, List.of(error), null));
    }

    public static Response<ApiResponseDTO<UserDTO>> httpError(int code) {
        return Response.error(code, ResponseBody.create("", MediaType.get("application/json")));
    }
}
