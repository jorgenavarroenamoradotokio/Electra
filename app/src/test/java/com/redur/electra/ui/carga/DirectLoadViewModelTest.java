package com.redur.electra.ui.carga;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

/** Con Robolectric: las lecturas se guardan como {@link android.os.Bundle} para sobrevivir a la muerte del proceso. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
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
    public void ultimoCodigoLeido_seMuestraAunqueSeaRepetido() {
        viewModel.onSealEntered(SEAL);
        assertNull(reading().lastBarcode());

        viewModel.onBarcodeScanned(BULTO_1);
        viewModel.onBarcodeScanned(" " + BULTO_2 + " ");
        assertEquals(BULTO_2, reading().lastBarcode());

        viewModel.onBarcodeScanned(BULTO_1);
        assertEquals(BULTO_1, reading().lastBarcode());
    }

    @Test
    public void tipoElegidoParaUnBultoLeido_loCorrigeSinTocarElFijadoNiElOrden() {
        viewModel.onSealEntered(SEAL);
        viewModel.onBarcodeScanned(BULTO_1);
        viewModel.onBarcodeScanned(BULTO_2);

        viewModel.onReadingTypePickerRequested(BULTO_1);
        viewModel.onBultoTypePicked(C_PLUS);

        assertNull(reading().fixedType());
        assertEquals(List.of(new DirectLoadReading(BULTO_2, null), new DirectLoadReading(BULTO_1, C_PLUS)),
                reading().readings());
        assertEquals(new DirectLoadState.Feedback.TypeChanged(BULTO_1, C_PLUS), reading().feedback());
        assertEquals(BULTO_2, reading().lastBarcode());
    }

    @Test
    public void tipoElegidoDesdeLaFichaTrasCorregirUnBulto_fijaElTipo() {
        viewModel.onSealEntered(SEAL);
        viewModel.onBarcodeScanned(BULTO_1);
        // Se abrió el selector desde la fila pero se volvió sin elegir
        viewModel.onReadingTypePickerRequested(BULTO_1);

        viewModel.onFixedTypePickerRequested();
        viewModel.onBultoTypePicked(C_PLUS);

        assertEquals(C_PLUS, reading().fixedType());
        assertEquals(List.of(new DirectLoadReading(BULTO_1, null)), reading().readings());
    }

    @Test
    public void corregirElTipo_seConservaTrasMatarElProceso() {
        SavedStateHandle handle = new SavedStateHandle();
        DirectLoadViewModel first = new DirectLoadViewModel(handle);
        first.onSealEntered(SEAL);
        first.onBarcodeScanned(BULTO_1);
        first.onReadingTypePickerRequested(BULTO_1);
        first.onBultoTypePicked(C_PLUS);

        DirectLoadState.Reading restored =
                (DirectLoadState.Reading) new DirectLoadViewModel(handle).getState().getValue();

        assertEquals(List.of(new DirectLoadReading(BULTO_1, C_PLUS)), restored.readings());
        assertEquals(BULTO_1, restored.lastBarcode());
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
