package com.redur.electra.core.scanner.datalogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.redur.electra.fake.FakeLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class DatalogicScannerManagerTest {

    private final List<String> scans = new ArrayList<>();
    private Application context;
    private FakeLifecycleOwner owner;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        owner = new FakeLifecycleOwner();
        owner.getLifecycle().addObserver(new DatalogicScannerManager(context, scans::add));
        owner.moveTo(Lifecycle.State.RESUMED);
    }

    @Test
    public void lecturaComoTexto_seEntregaAlListener() {
        send(datalogicIntent().putExtra(DatalogicScannerManager.EXTRA_BARCODE_STRING, "B0001"));

        assertEquals(List.of("B0001"), scans);
    }

    @Test
    public void lecturaSoloEnBytes_seDecodificaComoUtf8() {
        send(datalogicIntent()
                .putExtra(DatalogicScannerManager.EXTRA_BARCODE_DATA, "Ñ-01".getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of("Ñ-01"), scans);
    }

    @Test
    public void intentSinCodigo_noLlegaAlListener() {
        send(datalogicIntent());

        assertTrue(scans.isEmpty());
    }

    @Test
    public void enPausa_noSeEntreganLecturas() {
        owner.moveTo(Lifecycle.State.STARTED);

        send(datalogicIntent().putExtra(DatalogicScannerManager.EXTRA_BARCODE_STRING, "B0001"));

        assertTrue(scans.isEmpty());
    }

    /** Como lo envía el Intent Wedge de Datalogic: acción y categoría por defecto. */
    private static Intent datalogicIntent() {
        return new Intent(DatalogicScannerManager.ACTION).addCategory(DatalogicScannerManager.CATEGORY);
    }

    private void send(Intent intent) {
        context.sendBroadcast(intent);
        shadowOf(Looper.getMainLooper()).idle();
    }
}
