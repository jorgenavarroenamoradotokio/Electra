package com.redur.electra.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.api.LoginApiService;
import com.redur.electra.data.remote.dto.request.login.LoginRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;
import com.redur.electra.data.remote.mapper.UserMapper;
import com.redur.electra.data.session.UserSession;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Autentica contra el backend y, si el login es correcto, abre la {@link UserSession}.
 * Es el único punto que conoce Retrofit y los DTOs del login.
 */
public class LoginRepository {

    private static final String HTTP_ERROR_PREFIX = "HTTP_";

    private final LoginApiService api;
    private final UserMapper mapper;
    private final UserSession session;

    @Inject
    public LoginRepository(LoginApiService api, UserMapper mapper, UserSession session) {
        this.api = api;
        this.mapper = mapper;
        this.session = session;
    }

    /**
     * Lanza el login en segundo plano. El callback llega en el hilo principal y no se invoca si
     * la operación se cancela.
     */
    @NonNull
    public Cancellable login(@NonNull String username, @NonNull String password,
                             @NonNull ResultCallback<User> callback) {
        LoginRequestDTO request = new LoginRequestDTO(username, password, Locale.getDefault().getLanguage());
        Call<ApiResponseDTO<UserDTO>> call = api.login(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<UserDTO>> call,
                                   @NonNull Response<ApiResponseDTO<UserDTO>> response) {
                if (call.isCanceled()) {
                    return;
                }
                handleResponse(response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<UserDTO>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(t));
            }
        });
        return call::cancel;
    }

    private void handleResponse(@NonNull Response<ApiResponseDTO<UserDTO>> response,
                                @NonNull ResultCallback<User> callback) {
        if (!response.isSuccessful()) {
            callback.onError(new AppError.Api(HTTP_ERROR_PREFIX + response.code(), null));
            return;
        }

        ApiResponseDTO<UserDTO> body = response.body();
        if (body == null) {
            callback.onError(new AppError.Api(null, null));
            return;
        }

        ApiErrorDetailResponseDTO apiError = firstError(body.errorList());
        if (apiError != null) {
            callback.onError(new AppError.Api(apiError.code(), apiError.description()));
            return;
        }

        if (body.data() == null || body.data().username() == null) {
            callback.onError(new AppError.Api(null, body.errorText()));
            return;
        }

        User user = mapper.toUser(body.data());
        session.start(user);
        callback.onSuccess(user);
    }

    @Nullable
    private static ApiErrorDetailResponseDTO firstError(@Nullable List<ApiErrorDetailResponseDTO> errors) {
        if (errors == null) {
            return null;
        }
        for (ApiErrorDetailResponseDTO error : errors) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    @NonNull
    private static AppError toAppError(@NonNull Throwable t) {
        // SocketTimeoutException hereda de InterruptedIOException
        if (t instanceof InterruptedIOException) {
            return new AppError.Network(NetworkType.TIMEOUT);
        }
        if (t instanceof IOException) {
            return new AppError.Network(NetworkType.NO_CONNECTION);
        }
        // Respuesta imposible de interpretar (p. ej. JSON inesperado): no es un fallo de red
        Timber.e(t, "Respuesta de login no interpretable");
        return new AppError.Api(null, null);
    }
}
