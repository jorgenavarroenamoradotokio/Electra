package com.redur.electra.data.repository;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.data.model.label.ZplLabel;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.request.label.ZplLabelRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiErrorDetailResponseDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeLabelApiService;
import com.redur.electra.fake.LabelResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

import retrofit2.Response;

public class LabelRepositoryTest {

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final Executor direct = Runnable::run;
    private final FakeLabelApiService api = new FakeLabelApiService();
    private final UserSession session = new UserSession();
    private final RecordingCallback callback = new RecordingCallback();
    private Locale previousLocale;

    @Before
    public void setUp() {
        previousLocale = Locale.getDefault();
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(previousLocale);
    }

    @Test
    public void etiquetaGenerada_entregaZplYPaginasDecodificadas() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()));

        newRepository().createZplLabel(callback);

        assertNotNull(callback.label);
        assertEquals(LabelResponses.ZPL, callback.label.zpl());
        assertEquals(1, callback.label.pages().size());
        assertArrayEquals(LabelResponses.PAGE, callback.label.pages().get(0));
    }

    @Test
    public void peticion_llevaCredencialesPlazaEIdiomaDelBackend() {
        Locale.setDefault(Locale.ENGLISH);
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()));

        newRepository().createZplLabel(callback);

        ZplLabelRequestDTO request = api.lastRequest();
        assertNotNull(request);
        assertEquals("jperez", request.username());
        assertEquals("secreta", request.password());
        assertEquals("P01", request.plzsId());
        assertEquals("uk", request.language());
    }

    @Test
    public void toStringDeLaPeticion_noExponeLaContrasena() {
        ZplLabelRequestDTO request = new ZplLabelRequestDTO("jperez", "secreta", "es", "P01");

        assertFalse(request.toString().contains("secreta"));
    }

    @Test
    public void paginaConSaltosDeLinea_seDecodifica() {
        String encoded = Base64.getMimeEncoder().encodeToString(new byte[200]);
        api.willReturnCreate(FakeCall.success(LabelResponses.label(List.of(encoded))));

        newRepository().createZplLabel(callback);

        assertNotNull(callback.label);
        assertEquals(200, callback.label.pages().get(0).length);
    }

    @Test
    public void sinPaginas_esError() {
        api.willReturnCreate(FakeCall.success(LabelResponses.label(List.of())));

        newRepository().createZplLabel(callback);

        assertNull(callback.label);
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void paginaNoBase64_esError() {
        api.willReturnCreate(FakeCall.success(LabelResponses.label(List.of("esto no es base64!"))));

        newRepository().createZplLabel(callback);

        assertNull(callback.label);
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void paginaNula_esError() {
        List<String> content = new ArrayList<>();
        content.add(null);
        api.willReturnCreate(FakeCall.success(LabelResponses.label(content)));

        newRepository().createZplLabel(callback);

        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void errorDeNegocio_seEntregaConSuCodigo() {
        Response<ApiResponseDTO<ZplLabelDTO>> response = Response.success(new ApiResponseDTO<>(0, null,
                List.of(new ApiErrorDetailResponseDTO("ERROR_L01", "Sin etiqueta", null, "es")), null));
        api.willReturnCreate(FakeCall.success(response));

        newRepository().createZplLabel(callback);

        assertEquals(new AppError.Api("ERROR_L01", "Sin etiqueta"), callback.error);
    }

    @Test
    public void timeout_esErrorDeRed() {
        api.willReturnCreate(FakeCall.failure(new SocketTimeoutException()));

        newRepository().createZplLabel(callback);

        assertEquals(new AppError.Network(NetworkType.TIMEOUT), callback.error);
    }

    @Test
    public void sinSesion_fallaSinLlamarAlBackend() {
        session.clear();

        newRepository().createZplLabel(callback);

        assertTrue(callback.error instanceof AppError.Api);
        assertEquals(0, api.createCalls());
    }

    @Test
    public void cancelada_noEntregaResultado() {
        FakeCall<ApiResponseDTO<ZplLabelDTO>> call = FakeCall.success(LabelResponses.onePage()).deferred();
        api.willReturnCreate(call);

        Cancellable request = newRepository().createZplLabel(callback);
        request.cancel();
        call.complete();

        assertTrue(call.isCanceled());
        assertEquals(0, callback.calls);
    }

    private LabelRepository newRepository() {
        return new LabelRepository(api, session, direct, direct);
    }

    private static final class RecordingCallback implements ResultCallback<ZplLabel> {
        @Nullable
        ZplLabel label;
        @Nullable
        AppError error;
        int calls;

        @Override
        public void onSuccess(@NonNull ZplLabel result) {
            calls++;
            label = result;
        }

        @Override
        public void onError(@NonNull AppError error) {
            calls++;
            this.error = error;
        }
    }
}
