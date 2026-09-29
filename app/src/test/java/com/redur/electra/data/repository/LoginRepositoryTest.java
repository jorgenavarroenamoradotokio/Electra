package com.redur.electra.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonSyntaxException;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.request.login.LoginRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;
import com.redur.electra.data.remote.mapper.UserMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeLoginApiService;
import com.redur.electra.fake.LoginResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import retrofit2.Response;

public class LoginRepositoryTest {

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final FakeLoginApiService api = new FakeLoginApiService();
    private final UserSession session = new UserSession();
    private final LoginRepository repository = new LoginRepository(api, new UserMapper(), session);
    private final RecordingCallback callback = new RecordingCallback();

    // ---------- Éxito ----------

    @Test
    public void loginCorrecto_abreLaSesionYDevuelveElUsuario() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));

        repository.login("jperez", "secreta", callback);

        assertNotNull(callback.user);
        assertEquals("jperez", callback.user.username());
        assertSame(callback.user, session.getUser());
        assertEquals(1, callback.invocations);
    }

    /** La API no devuelve la contraseña: la sesión guarda la que se envió en el login. */
    @Test
    public void loginCorrecto_guardaLasCredencialesEnviadas() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));

        repository.login("jperez", "secreta", callback);

        assertEquals(new Credentials("jperez", "secreta"), session.getCredentials());
    }

    @Test
    public void loginFallido_noGuardaCredenciales() {
        api.willReturn(FakeCall.success(LoginResponses.apiError("ERROR_A01", "Credenciales inválidas")));

        repository.login("jperez", "mala", callback);

        assertNull(session.getCredentials());
    }

    @Test
    public void enviaLasCredencialesYElIdioma() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));

        repository.login("jperez", "secreta", callback);

        LoginRequestDTO request = api.lastRequest();
        assertNotNull(request);
        assertEquals("jperez", request.userName());
        assertEquals("secreta", request.password());
        assertFalse(request.language().isEmpty());
    }

    // ---------- Errores del servidor ----------

    @Test
    public void errorDeNegocio_devuelveElCodigoYNoAbreSesion() {
        api.willReturn(FakeCall.success(LoginResponses.apiError("ERROR_A01", "Credenciales inválidas")));

        repository.login("jperez", "mala", callback);

        assertEquals(new AppError.Api("ERROR_A01", "Credenciales inválidas"), callback.error);
        assertFalse(session.isActive());
    }

    @Test
    public void respuestaSinDatos_usaElTextoDeErrorGeneral() {
        api.willReturn(FakeCall.success(Response.success(
                new ApiResponseDTO<UserDTO>(500, "Servicio no disponible", List.of(), null))));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Api(null, "Servicio no disponible"), callback.error);
        assertFalse(session.isActive());
    }

    @Test
    public void usuarioSinNombreDeUsuario_seConsideraRespuestaInvalida() {
        api.willReturn(FakeCall.success(LoginResponses.ok(new UserDTO(null, "Juan", null, null, null))));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Api(null, null), callback.error);
        assertFalse(session.isActive());
    }

    @Test
    public void cuerpoVacio_esErrorDesconocido() {
        api.willReturn(FakeCall.success(Response.success((ApiResponseDTO<UserDTO>) null)));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Api("HTTP_200", null), callback.error);
    }

    @Test
    public void errorHttp_seIdentificaPorSuCodigo() {
        api.willReturn(FakeCall.success(LoginResponses.httpError(503)));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Api("HTTP_503", null), callback.error);
    }

    // ---------- Fallos de red ----------

    @Test
    public void timeout_esErrorDeRedTimeout() {
        api.willReturn(FakeCall.failure(new SocketTimeoutException("timeout")));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Network(NetworkType.TIMEOUT), callback.error);
    }

    @Test
    public void hostDesconocido_esErrorDeRedSinConexion() {
        api.willReturn(FakeCall.failure(new UnknownHostException("host")));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
    }

    @Test
    public void respuestaNoInterpretable_esErrorDesconocidoYSeRegistra() {
        api.willReturn(FakeCall.failure(new JsonSyntaxException("json inesperado")));

        repository.login("jperez", "secreta", callback);

        assertEquals(new AppError.Api(null, null), callback.error);
        assertTrue(timber.contains(Log.ERROR, "no interpretable"));
    }

    // ---------- Cancelación ----------

    @Test
    public void cancelar_noInvocaElCallbackNiAbreSesion() {
        FakeCall<ApiResponseDTO<UserDTO>> call =
                FakeCall.success(LoginResponses.ok(LoginResponses.user())).deferred();
        api.willReturn(call);

        Cancellable pending = repository.login("jperez", "secreta", callback);
        pending.cancel();
        call.complete();

        assertTrue(call.isCanceled());
        assertEquals(0, callback.invocations);
        assertFalse(session.isActive());
    }

    @Test
    public void cancelar_ignoraElFalloQueRetrofitEntregaAlCancelar() {
        FakeCall<ApiResponseDTO<UserDTO>> call =
                FakeCall.<ApiResponseDTO<UserDTO>>failure(new IOException("Canceled")).deferred();
        api.willReturn(call);

        repository.login("jperez", "secreta", callback).cancel();
        call.complete();

        assertEquals(0, callback.invocations);
    }

    private static final class RecordingCallback implements ResultCallback<User> {
        @Nullable
        User user;
        @Nullable
        AppError error;
        int invocations;

        @Override
        public void onSuccess(@NonNull User result) {
            invocations++;
            user = result;
        }

        @Override
        public void onError(@NonNull AppError error) {
            invocations++;
            this.error = error;
        }
    }
}
