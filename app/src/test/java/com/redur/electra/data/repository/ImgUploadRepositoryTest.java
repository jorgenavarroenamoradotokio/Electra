package com.redur.electra.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okio.Buffer;

public class ImgUploadRepositoryTest {

    private static final String IMAGE_URI = "content://media/picker/0/1";
    private static final byte[] IMAGE_BYTES = {1, 2, 3, 4};

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final FakeFileApiService api = new FakeFileApiService();
    private final UserSession session = new UserSession();
    /** El hilo principal se simula con una cola para comprobar qué ocurre antes de la entrega. */
    private final QueueExecutor mainExecutor = new QueueExecutor();
    private final List<Integer> progress = new ArrayList<>();
    private final Locale defaultLocale = Locale.getDefault();

    @Nullable
    private ImgUploadRepository.ImageContent image =
            new ImgUploadRepository.ImageContent("IMG_1.jpg", "image/png", IMAGE_BYTES);
    @Nullable
    private String readUri;

    private final ImgUploadRepository repository = new ImgUploadRepository(uri -> {
        readUri = uri;
        if (image == null) {
            throw new FileNotFoundException(uri);
        }
        return image;
    }, api, session, Runnable::run, mainExecutor);

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void subidaAceptada_entregaExitoPorElEndpointDeImagenes() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);

        assertEquals(Boolean.TRUE, callback.result);
        assertEquals(1, callback.invocations);
        assertEquals(1, api.imgUploadCalls());
        assertEquals(IMAGE_URI, readUri);
    }

    @Test
    public void enviaLaImagenConSuNombreYTipo() throws IOException {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));

        repository.upload(IMAGE_URI, progress::add, new Recorder());

        MultipartBody.Part file = part("filename=");
        assertTrue(file.headers().get("Content-Disposition").contains("filename=\"IMG_1.jpg\""));
        assertEquals(MediaType.get("image/png"), file.body().contentType());
        Buffer content = new Buffer();
        file.body().writeTo(content);
        assertEquals(IMAGE_BYTES.length, content.size());
    }

    @Test
    public void tipoDesconocido_seEnviaComoJpeg() {
        image = new ImgUploadRepository.ImageContent("IMG_1", null, IMAGE_BYTES);
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));

        repository.upload(IMAGE_URI, progress::add, new Recorder());

        assertEquals(MediaType.get("image/jpeg"), part("filename=").body().contentType());
    }

    @Test
    public void enIngles_enviaElIdiomaQueEsperaElBackend() throws IOException {
        Locale.setDefault(Locale.ENGLISH);
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));

        repository.upload(IMAGE_URI, progress::add, new Recorder());

        Buffer language = new Buffer();
        part("name=\"language\"").body().writeTo(language);
        assertEquals("uk", language.readUtf8());
    }

    @Test
    public void elProgresoLlegaPorElHiloPrincipal() throws IOException {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)).deferred());
        repository.upload(IMAGE_URI, progress::add, new Recorder());

        // OkHttp escribe el cuerpo en su propio hilo; aquí se simula escribiéndolo directamente
        part("filename=").body().writeTo(new Buffer());

        assertTrue(progress.isEmpty());
        mainExecutor.runAll();
        assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
    }

    @Test
    public void backendRechazaLaImagen_entregaError() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(false)));
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);

        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void errorDeNegocio_entregaSuCodigo() {
        api.willReturnUpload(FakeCall.success(PlaceResponses.apiError("ERROR_F01", "Fichero no válido")));
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);

        assertEquals(new AppError.Api("ERROR_F01", "Fichero no válido"), callback.error);
    }

    @Test
    public void sinConexion_entregaErrorDeRed() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
    }

    @Test
    public void sinSesion_noLeeNiLlamaAlBackend() {
        session.clear();
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);

        assertNull(readUri);
        assertEquals(0, api.uploadCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void imagenIlegible_entregaErrorSinLlamarAlBackend() {
        image = null;
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);
        mainExecutor.runAll();

        assertEquals(0, api.uploadCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void imagenVacia_entregaErrorSinLlamarAlBackend() {
        image = new ImgUploadRepository.ImageContent("IMG_1.jpg", "image/jpeg", new byte[0]);
        Recorder callback = new Recorder();

        repository.upload(IMAGE_URI, progress::add, callback);
        mainExecutor.runAll();

        assertEquals(0, api.uploadCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void cancelada_noEntregaResultadoNiProgreso() throws IOException {
        FakeCall<ApiResponseDTO<Boolean>> call = FakeCall.success(FileResponses.uploaded(true)).deferred();
        api.willReturnUpload(call);
        Recorder callback = new Recorder();

        Cancellable pending = repository.upload(IMAGE_URI, progress::add, callback);
        part("filename=").body().writeTo(new Buffer());
        pending.cancel();
        call.complete();
        mainExecutor.runAll();

        assertTrue(call.isCanceled());
        assertTrue(progress.isEmpty());
        assertEquals(0, callback.invocations);
    }

    @NonNull
    private MultipartBody.Part part(@NonNull String disposition) {
        List<MultipartBody.Part> parts = api.lastUploadParts();
        assertNotNull(parts);
        for (MultipartBody.Part part : parts) {
            if (part.headers().get("Content-Disposition").contains(disposition)) {
                return part;
            }
        }
        throw new AssertionError("No se ha enviado la parte " + disposition);
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
