package com.redur.electra.ui.carga.camion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.core.async.Cancellable;
import com.redur.electra.core.async.ResultCallback;
import com.redur.electra.core.error.AppError;
import com.redur.electra.data.model.carga.Consignee;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.data.model.carga.PackageKind;
import com.redur.electra.data.repository.TruckLoadRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TruckLoadViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    /** Orden de carga 2, zona 23B, vuelta 1, completa. */
    private static final Expedition COMPLETE = expedition("963456111", "23B", 2, 1, 3, 3);
    /** Orden de carga 1, zona 23B, vuelta 2, faltan 3. */
    private static final Expedition MISSING = expedition("977991202", "23B", 1, 2, 3, 0);
    /** Orden de carga 3, zona 23A, vuelta 1, falta 1. */
    private static final Expedition ZONE_A = expedition("971200548", "23A", 3, 1, 2, 1);

    private final ScriptedRepository repository = new ScriptedRepository();

    @Test
    public void cargaRecibida_ordenadaPorOrdenDeCarga_conLasCompletasContadas() {
        repository.willReturn(List.of(COMPLETE, ZONE_A, MISSING));

        TruckLoadState.Ready ready = ready(newViewModel());

        assertEquals(List.of(MISSING, COMPLETE, ZONE_A), ready.expeditions());
        assertEquals(TruckLoadSort.LOAD_ORDER, ready.sort());
        assertEquals(List.of(1, 2), ready.trips());
        assertEquals(1, ready.completeCount());
        assertEquals(3, ready.totalCount());
        assertFalse(ready.canFinish());
        assertFalse(ready.hasSelection());
    }

    @Test
    public void cargaSinExpediciones_vacia_yRefrescarVuelveAPedirla() {
        repository.willReturn(List.of());
        repository.willReturn(List.of(COMPLETE));
        TruckLoadViewModel viewModel = newViewModel();
        assertTrue(viewModel.getState().getValue() instanceof TruckLoadState.Empty);

        viewModel.onRefreshClicked();

        assertEquals(List.of(COMPLETE), ready(viewModel).expeditions());
    }

    @Test
    public void falloAlPedirLaCarga_reintentarVuelveAPedirla() {
        repository.willFail();
        repository.willReturn(List.of(COMPLETE));
        TruckLoadViewModel viewModel = newViewModel();
        assertTrue(viewModel.getState().getValue() instanceof TruckLoadState.Failed);

        viewModel.onRetryClicked();

        assertEquals(List.of(COMPLETE), ready(viewModel).expeditions());
    }

    @Test
    public void sinDiferenciasEnNingunaExpedicion_sePuedeFinalizar() {
        repository.willReturn(List.of(COMPLETE));

        assertTrue(ready(newViewModel()).canFinish());
    }

    @Test
    public void ordenarPorZona_yDentroDeLaZonaPorOrdenDeCarga() {
        repository.willReturn(List.of(COMPLETE, ZONE_A, MISSING));
        TruckLoadViewModel viewModel = newViewModel();

        viewModel.onSortSelected(TruckLoadSort.ZONE);

        assertEquals(List.of(ZONE_A, MISSING, COMPLETE), ready(viewModel).expeditions());
    }

    @Test
    public void filtrarPorVuelta_soloSeVenLasDeEsaVuelta_peroElProgresoEsDeTodaLaCarga() {
        repository.willReturn(List.of(COMPLETE, ZONE_A, MISSING));
        TruckLoadViewModel viewModel = newViewModel();

        viewModel.onTripFilterSelected(2);

        TruckLoadState.Ready ready = ready(viewModel);
        assertEquals(List.of(MISSING), ready.expeditions());
        assertEquals(Integer.valueOf(2), ready.tripFilter());
        assertEquals(3, ready.totalCount());

        viewModel.onTripFilterSelected(null);

        assertEquals(3, ready(viewModel).expeditions().size());
    }

    @Test
    public void elegirExpedicion_yVolverATocarlaLaDejaSinElegir() {
        repository.willReturn(List.of(COMPLETE, MISSING));
        TruckLoadViewModel viewModel = newViewModel();

        viewModel.onExpeditionClicked(MISSING.number());
        assertEquals(MISSING.number(), ready(viewModel).selected());

        viewModel.onExpeditionClicked(MISSING.number());
        assertNull(ready(viewModel).selected());
    }

    @Test
    public void laElegidaQuedaFueraDelFiltro_seDescarta() {
        repository.willReturn(List.of(COMPLETE, MISSING));
        TruckLoadViewModel viewModel = newViewModel();
        viewModel.onExpeditionClicked(COMPLETE.number());

        viewModel.onTripFilterSelected(2);

        assertFalse(ready(viewModel).hasSelection());
    }

    @Test
    public void refrescar_mantieneLaCargaALaVista_eIgnoraOtroRefrescoEnCurso() {
        repository.willReturn(List.of(COMPLETE, MISSING));
        TruckLoadViewModel viewModel = newViewModel();
        repository.holdNext();

        viewModel.onRefreshClicked();
        viewModel.onRefreshClicked();

        TruckLoadState.Ready refreshing = ready(viewModel);
        assertTrue(refreshing.refreshing());
        assertEquals(2, refreshing.expeditions().size());
        assertFalse(refreshing.canFinish());
        assertEquals(2, repository.requests);

        repository.release(List.of(COMPLETE));

        TruckLoadState.Ready refreshed = ready(viewModel);
        assertFalse(refreshed.refreshing());
        assertEquals(List.of(COMPLETE), refreshed.expeditions());
    }

    @Test
    public void refrescoFallido_conservaLaCarga_yElAvisoSeDaUnaVez() {
        repository.willReturn(List.of(COMPLETE));
        TruckLoadViewModel viewModel = newViewModel();
        repository.willFail();

        viewModel.onRefreshClicked();

        TruckLoadState.Ready ready = ready(viewModel);
        assertEquals(List.of(COMPLETE), ready.expeditions());
        assertNotNull(ready.refreshError());

        viewModel.onRefreshErrorShown();

        assertNull(ready(viewModel).refreshError());
    }

    @Test
    public void filtroYElegidaQueYaNoEstanTrasRefrescar_seDescartan() {
        repository.willReturn(List.of(COMPLETE, MISSING));
        repository.willReturn(List.of(COMPLETE));
        TruckLoadViewModel viewModel = newViewModel();
        viewModel.onTripFilterSelected(2);
        viewModel.onExpeditionClicked(MISSING.number());

        viewModel.onRefreshClicked();

        TruckLoadState.Ready ready = ready(viewModel);
        assertNull(ready.tripFilter());
        assertNull(ready.selected());
        assertEquals(List.of(COMPLETE), ready.expeditions());
    }

    @Test
    public void seRecuperanOrdenFiltroYElegidaGuardados() {
        repository.willReturn(List.of(COMPLETE, ZONE_A, MISSING));
        SavedStateHandle savedState = new SavedStateHandle(Map.of(
                TruckLoadViewModel.KEY_SORT, TruckLoadSort.EXPEDITION_NUMBER.name(),
                TruckLoadViewModel.KEY_TRIP_FILTER, 1,
                TruckLoadViewModel.KEY_SELECTED, ZONE_A.number()));

        TruckLoadState.Ready ready = ready(new TruckLoadViewModel(savedState, repository));

        assertEquals(TruckLoadSort.EXPEDITION_NUMBER, ready.sort());
        assertEquals(List.of(COMPLETE, ZONE_A), ready.expeditions());
        assertEquals(ZONE_A.number(), ready.selected());
    }

    @Test
    public void ordenarPorAlbaranCpPoblacionRemitenteYConsignatario() {
        Expedition first = expedition("1", "A-300", "Ávila Trans",
                new Consignee("zoe Martín", "C/ Uno 1", "08002", "Barcelona"), "23B", 3, 1, 1, 1, 1.0);
        Expedition second = expedition("2", "A-100", "zeta Envíos",
                new Consignee("Álvaro Ruiz", "C/ Dos 2", "28001", "Ávila"), "23B", 1, 1, 1, 1, 1.0);
        Expedition third = expedition("3", "A-200", "abc Logística",
                new Consignee("María Pons", "C/ Tres 3", "08001", "Zaragoza"), "23B", 2, 1, 1, 1, 1.0);
        repository.willReturn(List.of(first, second, third));
        TruckLoadViewModel viewModel = newViewModel();

        viewModel.onSortSelected(TruckLoadSort.DELIVERY_NOTE);
        assertEquals(List.of(second, third, first), ready(viewModel).expeditions());

        viewModel.onSortSelected(TruckLoadSort.POSTAL_CODE);
        assertEquals(List.of(third, first, second), ready(viewModel).expeditions());

        // Sin distinguir mayúsculas ni acentos
        viewModel.onSortSelected(TruckLoadSort.TOWN);
        assertEquals(List.of(second, first, third), ready(viewModel).expeditions());

        viewModel.onSortSelected(TruckLoadSort.SENDER);
        assertEquals(List.of(third, first, second), ready(viewModel).expeditions());

        viewModel.onSortSelected(TruckLoadSort.CONSIGNEE);
        assertEquals(List.of(second, third, first), ready(viewModel).expeditions());

        viewModel.onSortSelected(TruckLoadSort.LOAD_ORDER);
        assertEquals(List.of(second, third, first), ready(viewModel).expeditions());
    }

    @Test
    public void resumen_deTodaLaCargaAunqueEsteFiltrada_yCadaDireccionEsUnaParada() {
        Consignee shared = new Consignee("Carmen Meca", "CL Joan Buscallà 1-9", "08190", "S.Cugat");
        Consignee sameStop = new Consignee("Otra persona", "cl joan  buscallà 1-9", "08190", "S.Cugat");
        repository.willReturn(List.of(
                expedition("1", "A1", "Remitente", shared, "23B", 1, 1, 3, 0, 12.5),
                expedition("2", "A2", "Remitente", sameStop, "23B", 2, 2, 2, 0, 7.5),
                expedition("3", "A3", "Remitente",
                        new Consignee("Juan López", "Calle Centolla", "08001", "Barcelona"), "23B", 3, 1, 4, 0, 30.0)));
        TruckLoadViewModel viewModel = newViewModel();

        viewModel.onTripFilterSelected(2);

        assertEquals(new TruckLoadSummary(3, 2, 9, 50.0), ready(viewModel).summary());
    }

    @NonNull
    private TruckLoadViewModel newViewModel() {
        return new TruckLoadViewModel(new SavedStateHandle(), repository);
    }

    @NonNull
    private static TruckLoadState.Ready ready(@NonNull TruckLoadViewModel viewModel) {
        TruckLoadState state = viewModel.getState().getValue();
        assertTrue("Se esperaba la carga lista y es " + state, state instanceof TruckLoadState.Ready);
        return (TruckLoadState.Ready) state;
    }

    @NonNull
    private static Expedition expedition(@NonNull String number, @NonNull String zone, int loadOrder, int trip,
                                         int documented, int scanned) {
        return expedition(number, "ALB" + number, "Remitente",
                new Consignee("Nombre", "Dirección " + number, "08001", "Barcelona"),
                zone, loadOrder, trip, documented, scanned, 1.0);
    }

    @NonNull
    private static Expedition expedition(@NonNull String number, @NonNull String deliveryNote, @NonNull String sender,
                                         @NonNull Consignee consignee, @NonNull String zone, int loadOrder, int trip,
                                         int documented, int scanned, double weight) {
        return new Expedition(number, deliveryNote, sender, consignee, documented, documented, scanned, zone,
                loadOrder, 3, trip, PackageKind.PACKAGE, Set.of(), Set.of(), 0, false, null, weight);
    }

    private static final class ScriptedRepository extends TruckLoadRepository {

        private final List<List<Expedition>> responses = new ArrayList<>();
        private boolean fail;
        private boolean hold;
        private ResultCallback<List<Expedition>> held;
        int requests;

        void willReturn(@NonNull List<Expedition> expeditions) {
            responses.add(expeditions);
        }

        void willFail() {
            fail = true;
        }

        /** La siguiente petición queda en curso hasta {@link #release}. */
        void holdNext() {
            hold = true;
        }

        void release(@NonNull List<Expedition> expeditions) {
            ResultCallback<List<Expedition>> callback = held;
            held = null;
            callback.onSuccess(expeditions);
        }

        @NonNull
        @Override
        public Cancellable getExpeditions(@NonNull ResultCallback<List<Expedition>> callback) {
            requests++;
            if (hold) {
                hold = false;
                held = callback;
            } else if (fail) {
                fail = false;
                callback.onError(new AppError.Api(null, null));
            } else {
                callback.onSuccess(responses.remove(0));
            }
            return () -> { };
        }
    }
}
