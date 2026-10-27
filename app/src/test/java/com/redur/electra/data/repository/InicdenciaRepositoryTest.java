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
import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.request.incidencia.IncidenciaRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;
import com.redur.electra.data.remote.mapper.IncidenciaMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.BultoResponses;
import com.redur.electra.fake.FakeBultoApiService;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.PlaceResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InicdenciaRepositoryTest {

    private static final String CODIGO_APLICACION = "CAR";

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final FakeBultoApiService api = new FakeBultoApiService();
    private final UserSession session = new UserSession();
    private final InicdenciaRepository repository =
            new InicdenciaRepository(api, session, new IncidenciaMapper());
    private final Recorder callback = new Recorder();
    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void envia_credencialesIdiomaYCodigoDeAplicacion() {
        Locale.setDefault(new Locale("es", "ES"));
        api.willReturnIncidencias(FakeCall.success(BultoResponses.incidencias(BultoResponses.A16)));

        repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);

        IncidenciaRequestDTO request = api.lastIncidenciasRequest();
        assertNotNull(request);
        assertEquals("jperez", request.username());
        assertEquals("secreta", request.password());
        assertEquals("es", request.language());
        assertEquals(CODIGO_APLICACION, request.codigoAplicacion());
    }

    @Test
    public void exito_entregaLasIncidenciasEnElOrdenDelBackend() {
        api.willReturnIncidencias(FakeCall.success(
                BultoResponses.incidencias(BultoResponses.A21, BultoResponses.A16)));

        repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);

        assertEquals(1, callback.invocations);
        assertEquals(List.of(
                new BultoIncidencia("A21", "BULTO ABIERTO", true, false),
                new BultoIncidencia("A16", "BULTO DAÑADO", false, false)), callback.result);
    }

    @Test
    public void errorDeApi_seNotifica() {
        api.willReturnIncidencias(FakeCall.success(
                PlaceResponses.<List<IncidenciaDTO>>apiError("ERROR_A01", "Login incorrecto")));

        repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);

        assertTrue(callback.error instanceof AppError.Api);
        assertEquals("ERROR_A01", ((AppError.Api) callback.error).code());
    }

    @Test
    public void sinConexion_errorDeRed() {
        api.willReturnIncidencias(FakeCall.<ApiResponseDTO<List<IncidenciaDTO>>>failure(new UnknownHostException()));

        repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
    }

    @Test
    public void sinSesion_fallaSinLlamarAlBackend() {
        session.clear();

        repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);

        assertEquals(0, api.incidenciasCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void cancelada_noNotifica() {
        FakeCall<ApiResponseDTO<List<IncidenciaDTO>>> call =
                FakeCall.success(BultoResponses.incidencias(BultoResponses.A16)).deferred();
        api.willReturnIncidencias(call);

        Cancellable operation = repository.getIncidenciasPorCodigo(CODIGO_APLICACION, callback);
        operation.cancel();
        call.complete();

        assertEquals(0, callback.invocations);
        assertNull(callback.result);
    }

    private static final class Recorder implements ResultCallback<List<BultoIncidencia>> {
        @Nullable
        List<BultoIncidencia> result;
        @Nullable
        AppError error;
        int invocations;

        @Override
        public void onSuccess(@NonNull List<BultoIncidencia> result) {
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
