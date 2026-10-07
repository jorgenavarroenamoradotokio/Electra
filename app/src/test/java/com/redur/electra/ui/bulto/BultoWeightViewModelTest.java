package com.redur.electra.ui.bulto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoMeasures;

import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class BultoWeightViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final BultoMeasures CURRENT = new BultoMeasures(
            new BigDecimal("12.0"), 40, 30, 35, new BigDecimal("0.04"));

    // --- Validación ---

    @Test
    public void soloPeso_seGrabaSinMedidas() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

        BultoWeightResult.Saved saved = viewModel.onConfirmClicked("12", "", "", "");

        assertNotNull(saved);
        assertEquals(new BigDecimal("12.0"), saved.measures().weightKg());
        assertFalse(saved.measures().hasMeasures());
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void pesoConMedidas_grabaVolumenCalculadoConDosDecimales() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

        BultoWeightResult.Saved saved = viewModel.onConfirmClicked("12,04", "040", "030", "035");

        assertNotNull(saved);
        assertEquals(new BigDecimal("12.0"), saved.measures().weightKg());
        assertEquals(Integer.valueOf(40), saved.measures().heightCm());
        assertEquals(Integer.valueOf(30), saved.measures().widthCm());
        assertEquals(Integer.valueOf(35), saved.measures().depthCm());
        // 40 × 30 × 35 = 42.000 cm³ = 0,042 m³
        assertEquals(new BigDecimal("0.04"), saved.measures().volumeM3());
    }

    @Test
    public void pesoVacioCeroOFueraDeRango_muestraErrorDePeso() {
        for (String weight : new String[]{"", "0", "0,04", "100000", "99999,96", "1,2,3"}) {
            BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

            assertNull(weight, viewModel.onConfirmClicked(weight, "", "", ""));
            assertEquals(weight, Integer.valueOf(R.string.bulto_weight_error_weight),
                    viewModel.getFormState().getValue().weightError());
        }
    }

    @Test
    public void pesoJustoBajoElMaximo_esValido() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

        assertNotNull(viewModel.onConfirmClicked("99999.9", "", "", ""));
    }

    @Test
    public void medidasIncompletas_volumenNoCorrecto() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

        assertNull(viewModel.onConfirmClicked("12", "040", "", "035"));
        assertEquals(Integer.valueOf(R.string.bulto_weight_error_measures),
                viewModel.getFormState().getValue().volumeError());
        assertNull(viewModel.getFormState().getValue().weightError());
    }

    @Test
    public void volumenCeroODesde10_muestraErrorDeVolumen() {
        // 0 cm, 0,004 m³ (redondea a 0,00) y 999³ cm³ ≈ 997 m³
        String[][] cases = {{"000", "050", "050"}, {"010", "020", "020"}, {"999", "999", "999"},
                {"200", "200", "250"}};
        for (String[] m : cases) {
            BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);

            assertNull(viewModel.onConfirmClicked("12", m[0], m[1], m[2]));
            assertEquals(Integer.valueOf(R.string.bulto_weight_error_volume),
                    viewModel.getFormState().getValue().volumeError());
        }
    }

    @Test
    public void corregirUnCampo_retiraSoloSuError() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);
        viewModel.onConfirmClicked("", "040", "", "");

        viewModel.onWeightChanged();
        assertNull(viewModel.getFormState().getValue().weightError());
        assertNotNull(viewModel.getFormState().getValue().volumeError());

        viewModel.onMeasuresChanged("040", "030", "");
        assertTrue(viewModel.getFormState().getValue().isValid());
    }

    @Test
    public void medidasRestauradas_recalculanVolumenSinRetirarErrores() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, null);
        viewModel.onConfirmClicked("12", "040", "", "");

        viewModel.onMeasuresRestored("040", "030", "035");

        assertEquals(new BigDecimal("0.04"), viewModel.getVolume().getValue());
        assertNotNull(viewModel.getFormState().getValue().volumeError());
    }

    // --- Volumen calculado ---

    @Test
    public void volumen_seCalculaSoloConLasTresMedidas() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.CUBICAR, null);

        viewModel.onMeasuresChanged("100", "100", "");
        assertNull(viewModel.getVolume().getValue());

        viewModel.onMeasuresChanged("100", "100", "100");
        assertEquals(new BigDecimal("1.00"), viewModel.getVolume().getValue());
    }

    // --- Comportamiento según el origen ---

    @Test
    public void botonKg_seAbreConLosDatosDelBultoYCancelarNoCambiaLaLectura() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.KG_BUTTON, CURRENT);

        assertEquals(CURRENT, viewModel.getInitialMeasures());
        assertEquals(new BigDecimal("0.04"), viewModel.getVolume().getValue());
        assertEquals(BultoWeightOrigin.CancelEffect.KEEP_UNCHANGED, viewModel.onCancelClicked().effect());
    }

    @Test
    public void abiertaSinArgumentos_comoDesdeElMenu_esBotonKgVacio() {
        BultoWeightViewModel viewModel = new BultoWeightViewModel(new SavedStateHandle());

        assertEquals(BultoWeightOrigin.KG_BUTTON, viewModel.getOrigin());
        assertNull(viewModel.getInitialMeasures());
        assertNull(viewModel.getVolume().getValue());
    }

    @Test
    public void cubicar_seAbreVaciaAunqueHayaDatosYCancelarDejaSinPeso() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.CUBICAR, CURRENT);

        assertNull(viewModel.getInitialMeasures());
        assertEquals(BultoWeightOrigin.CancelEffect.KEEP_WITHOUT_WEIGHT,
                viewModel.onCancelClicked().effect());
    }

    @Test
    public void pesoAnterior_seAbreConElBultoAnterior() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.PREVIOUS_BULTO, CURRENT);

        assertEquals(CURRENT, viewModel.getInitialMeasures());
        assertEquals(BultoWeightOrigin.CancelEffect.KEEP_WITHOUT_WEIGHT,
                viewModel.onCancelClicked().effect());
    }

    @Test
    public void pesoAnteriorSinBultoAnterior_seAbreACero() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.PREVIOUS_BULTO, null);

        BultoMeasures initial = viewModel.getInitialMeasures();
        assertNotNull(initial);
        assertEquals(0, initial.weightKg().signum());
        assertEquals(Integer.valueOf(0), initial.heightCm());
    }

    @Test
    public void expedicionCompleta_vaciaYCancelarAnulaLaLectura() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.FULL_EXPEDITION, CURRENT);

        assertNull(viewModel.getInitialMeasures());
        assertEquals(BultoWeightOrigin.CancelEffect.CANCEL_READING, viewModel.onCancelClicked().effect());
    }

    @Test
    public void expedicionCompleta_alAceptarMarcaExpedicionCompleta() {
        BultoWeightResult.Saved full = newViewModel(BultoWeightOrigin.FULL_EXPEDITION, null)
                .onConfirmClicked("5", "", "", "");
        BultoWeightResult.Saved kg = newViewModel(BultoWeightOrigin.KG_BUTTON, null)
                .onConfirmClicked("5", "", "", "");

        assertNotNull(full);
        assertTrue(full.fullExpedition());
        assertNotNull(kg);
        assertFalse(kg.fullExpedition());
    }

    @Test
    public void expedicionCompleta_sinPesoNoSeAcepta() {
        BultoWeightViewModel viewModel = newViewModel(BultoWeightOrigin.FULL_EXPEDITION, null);

        assertNull(viewModel.onConfirmClicked("", "", "", ""));
    }

    /** Simula los argumentos tal como los guarda {@link BultoWeightArgs#addTo}. */
    private static BultoWeightViewModel newViewModel(BultoWeightOrigin origin, BultoMeasures reference) {
        Map<String, Object> args = new HashMap<>();
        args.put("bultoWeight.origin", origin.name());
        if (reference != null) {
            args.put("bultoWeight.weight", reference.weightKg().toPlainString());
            if (reference.hasMeasures()) {
                args.put("bultoWeight.height", reference.heightCm());
                args.put("bultoWeight.width", reference.widthCm());
                args.put("bultoWeight.depth", reference.depthCm());
                args.put("bultoWeight.volume", reference.volumeM3().toPlainString());
            }
        }
        return new BultoWeightViewModel(new SavedStateHandle(args));
    }
}
