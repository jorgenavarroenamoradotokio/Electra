package com.redur.electra.ui.label;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;
import com.redur.electra.data.repository.LabelRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeLabelApiService;
import com.redur.electra.fake.LabelResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Sin framework Android no se pueden crear Bitmaps: el decodificador de prueba registra lo que
 * recibe y devuelve null, así que el éxito completo (estado Ready) se comprueba en el dispositivo.
 */
public class LabelPreviewViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final Executor direct = Runnable::run;
    private final FakeLabelApiService api = new FakeLabelApiService();
    private final UserSession session = new UserSession();
    private final List<byte[]> decodedPages = new ArrayList<>();
    private final LabelPreviewViewModel.PageDecoder decoder = png -> {
        decodedPages.add(png);
        return null;
    };

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
    }

    @Test
    public void alAbrir_avisaDeQueSeEstaGenerando() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()).deferred());

        LabelPreviewViewModel viewModel = newViewModel();

        assertTrue(viewModel.getState().getValue() instanceof LabelPreviewState.Generating);
        assertEquals(1, api.createCalls());
    }

    @Test
    public void etiquetaRecibida_decodificaSusPaginas() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()));

        newViewModel();

        assertEquals(1, decodedPages.size());
        assertArrayEquals(LabelResponses.PAGE, decodedPages.get(0));
    }

    @Test
    public void paginaQueNoEsImagen_explicaQueLaEtiquetaLlegoDanada() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()));

        LabelPreviewViewModel viewModel = newViewModel();

        assertEquals(new LabelPreviewState.Failed(new UiText.Res(R.string.label_preview_unreadable)),
                viewModel.getState().getValue());
    }

    @Test
    public void sinConexion_muestraElErrorDeRed() {
        api.willReturnCreate(FakeCall.failure(new UnknownHostException()));

        LabelPreviewViewModel viewModel = newViewModel();

        assertEquals(new LabelPreviewState.Failed(
                        ErrorUiMapper.toUiText(new AppError.Network(NetworkType.NO_CONNECTION))),
                viewModel.getState().getValue());
    }

    @Test
    public void reintentarTrasFallo_vuelveAGenerar() {
        api.willReturnCreate(FakeCall.failure(new UnknownHostException()));
        LabelPreviewViewModel viewModel = newViewModel();
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()).deferred());

        viewModel.onRetryClicked();

        assertTrue(viewModel.getState().getValue() instanceof LabelPreviewState.Generating);
        assertEquals(2, api.createCalls());
    }

    @Test
    public void reintentarMientrasSeGenera_noRepiteLaPeticion() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()).deferred());
        LabelPreviewViewModel viewModel = newViewModel();

        viewModel.onRetryClicked();

        assertEquals(1, api.createCalls());
    }

    @Test
    public void salirDeLaPantalla_cancelaLaPeticion() {
        FakeCall<ApiResponseDTO<ZplLabelDTO>> call = FakeCall.success(LabelResponses.onePage()).deferred();
        api.willReturnCreate(call);
        LabelPreviewViewModel viewModel = newViewModel();

        viewModel.onCleared();

        assertTrue(call.isCanceled());
    }

    private LabelPreviewViewModel newViewModel() {
        return new LabelPreviewViewModel(new LabelRepository(api, session, direct, direct), decoder,
                direct, direct);
    }
}
