package com.redur.electra.ui.bulto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.R;
import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.bulto.OperationType;
import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.repository.InicdenciaRepository;
import com.redur.electra.ui.bulto.incidencia.BultoIncidenceEntry;
import com.redur.electra.ui.bulto.incidencia.BultoIncidenceState;
import com.redur.electra.ui.bulto.incidencia.BultoIncidenceViewModel;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class BultoIncidenceViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final String BARCODE = "14600400072459116400016247001";
    private static final String OTHER_BARCODE = "14600400072459116400016247002";
    private static final String PHOTO_URI = "content://com.redur.electra.fileprovider/photos/IMG_1.jpg";
    private static final BultoIncidencia A16 = new BultoIncidencia("A16", "Bulto dañado", false, false);
    private static final BultoIncidencia A21 = new BultoIncidencia("A21", "Bulto abierto", true, false);
    private static final BultoIncidencia I11 = new BultoIncidencia("I11", "Falta mercancía", false, true);

    private final ScriptedRepository repository = new ScriptedRepository();

    @Test
    public void desdeElMenu_esperaAlBulto_sinPedirIncidencias() {
        BultoIncidenceViewModel viewModel = new BultoIncidenceViewModel(new SavedStateHandle(), repository);

        assertEquals(OperationType.CARGA, viewModel.getArgs().operationType());
        assertNull(viewModel.getArgs().barcode());
        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.WaitingForBarcode);
        assertEquals(0, repository.loads);
        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void desdeElMenu_alLeerElBulto_cargaLasIncidenciasDeCAR() {
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = new BultoIncidenceViewModel(new SavedStateHandle(), repository);

        viewModel.onBarcodeScanned(BARCODE);

        assertEquals("CAR", repository.lastCodigoAplicacion);
        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.Ready);
    }

    @Test
    public void conBultoEnLosArgumentos_cargaAlAbrir() {
        repository.willReturn(List.of(A16));

        newViewModel();

        assertEquals(1, repository.loads);
    }

    @Test
    public void leerOtroBulto_vuelveACargar_yReleerElMismoNo() {
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned(BARCODE);
        assertEquals(1, repository.loads);

        viewModel.onBarcodeScanned(OTHER_BARCODE);
        assertEquals(2, repository.loads);
    }

    @Test
    public void lecturaDuranteLaCarga_cancelaLaDelBultoAnterior() {
        repository.willReturn(List.of(A16));
        repository.deferResponses();
        BultoIncidenceViewModel viewModel = new BultoIncidenceViewModel(new SavedStateHandle(), repository);
        viewModel.onBarcodeScanned(BARCODE);

        viewModel.onBarcodeScanned(OTHER_BARCODE);

        assertEquals(1, repository.cancellations);
        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.Loading);
    }

    @Test
    public void conTipoDeOperacionEnLosArgumentos_pideLasIncidenciasDeEseTipo() {
        repository.willReturn(List.of(A16));

        newViewModel();

        assertEquals("REC", repository.lastCodigoAplicacion);
    }

    @Test
    public void incidenciasRecibidas_listasSinNingunaElegida() {
        repository.willReturn(List.of(A16, A21));

        BultoIncidenceViewModel viewModel = newViewModel();

        BultoIncidenceState state = viewModel.getState().getValue();
        assertTrue(state instanceof BultoIncidenceState.Ready);
        assertEquals(2, ((BultoIncidenceState.Ready) state).incidences().size());
        assertFalse(viewModel.canConfirm());
        assertFalse(viewModel.canTakePhoto());
    }

    @Test
    public void sinIncidencias_vacio_yReintentarVuelveACargar() {
        repository.willReturn(List.of());
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = newViewModel();
        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.Empty);

        viewModel.onRetryClicked();

        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.Ready);
    }

    @Test
    public void falloAlCargar_explicaElError_yNoSePuedeElegir() {
        repository.willFail();

        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A16);

        assertTrue(viewModel.getState().getValue() instanceof BultoIncidenceState.Failed);
        assertNull(viewModel.getSelectedIncidence().getValue());
    }

    @Test
    public void incidenciaSinExigencias_seGrabaYLaPantallaQuedaLimpia() {
        repository.willReturn(List.of(A16, A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A16);

        BultoIncidenceEntry entry = viewModel.onConfirmClicked("texto que no se graba");

        assertNotNull(entry);
        assertEquals(BARCODE, entry.barcode());
        assertEquals(A16, entry.incidence());
        assertEquals("", entry.observations());
        assertNull(viewModel.getSelectedIncidence().getValue());
    }

    @Test
    public void incidenciaQueExigeFoto_sinFoto_noSeGraba() {
        repository.willReturn(List.of(A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);

        assertNull(viewModel.onConfirmClicked(""));

        assertEquals(Integer.valueOf(R.string.bulto_incidence_error_photo),
                viewModel.getFormState().getValue().photoError());
        assertEquals(A21, viewModel.getSelectedIncidence().getValue());
    }

    @Test
    public void incidenciaQueExigeFoto_conFoto_seGrabaConLaFoto() {
        repository.willReturn(List.of(A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);
        viewModel.onConfirmClicked("");

        viewModel.onPhotoAttached(PHOTO_URI);
        assertTrue(viewModel.getFormState().getValue().isValid());
        BultoIncidenceEntry entry = viewModel.onConfirmClicked("");

        assertNotNull(entry);
        assertEquals(PHOTO_URI, entry.photoUri());
        assertNull(viewModel.getPhotoUri().getValue());
    }

    @Test
    public void incidenciaQueExigeObservaciones_enBlanco_noSeGraba() {
        repository.willReturn(List.of(I11));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(I11);

        assertNull(viewModel.onConfirmClicked("   "));
        assertEquals(Integer.valueOf(R.string.bulto_incidence_error_observations),
                viewModel.getFormState().getValue().observationsError());

        viewModel.onObservationsChanged();
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void incidenciaQueExigeObservaciones_seGrabanSinEspaciosSobrantes() {
        repository.willReturn(List.of(I11));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(I11);

        BultoIncidenceEntry entry = viewModel.onConfirmClicked("  caja abierta ");

        assertNotNull(entry);
        assertEquals("caja abierta", entry.observations());
    }

    @Test
    public void observacionesDeMasDe40Caracteres_noSeGraban() {
        repository.willReturn(List.of(I11));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(I11);

        assertNull(viewModel.onConfirmClicked("x".repeat(BultoIncidencia.OBSERVATIONS_MAX_LENGTH + 1)));
        assertEquals(Integer.valueOf(R.string.bulto_incidence_error_observations_length),
                viewModel.getFormState().getValue().observationsError());
    }

    @Test
    public void observacionesEscritas_noSeGrabanSiNoSeExigen() {
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A16);

        BultoIncidenceEntry entry = viewModel.onConfirmClicked("x".repeat(BultoIncidencia.OBSERVATIONS_MAX_LENGTH + 1));

        assertNotNull(entry);
        assertEquals("", entry.observations());
    }

    @Test
    public void cambiarDeIncidencia_olvidaLosErroresDeLaAnterior() {
        repository.willReturn(List.of(A16, A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);
        viewModel.onConfirmClicked("");

        viewModel.onIncidenceSelected(A16);

        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void cancelar_descartaIncidenciaYFoto() {
        repository.willReturn(List.of(A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);
        viewModel.onPhotoAttached(PHOTO_URI);

        viewModel.onCancelClicked();

        assertNull(viewModel.getSelectedIncidence().getValue());
        assertNull(viewModel.getPhotoUri().getValue());
        assertFalse(viewModel.canConfirm());
    }

    @Test
    public void trasRecrearseElProceso_recuperaLaIncidenciaYLaFoto() {
        repository.willReturn(List.of(A16, A21));
        repository.willReturn(List.of(A16, A21));
        SavedStateHandle savedState = savedStateWithArgs();
        BultoIncidenceViewModel before = new BultoIncidenceViewModel(savedState, repository);
        before.onIncidenceSelected(A21);
        before.onPhotoAttached(PHOTO_URI);

        BultoIncidenceViewModel after = new BultoIncidenceViewModel(copyOf(savedState), repository);

        assertEquals(A21, after.getSelectedIncidence().getValue());
        assertEquals(PHOTO_URI, after.getPhotoUri().getValue());
    }

    @Test
    public void bultoLeido_sustituyeAlDeLosArgumentos_yLaIncidenciaSeGrabaConEl() {
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = newViewModel();

        viewModel.onBarcodeScanned("  " + OTHER_BARCODE + " ");
        viewModel.onIncidenceSelected(A16);
        BultoIncidenceEntry entry = viewModel.onConfirmClicked("");

        assertEquals(OTHER_BARCODE, viewModel.getBarcode().getValue());
        assertNotNull(entry);
        assertEquals(OTHER_BARCODE, entry.barcode());
    }

    @Test
    public void desdeElMenu_sinBulto_hastaQueSeLeeUno() {
        repository.willReturn(List.of(A16));
        BultoIncidenceViewModel viewModel = new BultoIncidenceViewModel(new SavedStateHandle(), repository);
        assertNull(viewModel.getBarcode().getValue());

        viewModel.onBarcodeScanned(BARCODE);

        assertEquals(BARCODE, viewModel.getBarcode().getValue());
    }

    @Test
    public void leerOtroBulto_descartaLaIncidenciaYLaFotoDelAnterior() {
        repository.willReturn(List.of(A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);
        viewModel.onPhotoAttached(PHOTO_URI);

        viewModel.onBarcodeScanned(OTHER_BARCODE);

        assertNull(viewModel.getSelectedIncidence().getValue());
        assertNull(viewModel.getPhotoUri().getValue());
    }

    @Test
    public void releerElMismoBulto_oUnaLecturaEnBlanco_conservanLoElegido() {
        repository.willReturn(List.of(A21));
        BultoIncidenceViewModel viewModel = newViewModel();
        viewModel.onIncidenceSelected(A21);
        viewModel.onPhotoAttached(PHOTO_URI);

        viewModel.onBarcodeScanned(BARCODE);
        viewModel.onBarcodeScanned("   ");

        assertEquals(BARCODE, viewModel.getBarcode().getValue());
        assertEquals(A21, viewModel.getSelectedIncidence().getValue());
        assertEquals(PHOTO_URI, viewModel.getPhotoUri().getValue());
    }

    @Test
    public void trasRecrearseElProceso_recuperaElBultoLeido() {
        repository.willReturn(List.of(A16));
        repository.willReturn(List.of(A16));
        SavedStateHandle savedState = savedStateWithArgs();
        new BultoIncidenceViewModel(savedState, repository).onBarcodeScanned(OTHER_BARCODE);

        BultoIncidenceViewModel after = new BultoIncidenceViewModel(copyOf(savedState), repository);

        assertEquals(OTHER_BARCODE, after.getBarcode().getValue());
    }

    @NonNull
    private BultoIncidenceViewModel newViewModel() {
        return new BultoIncidenceViewModel(savedStateWithArgs(), repository);
    }

    @NonNull
    private static SavedStateHandle savedStateWithArgs() {
        SavedStateHandle handle = new SavedStateHandle();
        handle.set("bultoIncidence.operationType", OperationType.RECOGIDAS.name());
        handle.set("bultoIncidence.barcode", BARCODE);
        return handle;
    }

    @NonNull
    private static SavedStateHandle copyOf(@NonNull SavedStateHandle handle) {
        SavedStateHandle copy = new SavedStateHandle();
        for (String key : handle.keys()) {
            copy.set(key, handle.get(key));
        }
        return copy;
    }

    /** Responde en orden lo programado (una lista de incidencias o un fallo) y cuenta las cargas. */
    private static final class ScriptedRepository extends InicdenciaRepository {

        private final Deque<List<BultoIncidencia>> responses = new ArrayDeque<>();
        private boolean fail;
        private boolean deferred;
        String lastCodigoAplicacion;
        int loads;
        int cancellations;

        ScriptedRepository() {
            super(null, null, null);
        }

        void willReturn(@NonNull List<BultoIncidencia> incidencias) {
            responses.add(incidencias);
        }

        void willFail() {
            fail = true;
        }

        /** Las cargas quedan pendientes: sirve para leer otro bulto con una carga en curso. */
        void deferResponses() {
            deferred = true;
        }

        @NonNull
        @Override
        public Cancellable getIncidenciasPorCodigo(@NonNull String codigoAplicacion,
                                                   @NonNull ResultCallback<List<BultoIncidencia>> callback) {
            lastCodigoAplicacion = codigoAplicacion;
            loads++;
            if (deferred) {
                return () -> cancellations++;
            }
            if (fail) {
                callback.onError(new AppError.Api(null, null));
            } else {
                // La última respuesta programada se repite: cada bulto leído vuelve a cargar
                callback.onSuccess(responses.size() > 1 ? responses.removeFirst() : responses.getFirst());
            }
            return () -> { };
        }
    }
}
