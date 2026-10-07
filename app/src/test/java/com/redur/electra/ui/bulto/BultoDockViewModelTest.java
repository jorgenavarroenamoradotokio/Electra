package com.redur.electra.ui.bulto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.bulto.BultoDock;
import com.redur.electra.data.repository.BultoDockRepository;
import com.redur.electra.ui.bulto.muelle.BultoDockState;
import com.redur.electra.ui.bulto.muelle.BultoDockViewModel;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BultoDockViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final String BARCODE = "14600400072459116400016247001";
    private static final String OTHER_BARCODE = "14600400072459116400016247002";
    private static final BultoDock Z14 = new BultoDock("Z14", "ZAR", "50237", "España");
    private static final BultoDock Z15 = new BultoDock("Z15", "MAD", "28001", "España");

    private final ManualRepository repository = new ManualRepository();

    @Test
    public void alAbrir_esperaUnBultoSinConsultar() {
        BultoDockViewModel viewModel = newViewModel();

        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Waiting);
        assertTrue(repository.requests.isEmpty());
    }

    @Test
    public void bultoLeido_buscaYMuestraSuMuelle() {
        BultoDockViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned("  " + BARCODE + " ");
        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Loading);
        repository.respond(0, Optional.of(Z14));

        assertEquals(new BultoDockState.Found(BARCODE, Z14), viewModel.getState().getValue());
        assertEquals(BARCODE, repository.requests.get(0).barcode);
    }

    @Test
    public void lecturaVacia_seIgnora() {
        BultoDockViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned("   ");
        viewModel.onBarcodeScanned(null);

        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Waiting);
        assertTrue(repository.requests.isEmpty());
    }

    @Test
    public void mismaLecturaMientrasSeConsulta_noRepiteLaPeticion() {
        BultoDockViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned(BARCODE);
        viewModel.onBarcodeScanned(BARCODE);

        assertEquals(1, repository.requests.size());
    }

    @Test
    public void lecturaNuevaMientrasSeConsulta_descartaLaAnterior() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);

        viewModel.onBarcodeScanned(OTHER_BARCODE);
        repository.respond(1, Optional.of(Z15));

        assertTrue(repository.requests.get(0).canceled);
        assertEquals(new BultoDockState.Found(OTHER_BARCODE, Z15), viewModel.getState().getValue());
    }

    @Test
    public void bultoSinMuelle_loIndica() {
        BultoDockViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned(BARCODE);
        repository.respond(0, Optional.empty());

        assertEquals(new BultoDockState.NotFound(BARCODE), viewModel.getState().getValue());
    }

    @Test
    public void fallo_reintentarVuelveAConsultarElMismoBulto() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);
        repository.fail(0);
        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Failed);

        viewModel.onRetryClicked();
        repository.respond(1, Optional.of(Z14));

        assertEquals(BARCODE, repository.requests.get(1).barcode);
        assertEquals(new BultoDockState.Found(BARCODE, Z14), viewModel.getState().getValue());
    }

    @Test
    public void reintentarSinFallo_noConsulta() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);

        viewModel.onRetryClicked();

        assertEquals(1, repository.requests.size());
    }

    @Test
    public void muelleALaVista_otraLecturaBuscaElDelBultoSiguiente() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);
        repository.respond(0, Optional.of(Z14));

        viewModel.onBarcodeScanned(OTHER_BARCODE);
        repository.respond(1, Optional.of(Z15));

        assertEquals(new BultoDockState.Found(OTHER_BARCODE, Z15), viewModel.getState().getValue());
    }

    @Test
    public void cancelarConMuelleALaVista_esperaElSiguienteBulto() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);
        repository.respond(0, Optional.of(Z14));

        viewModel.onCancelClicked();

        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Waiting);
    }

    @Test
    public void cancelarDuranteLaConsulta_descartaLaRespuesta() {
        BultoDockViewModel viewModel = newViewModel();
        viewModel.onBarcodeScanned(BARCODE);

        viewModel.onCancelClicked();

        assertTrue(repository.requests.get(0).canceled);
        assertTrue(viewModel.getState().getValue() instanceof BultoDockState.Waiting);
    }

    @Test
    public void procesoRecreado_vuelveABuscarElMuelleDelBultoLeido() {
        SavedStateHandle savedState = new SavedStateHandle();
        BultoDockViewModel viewModel = new BultoDockViewModel(savedState, repository);
        viewModel.onBarcodeScanned(BARCODE);
        repository.respond(0, Optional.of(Z14));

        BultoDockViewModel recreated = new BultoDockViewModel(copyOf(savedState), repository);
        repository.respond(1, Optional.of(Z14));

        assertEquals(new BultoDockState.Found(BARCODE, Z14), recreated.getState().getValue());
    }

    @Test
    public void procesoRecreadoTrasCancelar_esperaUnBulto() {
        SavedStateHandle savedState = new SavedStateHandle();
        BultoDockViewModel viewModel = new BultoDockViewModel(savedState, repository);
        viewModel.onBarcodeScanned(BARCODE);
        repository.respond(0, Optional.of(Z14));
        viewModel.onCancelClicked();

        BultoDockViewModel recreated = new BultoDockViewModel(copyOf(savedState), repository);

        assertTrue(recreated.getState().getValue() instanceof BultoDockState.Waiting);
        assertEquals(1, repository.requests.size());
    }

    @NonNull
    private BultoDockViewModel newViewModel() {
        return new BultoDockViewModel(new SavedStateHandle(), repository);
    }

    @NonNull
    private static SavedStateHandle copyOf(@NonNull SavedStateHandle handle) {
        SavedStateHandle copy = new SavedStateHandle();
        for (String key : handle.keys()) {
            copy.set(key, handle.get(key));
        }
        return copy;
    }

    /** Guarda cada consulta para responderla cuando lo decida el test, como haría la red. */
    private static final class ManualRepository extends BultoDockRepository {

        final List<Request> requests = new ArrayList<>();

        void respond(int index, @NonNull Optional<BultoDock> dock) {
            Request request = requests.get(index);
            if (!request.canceled) {
                request.callback.onSuccess(dock);
            }
        }

        void fail(int index) {
            Request request = requests.get(index);
            if (!request.canceled) {
                request.callback.onError(new AppError.Api(null, null));
            }
        }

        @NonNull
        @Override
        public Cancellable getDock(@NonNull String barcode,
                                   @NonNull ResultCallback<Optional<BultoDock>> callback) {
            Request request = new Request(barcode, callback);
            requests.add(request);
            return () -> request.canceled = true;
        }
    }

    private static final class Request {

        final String barcode;
        final ResultCallback<Optional<BultoDock>> callback;
        boolean canceled;

        Request(@NonNull String barcode, @NonNull ResultCallback<Optional<BultoDock>> callback) {
            this.barcode = barcode;
            this.callback = callback;
        }
    }
}
