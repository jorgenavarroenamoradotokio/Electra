package com.redur.electra.ui.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;
import com.redur.electra.data.remote.mapper.UserMapper;
import com.redur.electra.data.repository.LoginRepository;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeLoginApiService;
import com.redur.electra.fake.LoginResponses;

import org.junit.Rule;
import org.junit.Test;

import java.net.SocketTimeoutException;

public class LoginViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private final FakeLoginApiService api = new FakeLoginApiService();
    private final UserSession session = new UserSession();
    private final LoginViewModel viewModel =
            new LoginViewModel(new LoginRepository(api, new UserMapper(), session));

    // ---------- Validación del formulario ----------

    @Test
    public void camposVacios_marcanErroresYNoLlamanAlBackend() {
        viewModel.onLoginClicked("  ", null);

        assertEquals(new LoginFormState(R.string.login_error_username_required,
                R.string.login_error_password_required), viewModel.getFormState().getValue());
        assertTrue(viewModel.getLoginState().getValue() instanceof UiState.Idle);
        assertEquals(0, api.calls());
    }

    @Test
    public void editarUnCampo_limpiaSoloSuError() {
        viewModel.onLoginClicked("", "");

        viewModel.onUsernameChanged();

        LoginFormState form = viewModel.getFormState().getValue();
        assertNotNull(form);
        assertNull(form.usernameError());
        assertEquals(Integer.valueOf(R.string.login_error_password_required), form.passwordError());
    }

    // ---------- Petición ----------

    @Test
    public void loginCorrecto_pasaASuccessYGuardaLaSesion() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));

        viewModel.onLoginClicked("jperez", "secreta");

        assertTrue(viewModel.getLoginState().getValue() instanceof UiState.Success);
        assertNotNull(session.getUser());
        assertEquals("jperez", session.getUser().username());
    }

    @Test
    public void elUsuarioSeEnviaSinEspaciosYLaContrasenaTalCual() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));

        viewModel.onLoginClicked("  jperez ", " secreta ");

        assertNotNull(api.lastRequest());
        assertEquals("jperez", api.lastRequest().userName());
        assertEquals(" secreta ", api.lastRequest().password());
    }

    @Test
    public void mientrasCarga_elEstadoEsLoading() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())).deferred());

        viewModel.onLoginClicked("jperez", "secreta");

        assertTrue(viewModel.getLoginState().getValue() instanceof UiState.Loading);
    }

    @Test
    public void pulsarDeNuevoMientrasCarga_noDuplicaLaPeticion() {
        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())).deferred());

        viewModel.onLoginClicked("jperez", "secreta");
        viewModel.onLoginClicked("jperez", "secreta");

        assertEquals(1, api.calls());
    }

    @Test
    public void errorDeNegocio_muestraElMensajeDelCodigo() {
        api.willReturn(FakeCall.success(LoginResponses.apiError("ERROR_A01", "Credenciales inválidas")));

        viewModel.onLoginClicked("jperez", "mala");

        assertEquals(new UiState.Error(new UiText.Res(R.string.error_login_failed)),
                viewModel.getLoginState().getValue());
        assertFalse(session.isActive());
    }

    @Test
    public void errorDeRed_muestraElMensajeDeRed() {
        api.willReturn(FakeCall.failure(new SocketTimeoutException("timeout")));

        viewModel.onLoginClicked("jperez", "secreta");

        assertEquals(new UiState.Error(new UiText.Res(R.string.error_network_timeout)),
                viewModel.getLoginState().getValue());
    }

    @Test
    public void trasUnError_sePuedeReintentar() {
        api.willReturn(FakeCall.failure(new SocketTimeoutException("timeout")));
        viewModel.onLoginClicked("jperez", "secreta");

        api.willReturn(FakeCall.success(LoginResponses.ok(LoginResponses.user())));
        viewModel.onLoginClicked("jperez", "secreta");

        assertEquals(2, api.calls());
        assertTrue(viewModel.getLoginState().getValue() instanceof UiState.Success);
    }

    @Test
    public void destruirElViewModel_cancelaLaPeticionEnCurso() {
        FakeCall<ApiResponseDTO<UserDTO>> call =
                FakeCall.success(LoginResponses.ok(LoginResponses.user())).deferred();
        api.willReturn(call);
        viewModel.onLoginClicked("jperez", "secreta");

        viewModel.onCleared();
        call.complete();

        assertTrue(call.isCanceled());
        assertTrue(viewModel.getLoginState().getValue() instanceof UiState.Loading);
        assertFalse(session.isActive());
    }
}
