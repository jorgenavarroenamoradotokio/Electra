package com.redur.electra.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.repository.LogRepository;
import com.redur.electra.data.repository.LogUploadRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeFileApiService;
import com.redur.electra.fake.FileResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import okhttp3.MultipartBody;
import okio.Buffer;

public class ProfileViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final UserSession session = new UserSession();
    private final FakeFileApiService api = new FakeFileApiService();
    private File logFile;

    @Before
    public void setUp() throws IOException {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
        logFile = tmp.newFile("electra_2026-09-28.log");
    }

    private ProfileViewModel viewModel(File currentLog, Executor ioExecutor) {
        LogRepository logRepository = new LogRepository(() -> null) {
            @Override
            public File findCurrentLogFile() {
                return currentLog;
            }
        };
        return new ProfileViewModel(session, logRepository,
                new LogUploadRepository(api, session, Runnable::run, Runnable::run),
                ioExecutor, Runnable::run);
    }

    private ProfileViewModel viewModel(File currentLog) {
        return viewModel(currentLog, Runnable::run);
    }

    @Test
    public void exponeElUsuarioDeLaSesion() {
        User user = session.getUser();

        assertEquals(user, viewModel(logFile).getUser());
    }

    @Test
    public void sinSesion_noHayUsuario() {
        session.clear();

        assertNull(viewModel(logFile).getUser());
    }

    @Test
    public void enviarLog_aceptado_emiteSent() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        ProfileViewModel viewModel = viewModel(logFile);

        viewModel.onSendLogClicked();

        assertTrue(viewModel.getLogSendState().getValue() instanceof LogSendState.Sent);
    }

    @Test
    public void enviarLog_duranteLaSubida_emiteElPorcentaje() throws IOException {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)).deferred());
        ProfileViewModel viewModel = viewModel(logFile);

        viewModel.onSendLogClicked();
        assertEquals(new LogSendState.Sending(0), viewModel.getLogSendState().getValue());
        writeUploadedFile();

        assertEquals(new LogSendState.Sending(LogSendState.Sending.COMPLETE),
                viewModel.getLogSendState().getValue());
    }

    @Test
    public void enviarLog_sinFichero_fallaSinOfrecerReintento() {
        ProfileViewModel viewModel = viewModel(null);

        viewModel.onSendLogClicked();

        assertEquals(new LogSendState.Failed(new UiText.Res(R.string.profile_log_unavailable),
                LogSendState.Recovery.NONE), viewModel.getLogSendState().getValue());
        assertEquals(0, api.uploadCalls());
    }

    @Test
    public void primerFallo_ofreceReintentarConElMotivo() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        ProfileViewModel viewModel = viewModel(logFile);

        viewModel.onSendLogClicked();

        UiText noConnection = ErrorUiMapper.toUiText(new AppError.Network(NetworkType.NO_CONNECTION));
        assertEquals(new LogSendState.Failed(noConnection, LogSendState.Recovery.RETRY),
                viewModel.getLogSendState().getValue());
    }

    @Test
    public void segundoFalloSeguido_ofreceEnviarPorCorreo() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        ProfileViewModel viewModel = viewModel(logFile);

        viewModel.onSendLogClicked();
        viewModel.onLogSendHandled();
        viewModel.onSendLogClicked();

        assertEquals(new LogSendState.Failed(new UiText.Res(R.string.profile_log_upload_failed_email),
                LogSendState.Recovery.SEND_BY_EMAIL), viewModel.getLogSendState().getValue());
    }

    @Test
    public void unExitoIntermedio_reiniciaLaCuentaDeFallos() {
        ProfileViewModel viewModel = viewModel(logFile);

        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        viewModel.onSendLogClicked();
        viewModel.onLogSendHandled();
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        viewModel.onSendLogClicked();
        viewModel.onLogSendHandled();
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        viewModel.onSendLogClicked();

        LogSendState state = viewModel.getLogSendState().getValue();
        assertEquals(LogSendState.Recovery.RETRY, ((LogSendState.Failed) state).recovery());
    }

    @Test
    public void enviarPorCorreo_entregaElFicheroYReiniciaLaCuentaDeFallos() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        ProfileViewModel viewModel = viewModel(logFile);
        viewModel.onSendLogClicked();
        viewModel.onLogSendHandled();
        viewModel.onSendLogClicked();
        viewModel.onLogSendHandled();

        viewModel.onSendByEmailClicked();
        assertEquals(new LogSendState.ReadyToEmail(logFile), viewModel.getLogSendState().getValue());
        viewModel.onLogSendHandled();
        viewModel.onSendLogClicked();

        LogSendState state = viewModel.getLogSendState().getValue();
        assertEquals(LogSendState.Recovery.RETRY, ((LogSendState.Failed) state).recovery());
    }

    @Test
    public void enviarLog_pulsadoDosVecesDuranteElEnvio_subeUnaSolaVez() {
        List<Runnable> pending = new ArrayList<>();
        ProfileViewModel viewModel = viewModel(logFile, pending::add);

        viewModel.onSendLogClicked();
        viewModel.onSendLogClicked();

        assertEquals(1, pending.size());
        assertTrue(viewModel.getLogSendState().getValue() instanceof LogSendState.Sending);
    }

    @Test
    public void alDestruirse_cancelaLaSubidaEnCurso() {
        FakeCall<ApiResponseDTO<Boolean>> call = FakeCall.success(FileResponses.uploaded(true)).deferred();
        api.willReturnUpload(call);
        ProfileViewModel viewModel = viewModel(logFile);
        viewModel.onSendLogClicked();

        viewModel.onCleared();

        assertTrue(call.isCanceled());
    }

    @Test
    public void trasAtenderElEnvio_vuelveAIdle() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        ProfileViewModel viewModel = viewModel(logFile);
        viewModel.onSendLogClicked();

        viewModel.onLogSendHandled();

        assertTrue(viewModel.getLogSendState().getValue() instanceof LogSendState.Idle);
    }

    /** Simula a OkHttp escribiendo el fichero en la conexión. */
    private void writeUploadedFile() throws IOException {
        for (MultipartBody.Part part : api.lastUploadParts()) {
            if (part.headers().get("Content-Disposition").contains("filename=")) {
                part.body().writeTo(new Buffer());
            }
        }
    }
}
