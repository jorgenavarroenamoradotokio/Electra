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
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.request.bulto.BultoTypeRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;
import com.redur.electra.data.remote.mapper.BultoTypeMapper;
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

public class BultoTypeRepositoryTest {

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final FakeBultoApiService api = new FakeBultoApiService();
    private final UserSession session = new UserSession();
    private final BultoTypeRepository repository =
            new BultoTypeRepository(api, session, new BultoTypeMapper());
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
    public void envia_credencialesDeLaSesionEIdiomaDelTerminal() {
        Locale.setDefault(new Locale("es", "ES"));
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU)));

        repository.getBultoTypes(callback);

        BultoTypeRequestDTO request = api.lastTypesRequest();
        assertNotNull(request);
        assertEquals("jperez", request.username());
        assertEquals("secreta", request.password());
        assertEquals("es", request.language());
    }

    @Test
    public void terminalEnIngles_enviaUk() {
        Locale.setDefault(Locale.UK);
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU)));

        repository.getBultoTypes(callback);

        assertEquals("uk", api.lastTypesRequest().language());
    }

    @Test
    public void exito_entregaLosTiposEnElOrdenDelBackend() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(
                BultoResponses.PL, BultoResponses.BU, BultoResponses.MORE_THAN_3)));

        repository.getBultoTypes(callback);

        assertEquals(1, callback.invocations);
        assertEquals(List.of(
                new BultoType("020", "PL", "PALET", false),
                new BultoType("010", "BU", "BULTO PAQUETE", false),
                new BultoType("030", "+3", "+ DE 3 METROS", true)), callback.result);
    }

    @Test
    public void errorDeApi_seNotifica() {
        api.willReturnTypes(FakeCall.success(
                PlaceResponses.<List<BultoTypeDTO>>apiError("ERROR_A01", "Login incorrecto")));

        repository.getBultoTypes(callback);

        assertTrue(callback.error instanceof AppError.Api);
        assertEquals("ERROR_A01", ((AppError.Api) callback.error).code());
    }

    @Test
    public void sinConexion_errorDeRed() {
        api.willReturnTypes(FakeCall.<ApiResponseDTO<List<BultoTypeDTO>>>failure(new UnknownHostException()));

        repository.getBultoTypes(callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
    }

    @Test
    public void sinSesion_fallaSinLlamarAlBackend() {
        session.clear();

        repository.getBultoTypes(callback);

        assertEquals(0, api.typesCalls());
        assertTrue(callback.error instanceof AppError.Api);
    }

    @Test
    public void cancelada_noNotifica() {
        FakeCall<ApiResponseDTO<List<BultoTypeDTO>>> call =
                FakeCall.success(BultoResponses.types(BultoResponses.BU)).deferred();
        api.willReturnTypes(call);

        Cancellable operation = repository.getBultoTypes(callback);
        operation.cancel();
        call.complete();

        assertEquals(0, callback.invocations);
        assertNull(callback.result);
    }

    private static final class Recorder implements ResultCallback<List<BultoType>> {
        @Nullable
        List<BultoType> result;
        @Nullable
        AppError error;
        int invocations;

        @Override
        public void onSuccess(@NonNull List<BultoType> result) {
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
