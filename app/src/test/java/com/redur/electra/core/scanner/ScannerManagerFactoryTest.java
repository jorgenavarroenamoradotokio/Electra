package com.redur.electra.core.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.redur.electra.core.scanner.datalogic.DatalogicScannerManager;
import com.redur.electra.core.scanner.honeywell.HoneywellScannerManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ScannerManagerFactoryTest {

    private ScannerManagerFactory factory;

    @Before
    public void setUp() {
        factory = new ScannerManagerFactory(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void terminalHoneywell_usaElEscanerHoneywell() {
        ShadowBuild.setManufacturer("Honeywell");

        HardwareScannerManager manager = factory.create(barcode -> { });

        assertTrue(manager instanceof HoneywellScannerManager);
        assertTrue(manager.isAvailable());
    }

    @Test
    public void terminalDatalogic_usaElEscanerDatalogic() {
        ShadowBuild.setManufacturer("Datalogic");

        HardwareScannerManager manager = factory.create(barcode -> { });

        assertTrue(manager instanceof DatalogicScannerManager);
        assertTrue(manager.isAvailable());
    }

    @Test
    public void fabricanteDesconocido_noHayEscanerHardware() {
        ShadowBuild.setManufacturer("Google");

        HardwareScannerManager manager = factory.create(barcode -> { });

        assertTrue(manager instanceof NoOpScannerManager);
        assertFalse(manager.isAvailable());
    }

    @Test
    public void fabricanteNulo_noHayEscanerHardware() {
        ShadowBuild.setManufacturer(null);

        assertFalse(factory.create(barcode -> { }).isAvailable());
    }
}
