package com.redur.electra.ui.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;
import com.redur.electra.data.remote.mapper.PlaceMapper;
import com.redur.electra.data.repository.ChangePlaceRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakePlaceApiService;
import com.redur.electra.fake.FakePlaceDao;
import com.redur.electra.fake.PlaceResponses;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;

public class PlaceViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final Place P01 = new Place("P01", "Madrid Centro");
    private static final Place P02 = new Place("P02", "Valencia Norte");

    private final FakePlaceApiService api = new FakePlaceApiService();
    private final FakePlaceDao dao = new FakePlaceDao();
    private final UserSession session = new UserSession();

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    /** El ViewModel carga al crearse: cada test prepara backend y terminal antes. */
    private PlaceViewModel createViewModel() {
        ChangePlaceRepository repository = new ChangePlaceRepository(
                api, session, dao, new PlaceMapper(), Runnable::run, Runnable::run);
        return new PlaceViewModel(repository, session);
    }

    private void backendReturns(Place... places) {
        PlaceDTO[] dtos = new PlaceDTO[places.length];
        for (int i = 0; i < places.length; i++) {
            dtos[i] = new PlaceDTO(places[i].id(), places[i].description());
        }
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places(dtos)));
    }

    // ---------- Carga de plazas ----------

    @Test
    public void terminalVacio_cargaDelBackendYLasMuestra() {
        backendReturns(P02, P01);

        PlaceViewModel viewModel = createViewModel();

        assertTrue(viewModel.getLoadState().getValue() instanceof UiState.Success);
        assertEquals(List.of(P01, P02), viewModel.getPlaces().getValue());
        assertEquals(List.of(P01, P02), dao.getAll());
    }

    @Test
    public void terminalVacio_mientrasSincroniza_elEstadoEsLoading() {
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places()).deferred());

        PlaceViewModel viewModel = createViewModel();

        assertTrue(viewModel.getLoadState().getValue() instanceof UiState.Loading);
    }

    @Test
    public void conPlazasEnElTerminal_seMuestranSinEsperarAlBackend() {
        dao.seed(P01);
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places()).deferred());

        PlaceViewModel viewModel = createViewModel();

        assertTrue(viewModel.getLoadState().getValue() instanceof UiState.Success);
        assertEquals(List.of(P01), viewModel.getPlaces().getValue());
    }

    @Test
    public void laSincronizacion_anadeLasPlazasNuevasALaLista() {
        dao.seed(P01);
        FakeCall<ApiResponseDTO<List<PlaceDTO>>> call = FakeCall.success(PlaceResponses.places(
                new PlaceDTO(P01.id(), P01.description()),
                new PlaceDTO(P02.id(), P02.description()))).deferred();
        api.willReturnPlaces(call);
        PlaceViewModel viewModel = createViewModel();

        call.complete();

        assertEquals(List.of(P01, P02), viewModel.getPlaces().getValue());
    }

    @Test
    public void falloDeSincronizacionConPlazasEnElTerminal_siguenDisponibles() {
        dao.seed(P01, P02);
        api.willReturnPlaces(FakeCall.failure(new SocketTimeoutException("timeout")));

        PlaceViewModel viewModel = createViewModel();

        assertTrue(viewModel.getLoadState().getValue() instanceof UiState.Success);
        assertEquals(List.of(P01, P02), viewModel.getPlaces().getValue());
    }

    @Test
    public void falloDeSincronizacionConTerminalVacio_muestraElError() {
        api.willReturnPlaces(FakeCall.failure(new SocketTimeoutException("timeout")));

        PlaceViewModel viewModel = createViewModel();

        assertEquals(new UiState.Error(new UiText.Res(R.string.error_network_timeout)),
                viewModel.getLoadState().getValue());
    }

    @Test
    public void sinPlazasEnNingunSitio_muestraElEstadoVacio() {
        backendReturns();

        PlaceViewModel viewModel = createViewModel();

        assertEquals(new UiState.Error(new UiText.Res(R.string.change_plaza_empty)),
                viewModel.getLoadState().getValue());
    }

    @Test
    public void trasUnErrorDeCarga_sePuedeReintentar() {
        api.willReturnPlaces(FakeCall.failure(new SocketTimeoutException("timeout")));
        PlaceViewModel viewModel = createViewModel();

        backendReturns(P01, P02);
        viewModel.onRetryLoadClicked();

        assertEquals(2, api.placesCalls());
        assertTrue(viewModel.getLoadState().getValue() instanceof UiState.Success);
        assertEquals(List.of(P01, P02), viewModel.getPlaces().getValue());
    }

    @Test
    public void reintentarMientrasCarga_noDuplicaLaPeticion() {
        api.willReturnPlaces(FakeCall.success(PlaceResponses.places()).deferred());
        PlaceViewModel viewModel = createViewModel();

        viewModel.onRetryLoadClicked();

        assertEquals(1, api.placesCalls());
    }

    // ---------- Selección ----------

    @Test
    public void sinSeleccion_noSePuedeConfirmar() {
        backendReturns(P01, P02);

        PlaceViewModel viewModel = createViewModel();

        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void elegirLaPlazaActual_noSePuedeConfirmar() {
        backendReturns(P01, P02);
        PlaceViewModel viewModel = createViewModel();

        viewModel.onPlaceSelected(P01);

        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void elegirOtraPlaza_sePuedeConfirmar() {
        backendReturns(P01, P02);
        PlaceViewModel viewModel = createViewModel();

        viewModel.onPlaceSelected(P02);

        assertEquals(P02, viewModel.getSelectedPlace().getValue());
        assertTrue(viewModel.canConfirm());
    }

    // ---------- Cambio de plaza ----------

    @Test
    public void confirmar_cambiaLaPlazaYActualizaLaSesion() {
        backendReturns(P01, P02);
        api.willReturnChange(FakeCall.success(PlaceResponses.changed(true)));
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);

        viewModel.onConfirmClicked();

        assertTrue(viewModel.getChangePlaceState().getValue() instanceof UiState.Success);
        assertEquals("P02", session.getUser().plazaId());
        assertEquals("P02", viewModel.getCurrentPlazaId());
        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void confirmarSinSeleccion_noLlamaAlBackend() {
        backendReturns(P01, P02);
        PlaceViewModel viewModel = createViewModel();

        viewModel.onConfirmClicked();

        assertEquals(0, api.changeCalls());
        assertTrue(viewModel.getChangePlaceState().getValue() instanceof UiState.Idle);
    }

    @Test
    public void confirmarDosVecesMientrasCambia_noDuplicaLaPeticion() {
        backendReturns(P01, P02);
        api.willReturnChange(FakeCall.success(PlaceResponses.changed(true)).deferred());
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);

        viewModel.onConfirmClicked();
        viewModel.onConfirmClicked();

        assertEquals(1, api.changeCalls());
        assertTrue(viewModel.getChangePlaceState().getValue() instanceof UiState.Loading);
    }

    @Test
    public void mientrasCambia_noSePuedeElegirOtraPlaza() {
        backendReturns(P01, P02);
        api.willReturnChange(FakeCall.success(PlaceResponses.changed(true)).deferred());
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);
        viewModel.onConfirmClicked();

        viewModel.onPlaceSelected(P01);

        assertEquals(P02, viewModel.getSelectedPlace().getValue());
    }

    @Test
    public void errorAlCambiar_muestraElErrorYMantieneLaPlaza() {
        backendReturns(P01, P02);
        api.willReturnChange(FakeCall.failure(new SocketTimeoutException("timeout")));
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);

        viewModel.onConfirmClicked();

        assertEquals(new UiState.Error(new UiText.Res(R.string.error_network_timeout)),
                viewModel.getChangePlaceState().getValue());
        assertEquals("P01", session.getUser().plazaId());
        assertTrue(viewModel.canConfirm());
    }

    @Test
    public void elegirOtraPlazaTrasUnError_limpiaElError() {
        backendReturns(P01, P02);
        api.willReturnChange(FakeCall.failure(new SocketTimeoutException("timeout")));
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);
        viewModel.onConfirmClicked();

        viewModel.onPlaceSelected(P02);

        assertTrue(viewModel.getChangePlaceState().getValue() instanceof UiState.Idle);
    }

    @Test
    public void destruirElViewModel_cancelaLasPeticionesEnCurso() {
        dao.seed(P01, P02);
        FakeCall<ApiResponseDTO<List<PlaceDTO>>> sync =
                FakeCall.success(PlaceResponses.places()).deferred();
        api.willReturnPlaces(sync);
        FakeCall<ApiResponseDTO<Boolean>> change =
                FakeCall.success(PlaceResponses.changed(true)).deferred();
        api.willReturnChange(change);
        PlaceViewModel viewModel = createViewModel();
        viewModel.onPlaceSelected(P02);
        viewModel.onConfirmClicked();

        viewModel.onCleared();
        change.complete();

        assertTrue(sync.isCanceled());
        assertTrue(change.isCanceled());
        assertEquals("P01", session.getUser().plazaId());
    }
}
