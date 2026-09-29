package com.redur.electra.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.LogRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class ProfileViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private final UserSession session = new UserSession();
    private final File logFile = new File("electra_2026-09-28.log");

    private ProfileViewModel viewModel(File currentLog, Executor executor) {
        return new ProfileViewModel(session, new LogRepository(() -> null) {
            @Override
            public File findCurrentLogFile() {
                return currentLog;
            }
        }, executor);
    }

    @Test
    public void exponeElUsuarioDeLaSesion() {
        User user = new User("jperez", "Juan Pérez", "P01", List.of(), Set.of());
        session.start(user, new Credentials("jperez", "secreta"));

        assertEquals(user, viewModel(logFile, Runnable::run).getUser());
    }

    @Test
    public void sinSesion_noHayUsuario() {
        assertNull(viewModel(logFile, Runnable::run).getUser());
    }

    @Test
    public void enviarLog_conFichero_emiteSuccessConElFichero() {
        ProfileViewModel viewModel = viewModel(logFile, Runnable::run);

        viewModel.onSendLogClicked();

        assertTrue(viewModel.getLogShareState().getValue() instanceof UiState.Success);
        assertEquals(logFile, viewModel.getLogFile());
    }

    @Test
    public void enviarLog_sinFichero_emiteErrorExplicativo() {
        ProfileViewModel viewModel = viewModel(null, Runnable::run);

        viewModel.onSendLogClicked();

        UiState state = viewModel.getLogShareState().getValue();
        assertTrue(state instanceof UiState.Error);
        assertEquals(new UiText.Res(R.string.profile_log_unavailable), ((UiState.Error) state).message());
    }

    @Test
    public void enviarLog_pulsadoDosVecesMientrasCarga_buscaUnaSolaVez() {
        List<Runnable> pending = new ArrayList<>();
        ProfileViewModel viewModel = viewModel(logFile, pending::add);

        viewModel.onSendLogClicked();
        viewModel.onSendLogClicked();

        assertEquals(1, pending.size());
        assertTrue(viewModel.getLogShareState().getValue() instanceof UiState.Loading);
    }

    @Test
    public void trasAtenderElEnvio_vuelveAIdleSinFichero() {
        ProfileViewModel viewModel = viewModel(logFile, Runnable::run);
        viewModel.onSendLogClicked();

        viewModel.onLogShareHandled();

        assertTrue(viewModel.getLogShareState().getValue() instanceof UiState.Idle);
        assertNull(viewModel.getLogFile());
    }
}
