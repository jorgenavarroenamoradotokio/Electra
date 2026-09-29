package com.redur.electra.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeFileApiService;
import com.redur.electra.fake.FileResponses;
import com.redur.electra.fake.PlaceResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import okhttp3.MultipartBody;
import okio.Buffer;

public class LogUploadRepositoryTest {

    @Rule
    public final TimberTestRule timber = new TimberTestRule();
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final FakeFileApiService api = new FakeFileApiService();
    private final UserSession session = new UserSession();
    /** El hilo principal se simula con una cola para comprobar qué ocurre antes de la entrega. */
    private final QueueExecutor mainExecutor = new QueueExecutor();
    private final LogUploadRepository repository =
            new LogUploadRepository(api, session, Runnable::run, mainExecutor);
    private final List<Integer> progress = new ArrayList<>();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
        logFile = tmp.newFile("electra_2026-09-29.log");
        Files.write(logFile.toPath(), "línea de log".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void subidaAceptada_entregaExito() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        Recorder callback = new Recorder();

        repository.upload(logFile, progress::add, callback);

        assertEquals(Boolean.TRUE, callback.result);
        assertEquals(1, callback.invocations);
    }

    @Test
    public void enviaElContenidoDelLogConSuNombre() throws IOException {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));

        repository.upload(logFile, progress::add, new Recorder());

        MultipartBody.Part file = filePart();
        assertTrue(file.headers().get("Content-Disposition").contains("filename=\"" + logFile.getName() + "\""));
        Buffer content = new Buffer();
        file.body().writeTo(content);
        assertEquals("línea de log", content.readUtf8());
    }

    @Test
    public void elProgresoLlegaPorElHiloPrincipal() throws IOException {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)).deferred());
        repository.upload(logFile, progress::add, new Recorder());

        // OkHttp escribe el cuerpo en su propio hilo; aquí se simula escribiéndolo directamente
        filePart().body().writeTo(new Buffer());

        assertTrue(progress.isEmpty());
        mainExecutor.runAll();
        assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
    }

    @Test
    public void backendRechazaElLog_entregaError() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(false)));
        Recorder callback = new Recorder();

        repository.upload(logFile, progress::add, callback);

        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void errorDeNegocio_entregaSuCodigo() {
        api.willReturnUpload(FakeCall.success(PlaceResponses.apiError("ERROR_F01", "Fichero no válido")));
        Recorder callback = new Recorder();

        repository.upload(logFile, progress::add, callback);

        assertEquals(new AppError.Api("ERROR_F01", "Fichero no válido"), callback.error);
    }

    @Test
    public void sinConexion_entregaErrorDeRed() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        Recorder callback = new Recorder();

        repository.upload(logFile, progress::add, callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
    }

    @Test
    public void sinSesion_noLlamaAlBackend() {
        session.clear();
        Recorder callback = new Recorder();

        repository.upload(logFile, progress::add, callback);

        assertEquals(0, api.uploadCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void ficheroIlegible_entregaErrorSinLlamarAlBackend() {
        Recorder callback = new Recorder();

        repository.upload(new File(tmp.getRoot(), "no_existe.log"), progress::add, callback);
        mainExecutor.runAll();

        assertEquals(0, api.uploadCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void cancelada_noEntregaResultadoNiProgreso() throws IOException {
        FakeCall<ApiResponseDTO<Boolean>> call = FakeCall.success(FileResponses.uploaded(true)).deferred();
        api.willReturnUpload(call);
        Recorder callback = new Recorder();

        Cancellable pending = repository.upload(logFile, progress::add, callback);
        filePart().body().writeTo(new Buffer());
        pending.cancel();
        call.complete();
        mainExecutor.runAll();

        assertTrue(call.isCanceled());
        assertTrue(progress.isEmpty());
        assertEquals(0, callback.invocations);
    }

    @NonNull
    private MultipartBody.Part filePart() {
        List<MultipartBody.Part> parts = api.lastUploadParts();
        assertNotNull(parts);
        for (MultipartBody.Part part : parts) {
            if (part.headers().get("Content-Disposition").contains("filename=")) {
                return part;
            }
        }
        throw new AssertionError("No se ha enviado el fichero");
    }

    private static final class QueueExecutor implements Executor {
        private final List<Runnable> queue = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(queue);
            queue.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static final class Recorder implements ResultCallback<Boolean> {
        @Nullable
        Boolean result;
        @Nullable
        AppError error;
        int invocations;

        @Override
        public void onSuccess(@NonNull Boolean result) {
            this.result = result;
            invocations++;
        }

        @Override
        public void onError(@NonNull AppError error) {
            this.error = error;
            invocations++;
        }
    }
}
