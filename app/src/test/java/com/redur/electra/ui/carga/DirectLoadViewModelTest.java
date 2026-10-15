package com.redur.electra.ui.carga;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.rule.TimberTestRule;
import com.redur.electra.ui.carga.directa.DirectLoadReading;
import com.redur.electra.ui.carga.directa.DirectLoadState;
import com.redur.electra.ui.carga.directa.DirectLoadViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class DirectLoadViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private static final String SEAL = "12345678";
    private static final String BULTO_1 = "13020000062081000447204320001";
    private static final String BULTO_2 = "13020000062081000447204320002";
    private static final BultoType C_PLUS = new BultoType("050", "C+", "CONTENEDOR +", false);

    private DirectLoadViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new DirectLoadViewModel(new SavedStateHandle());
    }

    @Test
    public void alEntrar_pideElPrecinto() {
        DirectLoadState.SealEntry entry = (DirectLoadState.SealEntry) viewModel.getState().getValue();

        assertNull(entry.sealError());
    }

    @Test
    public void precintoVacio_pideLeerloOEscribirlo() {
        viewModel.onSealEntered("  ");

        assertSealError(R.string.direct_load_error_seal_required);
    }

    @Test
    public void precintoQueNoSonOchoDigitos_seRechaza() {
        for (String seal : List.of("1234567", "123456789", "1234567A")) {
            viewModel.onSealEntered(seal);

            assertSealError(R.string.direct_load_error_seal_format);
        }
    }

    @Test
    public void corregirElPrecinto_retiraElError() {
        viewModel.onSealEntered("123");

        viewModel.onSealEdited();

        assertNull(((DirectLoadState.SealEntry) viewModel.getState().getValue()).sealError());
    }

    @Test
    public void precintoValido_empiezaLaLecturaSinTipoNiBultos() {
        viewModel.onSealEntered(" " + SEAL + " ");

        DirectLoadState.Reading reading = reading();
        assertEquals(SEAL, reading.seal());
        assertNull(reading.fixedType());
        assertTrue(reading.readings().isEmpty());
        assertNull(reading.feedback());
    }

    @Test
    public void bultoAntesDelPrecinto_seIgnora() {
        viewModel.onBarcodeScanned(BULTO_1);

        assertTrue(viewModel.getState().getValue() instanceof DirectLoadState.SealEntry);
    }

    @Test
    public void bultosLeidos_elUltimoPrimeroConElTipoFijadoEnSuMomento() {
        viewModel.onSealEntered(SEAL);

        viewModel.onBarcodeScanned(BULTO_1);
        viewModel.onBultoTypeFixed(C_PLUS);
        viewModel.onBarcodeScanned(BULTO_2);

        assertEquals(List.of(new DirectLoadReading(BULTO_2, C_PLUS), new DirectLoadReading(BULTO_1, null)),
                reading().readings());
        assertEquals(new DirectLoadState.Feedback.Added(new DirectLoadReading(BULTO_2, C_PLUS)),
                reading().feedback());
    }

    @Test
    public void quitarElTipo_noCambiaLosYaLeidos() {
        viewModel.onSealEntered(SEAL);
        viewModel.onBultoTypeFixed(C_PLUS);
        viewModel.onBarcodeScanned(BULTO_1);

        viewModel.onBultoTypeCleared();
        viewModel.onBarcodeScanned(BULTO_2);

        assertNull(reading().fixedType());
        assertEquals(List.of(new DirectLoadReading(BULTO_2, null), new DirectLoadReading(BULTO_1, C_PLUS)),
                reading().readings());
    }

    @Test
    public void bultoRepetido_noSeAnadeYSeAvisa() {
        viewModel.onSealEntered(SEAL);
        viewModel.onBarcodeScanned(BULTO_1);

        viewModel.onBarcodeScanned(BULTO_1);

        assertEquals(1, reading().readings().size());
        assertEquals(new DirectLoadState.Feedback.Duplicate(BULTO_1), reading().feedback());
    }

    @Test
    public void lecturaVacia_seIgnora() {
        viewModel.onSealEntered(SEAL);

        viewModel.onBarcodeScanned(" ");

        assertTrue(reading().readings().isEmpty());
        assertNull(reading().feedback());
    }

    @Test
    public void codigoDelTipoFijado_paraMarcarloEnElSelector() {
        viewModel.onSealEntered(SEAL);
        assertNull(viewModel.getFixedTypeCode());

        viewModel.onBultoTypeFixed(C_PLUS);

        assertEquals("050", viewModel.getFixedTypeCode());
    }

    @Test
    public void trasMatarElProceso_vuelveALasLecturasDelPrecinto() {
        DirectLoadViewModel restored = new DirectLoadViewModel(new SavedStateHandle(Map.of("directLoad.seal", SEAL)));

        DirectLoadState state = restored.getState().getValue();
        assertTrue(state instanceof DirectLoadState.Reading);
        assertEquals(SEAL, ((DirectLoadState.Reading) state).seal());
    }

    private void assertSealError(int expected) {
        DirectLoadState.SealEntry entry = (DirectLoadState.SealEntry) viewModel.getState().getValue();
        assertEquals(Integer.valueOf(expected), entry.sealError());
    }

    private DirectLoadState.Reading reading() {
        return (DirectLoadState.Reading) viewModel.getState().getValue();
    }
}
