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
import com.redur.electra.data.model.bulto.BultoIncidence;
import com.redur.electra.data.model.bulto.OperationType;
import com.redur.electra.data.repository.BultoIncidenceRepository;
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
    private static final String PHOTO_URI = "content://com.redur.electra.fileprovider/photos/IMG_1.jpg";
    private static final BultoIncidence A16 = new BultoIncidence("A16", false, false);
    private static final BultoIncidence A21 = new BultoIncidence("A21", true, false);
    private static final BultoIncidence I11 = new BultoIncidence("I11", false, true);

    private final ScriptedRepository repository = new ScriptedRepository();

    @Test
    public void desdeElMenu_recogidasSinBultoLeido() {
        repository.willReturn(List.of(A16));

        BultoIncidenceViewModel viewModel = new BultoIncidenceViewModel(new SavedStateHandle(), repository);

        assertEquals(OperationType.RECOGIDAS, viewModel.getArgs().operationType());
        assertNull(viewModel.getArgs().barcode());
        assertEquals(OperationType.RECOGIDAS, repository.lastOperationType);
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

        assertNull(viewModel.onConfirmClicked("x".repeat(BultoIncidence.OBSERVATIONS_MAX_LENGTH + 1)));
        assertEquals(Integer.valueOf(R.string.bulto_incidence_error_observations_length),
                viewModel.getFormState().getValue().observationsError());
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

    /** Responde en orden lo programado: una lista de incidencias o un fallo de red. */
    private static final class ScriptedRepository extends BultoIncidenceRepository {

        private final Deque<List<BultoIncidence>> responses = new ArrayDeque<>();
        private boolean fail;
        OperationType lastOperationType;

        void willReturn(@NonNull List<BultoIncidence> incidences) {
            responses.add(incidences);
        }

        void willFail() {
            fail = true;
        }

        @NonNull
        @Override
        public Cancellable getIncidences(@NonNull OperationType operationType,
                                         @NonNull ResultCallback<List<BultoIncidence>> callback) {
            lastOperationType = operationType;
            if (fail) {
                callback.onError(new AppError.Api(null, null));
            } else {
                callback.onSuccess(responses.removeFirst());
            }
            return () -> { };
        }
    }
}
