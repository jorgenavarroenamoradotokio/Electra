package com.redur.electra.ui.bulto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;
import com.redur.electra.data.remote.mapper.BultoTypeMapper;
import com.redur.electra.data.repository.BultoTypeRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.BultoResponses;
import com.redur.electra.fake.FakeBultoApiService;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.rule.TimberTestRule;
import com.redur.electra.ui.bulto.tipo.BultoTypeState;
import com.redur.electra.ui.bulto.tipo.BultoTypeViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

public class BultoTypeViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private static final BultoType PL = new BultoType("020", "PL", "PALET", false);

    private final FakeBultoApiService api = new FakeBultoApiService();
    private final UserSession session = new UserSession();

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    @Test
    public void alAbrir_cargaLosTipos() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU)).deferred());

        BultoTypeViewModel viewModel = newViewModel();

        assertTrue(viewModel.getState().getValue() instanceof BultoTypeState.Loading);
        assertEquals(1, api.typesCalls());
    }

    @Test
    public void tiposRecibidos_listosSinNingunoElegido() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU, BultoResponses.PL)));

        BultoTypeViewModel viewModel = newViewModel();

        BultoTypeState state = viewModel.getState().getValue();
        assertTrue(state instanceof BultoTypeState.Ready);
        assertEquals(2, ((BultoTypeState.Ready) state).types().size());
        assertNull(viewModel.getSelectedType().getValue());
        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void elegirTipo_permiteAsignar() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU, BultoResponses.PL)));
        BultoTypeViewModel viewModel = newViewModel();

        viewModel.onTypeSelected(PL);

        assertEquals(PL, viewModel.getSelectedType().getValue());
        assertTrue(viewModel.canConfirm());
    }

    @Test
    public void sinTipos_estadoVacio() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types()));

        BultoTypeViewModel viewModel = newViewModel();

        assertTrue(viewModel.getState().getValue() instanceof BultoTypeState.Empty);
    }

    @Test
    public void fallo_permiteReintentar() {
        api.willReturnTypes(FakeCall.<ApiResponseDTO<List<BultoTypeDTO>>>failure(new UnknownHostException()));
        BultoTypeViewModel viewModel = newViewModel();
        assertTrue(viewModel.getState().getValue() instanceof BultoTypeState.Failed);
        assertFalse(viewModel.canConfirm());

        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.PL)));
        viewModel.onRetryClicked();

        assertTrue(viewModel.getState().getValue() instanceof BultoTypeState.Ready);
        assertEquals(2, api.typesCalls());
    }

    @Test
    public void reintentarDuranteLaCarga_noRepiteLaPeticion() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU)).deferred());
        BultoTypeViewModel viewModel = newViewModel();

        viewModel.onRetryClicked();

        assertEquals(1, api.typesCalls());
    }

    @Test
    public void fijar_seConserva() {
        api.willReturnTypes(FakeCall.success(BultoResponses.types(BultoResponses.BU)));
        BultoTypeViewModel viewModel = newViewModel();

        viewModel.onFixedChanged(true);

        assertEquals(Boolean.TRUE, viewModel.getFixedForNextReadings().getValue());
    }

    private BultoTypeViewModel newViewModel() {
        return new BultoTypeViewModel(new BultoTypeRepository(api, session, new BultoTypeMapper()));
    }
}
