package com.redur.electra.data.repository;

import androidx.annotation.NonNull;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.api.LoginApiService;
import com.redur.electra.data.remote.dto.request.login.LoginRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;
import com.redur.electra.data.remote.mapper.UserMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

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
public class LoginRepository extends BaseRepository {

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
     * Lanzamos la peticion de login en segundo plano.
     * El callback llega en el hilo principal y no se invoca si la operación se cancela.
     */
    @NonNull
    public Cancellable login(@NonNull String username, @NonNull String password,
                             @NonNull ResultCallback<User> callback) {

        // Ajustamos el texto para enviar UK en vez de EN a la API
        String locale = backendLanguage();

        // Construimos el DTO que vamos a enviar a la api
        Timber.d("El idioma del usuario que esta usando: %s", locale);
        LoginRequestDTO request = new LoginRequestDTO(username, password, locale);
        Timber.i("DTO request  %s", request.toString());

        // Procesamos la respuesta de la API
        Call<ApiResponseDTO<UserDTO>> call = api.login(request);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponseDTO<UserDTO>> call,
                                   @NonNull Response<ApiResponseDTO<UserDTO>> response) {
                if (call.isCanceled()) {
                    return;
                }
                handleResponse(response, new Credentials(username, password), callback);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponseDTO<UserDTO>> call, @NonNull Throwable t) {
                Timber.e(t);
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(toAppError(t));
            }
        });
        return call::cancel;
    }

    private void handleResponse(@NonNull Response<ApiResponseDTO<UserDTO>> response,
                                @NonNull Credentials credentials,
                                @NonNull ResultCallback<User> callback) {

        // Extraemos los datos
        UserDTO data = extractData(response, callback);
        if (data == null) {
            return;
        }

        // Notificamos que el nombre del usuario obtenido es null
        if (data.username() == null) {
            Timber.e("Login sin nombre de usuario: %s", response.body().errorText());
            callback.onError(new AppError.Api(null, response.body().errorText()));
            return;
        }

        // Guardamos la respuesta en la sesion de la aplicacion
        Timber.i("Usuario conectado correctamente %s", data);
        User user = mapper.toUser(data);
        session.start(user, credentials);
        callback.onSuccess(user);
    }

    /**
     * Cierra la sesión local: los datos del usuario dejan de estar disponibles para la app.
     */
    public void logout() {
        Timber.i("Sesion de usuario cerrada");
        session.clear();
    }
}