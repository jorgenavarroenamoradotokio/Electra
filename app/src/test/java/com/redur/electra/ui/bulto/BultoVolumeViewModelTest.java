package com.redur.electra.ui.bulto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.R;
import com.redur.electra.ui.bulto.volumen.BultoVolumeReading;
import com.redur.electra.ui.bulto.volumen.BultoVolumeViewModel;

import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;

public class BultoVolumeViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final String BARCODE = "14600400072459116400016247001";

    private final BultoVolumeViewModel viewModel = new BultoVolumeViewModel(new SavedStateHandle());

    // --- Validación ---

    @Test
    public void lecturaCompleta_devuelveCbMedidasYVolumen() {
        BultoVolumeReading reading = viewModel.onConfirmClicked(" " + BARCODE + " ", "040", "030", "035");

        assertNotNull(reading);
        assertEquals(BARCODE, reading.barcode());
        assertEquals(40, reading.heightCm());
        assertEquals(30, reading.widthCm());
        assertEquals(35, reading.depthCm());
        // 40 × 30 × 35 = 42.000 cm³ = 0,042 m³
        assertEquals(new BigDecimal("0.04"), reading.volumeM3());
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void sinCb_muestraErrorDeCb() {
        assertNull(viewModel.onConfirmClicked("  ", "040", "030", "035"));

        assertEquals(Integer.valueOf(R.string.bulto_volume_error_barcode),
                viewModel.getFormState().getValue().barcodeError());
        assertNull(viewModel.getFormState().getValue().volumeError());
    }

    @Test
    public void faltaUnaMedida_muestraErrorDeMedidas() {
        assertNull(viewModel.onConfirmClicked(BARCODE, "040", "", "035"));

        assertEquals(Integer.valueOf(R.string.bulto_volume_error_measures),
                viewModel.getFormState().getValue().volumeError());
    }

    @Test
    public void volumenCeroOFueraDeRango_muestraErrorDeVolumen() {
        // 0 × 30 × 35 = 0 m³ y 999 × 999 × 999 ≈ 997 m³
        String[][] measures = {{"0", "030", "035"}, {"999", "999", "999"}};
        for (String[] m : measures) {
            BultoVolumeViewModel fresh = new BultoVolumeViewModel(new SavedStateHandle());

            assertNull(m[0], fresh.onConfirmClicked(BARCODE, m[0], m[1], m[2]));
            assertEquals(m[0], Integer.valueOf(R.string.bulto_volume_error_volume),
                    fresh.getFormState().getValue().volumeError());
        }
    }

    // --- Volumen calculado y corrección de errores ---

    @Test
    public void volumenSeCalculaSoloConLasTresMedidas() {
        viewModel.onMeasuresChanged("040", "030", "");
        assertNull(viewModel.getVolume().getValue());

        viewModel.onMeasuresChanged("040", "030", "035");
        assertEquals(new BigDecimal("0.04"), viewModel.getVolume().getValue());
    }

    @Test
    public void corregirCampos_retiraSusErrores() {
        viewModel.onConfirmClicked("", "", "", "");

        viewModel.onBarcodeChanged();
        assertNull(viewModel.getFormState().getValue().barcodeError());
        assertNotNull(viewModel.getFormState().getValue().volumeError());

        viewModel.onMeasuresChanged("040", "", "");
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void medidasRestauradas_recalculanVolumenSinRetirarErrores() {
        viewModel.onConfirmClicked("", "", "", "");

        viewModel.onMeasuresRestored("040", "030", "035");

        assertEquals(new BigDecimal("0.04"), viewModel.getVolume().getValue());
        assertNotNull(viewModel.getFormState().getValue().volumeError());
    }

    // --- Bucle y modo ---

    @Test
    public void cancelar_dejaLaPantallaLimpiaParaElSiguienteBulto() {
        viewModel.onMeasuresChanged("040", "030", "035");
        viewModel.onConfirmClicked("", "040", "030", "035");

        viewModel.onCancelClicked();

        assertNull(viewModel.getVolume().getValue());
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void modoVolumen_seAbreActivoYSinElNoSeGraba() {
        assertTrue(viewModel.isVolumeMode());

        viewModel.onVolumeModeChanged(false);

        assertFalse(viewModel.isVolumeMode());
        assertNull(viewModel.onConfirmClicked(BARCODE, "040", "030", "035"));
    }

    @Test
    public void modoVolumen_seRestauraDelEstadoGuardado() {
        SavedStateHandle savedState = new SavedStateHandle();
        new BultoVolumeViewModel(savedState).onVolumeModeChanged(false);

        assertFalse(new BultoVolumeViewModel(savedState).isVolumeMode());
    }
}
