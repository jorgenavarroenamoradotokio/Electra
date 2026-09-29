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
import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;
import com.redur.electra.data.remote.mapper.PlaceMapper;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakePlaceApiService;
import com.redur.electra.fake.FakePlaceDao;
import com.redur.electra.fake.PlaceResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class ChangePlaceRepositoryTest {

    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private static final Place P01 = new Place("P01", "Madrid Centro");
    private static final Place P02 = new Place("P02", "Valencia Norte");
    private static final Place P03 = new Place("P03", "Sevilla Sur");

    private final FakePlaceApiService api = new FakePlaceApiService();
    private final FakePlaceDao dao = new FakePlaceDao();
    private final UserSession session = new UserSession();
    /** El hilo principal se simula con una cola para comprobar qué ocurre antes de la entrega. */
    private final QueueExecutor mainExecutor = new QueueExecutor();
    private final ChangePlaceRepository repository = new ChangePlaceRepository(
            api, session, dao, new PlaceMapper(), Runnable::run, mainExecutor);

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    // ---------- Plazas del terminal ----------

    @Test
    public void cache_devuelveLasPlazasGuardadasOrdenadas() {
        dao.seed(P02, P01);
        Recorder<List<Place>> callback = new Recorder<>();

        repository.getCachedPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(P01, P02), callback.result);
    }

    @Test
    public void cacheIlegible_seTrataComoVacia() {
        dao.failWith(true);
        Recorder<List<Place>> callback = new Recorder<>();

        repository.getCachedPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(), callback.result);
    }

    @Test
    public void cacheCancelada_noEntregaResultado() {
        Recorder<List<Place>> callback = new Recorder<>();

        Cancellable pending = repository.getCachedPlaces(callback);
        pending.cancel();
        mainExecutor.runAll();

        assertEquals(0, callback.invocations);
    }

    // ---------- Sincronización ----------

    @Test
    public void terminalVacio_guardaTodasLasPlazasDelBackend() {
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places(dto(P02), dto(P01))));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(P01, P02), callback.result);
        assertEquals(List.of(P01, P02), dao.getAll());
    }

    @Test
    public void sincronizar_soloAnadeLasPlazasNuevasYNoModificaLasExistentes() {
        Place p01Local = new Place("P01", "Descripción local");
        dao.seed(p01Local);
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places(
                new PlaceDTO("P01", "Descripción del backend"), dto(P03))));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(p01Local, P03), callback.result);
        assertEquals(List.of(p01Local, P03), dao.getAll());
    }

    @Test
    public void sincronizar_noBorraPlazasQueElBackendYaNoDevuelve() {
        dao.seed(P01, P02);
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places(dto(P03))));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(P01, P02, P03), callback.result);
    }

    @Test
    public void sincronizar_enviaLasCredencialesDeLaSesion() {
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places()));

        repository.syncPlaces(new Recorder<>());

        assertNotNull(api.lastPlacesRequest());
        assertEquals("jperez", api.lastPlacesRequest().username());
        assertEquals("secreta", api.lastPlacesRequest().password());
    }

    @Test
    public void terminalSinEscritura_devuelveLasPlazasDelBackend() {
        dao.failWith(true);
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places(dto(P02), dto(P01))));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);
        mainExecutor.runAll();

        assertEquals(List.of(P01, P02), callback.result);
    }

    @Test
    public void errorDeRed_seNotificaSinTocarElTerminal() {
        dao.seed(P01);
        api.willReturnPlaces(FakeCall.failure(new UnknownHostException("sin red")));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);

        assertEquals(new AppError.Network(NetworkType.NO_CONNECTION), callback.error);
        assertEquals(List.of(P01), dao.getAll());
    }

    @Test
    public void errorDeNegocio_seNotifica() {
        api.willReturnPlaces(FakeCall.success(PlaceResponses.apiError("ERROR_P01", "Sin plazas")));
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);

        assertEquals(new AppError.Api("ERROR_P01", "Sin plazas"), callback.error);
    }

    @Test
    public void sincronizacionCancelada_noEntregaResultadoNiSigueLaPeticion() {
        FakeCall<ApiResponseDTO<List<PlaceDTO>>> call =
                FakeCall.success(PlaceResponses.places(dto(P01))).deferred();
        api.willReturnPlaces(call);
        Recorder<List<Place>> callback = new Recorder<>();

        Cancellable pending = repository.syncPlaces(callback);
        pending.cancel();
        call.complete();
        mainExecutor.runAll();

        assertTrue(call.isCanceled());
        assertEquals(0, callback.invocations);
    }

    @Test
    public void sinSesion_sincronizarNotificaErrorSinLlamarAlBackend() {
        session.clear();
        Recorder<List<Place>> callback = new Recorder<>();

        repository.syncPlaces(callback);

        assertNotNull(callback.error);
        assertEquals(0, api.placesCalls());
    }

    // ---------- Cambio de plaza ----------

    @Test
    public void cambioAceptado_actualizaLaPlazaDeLaSesion() {
        api.willReturnChange(FakeCall.success(PlaceResponses.changed(true)));
        Recorder<Boolean> callback = new Recorder<>();

        repository.updatePlzs("P02", callback);

        assertEquals(Boolean.TRUE, callback.result);
        assertEquals("P02", session.getUser().plazaId());
        assertNotNull(api.lastChangeRequest());
        assertEquals("P02", api.lastChangeRequest().plzsId());
        assertEquals("secreta", api.lastChangeRequest().password());
    }

    @Test
    public void cambioRechazado_noModificaLaSesion() {
        api.willReturnChange(FakeCall.success(PlaceResponses.changed(false)));
        Recorder<Boolean> callback = new Recorder<>();

        repository.updatePlzs("P02", callback);

        assertNotNull(callback.error);
        assertNull(callback.result);
        assertEquals("P01", session.getUser().plazaId());
    }

    @Test
    public void cambioConErrorDeNegocio_noModificaLaSesion() {
        api.willReturnChange(FakeCall.success(PlaceResponses.apiError("ERROR_A01", "Credenciales")));
        Recorder<Boolean> callback = new Recorder<>();

        repository.updatePlzs("P02", callback);

        assertEquals(new AppError.Api("ERROR_A01", "Credenciales"), callback.error);
        assertEquals("P01", session.getUser().plazaId());
    }

    // ---------- Utilidades ----------

    private static PlaceDTO dto(Place place) {
        return new PlaceDTO(place.id(), place.description());
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

    private static final class Recorder<T> implements ResultCallback<T> {
        @Nullable
        T result;
        @Nullable
        AppError error;
        int invocations;

        @Override
        public void onSuccess(@NonNull T result) {
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
