package com.redur.electra.ui.carga.camion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.carga.Consignee;
import com.redur.electra.data.model.carga.DeliveryInstruction;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.data.model.carga.ExpeditionAlert;
import com.redur.electra.data.model.carga.PackageKind;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class TruckLoadTagsTest {

    private static final Consignee CONSIGNEE = new Consignee("Carmen Meca", "CL Joan Buscallà 1-9", "08190", "S.Cugat");

    @Test
    public void primeraVueltaDePaquetesSinNadaMas_sinEtiquetas() {
        Expedition expedition = new Expedition("1", "80012345", "Remitente", CONSIGNEE, 1, 1, 0, "23B", 1, 1, 1,
                PackageKind.PACKAGE, Set.of(), Set.of(), 0, false, null, 1.0);

        assertTrue(TruckLoadTags.of(expedition).isEmpty());
    }

    @Test
    public void etiquetasEnElOrdenDelPrototipo_yLosAvisosMarcados() {
        Expedition expedition = new Expedition("1", "80012345", "Remitente", CONSIGNEE, 1, 1, 0, "23B", 1, 1, 2,
                PackageKind.PALLET,
                Set.of(DeliveryInstruction.TIME_WINDOW, DeliveryInstruction.CASH_ON_DELIVERY),
                Set.of(ExpeditionAlert.URGENT), 3, true, "E", 1.0);

        List<TruckLoadTag> tags = TruckLoadTags.of(expedition);

        assertEquals(List.of(
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_trip, List.of(2)), R.drawable.ic_local_shipping_24, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_pallet), 0, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_cash_on_delivery), 0, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_time_window), 0, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_urgent), R.drawable.ic_report_problem_24, true),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_attempts, List.of(3)), 0, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_pharma), 0, false),
                new TruckLoadTag(new UiText.Res(R.string.truck_load_tag_product, List.of("E")), 0, false)), tags);
    }
}
