package com.redur.electra.ui.logout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.core.ui.UiState;
import com.redur.electra.data.remote.mapper.UserMapper;
import com.redur.electra.data.repository.LoginRepository;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeLoginApiService;
import com.redur.electra.fake.LoginResponses;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LogoutViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private final UserSession session = new UserSession();
    private final LogoutViewModel viewModel = new LogoutViewModel(
            new LoginRepository(new FakeLoginApiService(), new UserMapper(), session));

    @Before
    public void setUp() {
        session.start(new UserMapper().toUser(LoginResponses.user()));
    }

    @Test
    public void estadoInicial_esIdleYNoTocaLaSesion() {
        assertTrue(viewModel.getLogoutState().getValue() instanceof UiState.Idle);
        assertTrue(session.isActive());
    }

    @Test
    public void confirmar_limpiaLaSesionYEmiteSuccess() {
        viewModel.onLogoutConfirmed();

        assertFalse(session.isActive());
        assertNull(session.getUser());
        assertTrue(viewModel.getLogoutState().getValue() instanceof UiState.Success);
    }

    @Test
    public void confirmarDosVeces_noReemiteElEstado() {
        viewModel.onLogoutConfirmed();
        int[] emissions = {0};
        viewModel.getLogoutState().observeForever(state -> emissions[0]++);

        viewModel.onLogoutConfirmed();

        // observeForever entrega el valor actual una vez; un segundo Success sumaría otra
        assertEquals(1, emissions[0]);
    }
}
