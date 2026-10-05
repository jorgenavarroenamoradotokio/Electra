package com.redur.electra.ui.label;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.core.error.PrinterFailure;
import com.redur.electra.core.permission.PermissionStatus;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.bluetooth.PrinterConnectionException;
import com.redur.electra.data.model.printer.Printer;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.LabelRepository;
import com.redur.electra.data.repository.PrinterRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeBluetoothPrinterGateway;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeLabelApiService;
import com.redur.electra.fake.LabelResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class PrintLabelViewModelTest {

    private static final Printer ZEBRA = new Printer("ZQ520", "AC:3F:A4:00:00:01", true, true);
    private static final Printer NEARBY = new Printer("ZD421", "AC:3F:A4:00:00:02", false, true);
    private static final Printer PHONE = new Printer("Móvil", "AC:3F:A4:00:00:03", false, false);

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private final Executor direct = Runnable::run;
    private final FakeBluetoothPrinterGateway bluetooth = new FakeBluetoothPrinterGateway();
    private final FakeLabelApiService api = new FakeLabelApiService();
    private final UserSession session = new UserSession();
    private PrintLabelViewModel viewModel;

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
        bluetooth.paired = List.of(ZEBRA);
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()));
        viewModel = new PrintLabelViewModel(new PrinterRepository(bluetooth, direct, direct),
                new LabelRepository(api, session, direct, direct));
    }

    @Test
    public void alAbrir_pideElPermisoUnaSolaVez() {
        assertTrue(state() instanceof PrintLabelState.RequestPermission);

        viewModel.onPermissionRequested();

        assertTrue(state() instanceof PrintLabelState.Busy);
    }

    @Test
    public void permisoConcedido_muestraEmparejadasYBuscaCercanas() {
        grantPermission();

        PrintLabelState.Choosing choosing = choosing();
        assertEquals(List.of(ZEBRA), choosing.printers());
        assertTrue(choosing.searching());
        assertEquals(1, bluetooth.discoveries());
    }

    @Test
    public void permisoDenegado_permiteVolverAPedirlo() {
        viewModel.onPermissionRequested();
        viewModel.onPermissionResult(PermissionStatus.DENIED);
        assertTrue(state() instanceof PrintLabelState.PermissionDenied);

        viewModel.onAllowClicked();

        assertTrue(state() instanceof PrintLabelState.RequestPermission);
    }

    @Test
    public void permisoDenegadoParaSiempre_llevaAAjustesYContinuaAlVolverConElPermiso() {
        viewModel.onPermissionRequested();
        viewModel.onPermissionResult(PermissionStatus.PERMANENTLY_DENIED);
        assertTrue(state() instanceof PrintLabelState.PermissionBlocked);

        viewModel.onSettingsPromptShown();
        viewModel.onSettingsOpened();
        viewModel.onReturnedFromSettings(true);

        assertTrue(state() instanceof PrintLabelState.Choosing);
    }

    @Test
    public void ajustesSinActivarElPermiso_explicaPorQueNoSePuedeImprimir() {
        viewModel.onPermissionResult(PermissionStatus.PERMANENTLY_DENIED);
        viewModel.onSettingsOpened();

        viewModel.onReturnedFromSettings(false);

        assertTrue(state() instanceof PrintLabelState.PermissionDenied);
    }

    @Test
    public void sinBluetooth_loExplica() {
        bluetooth.supported = false;

        grantPermission();

        assertTrue(state() instanceof PrintLabelState.BluetoothUnsupported);
    }

    @Test
    public void bluetoothApagado_ofreceActivarloYContinuaAlActivarse() {
        bluetooth.enabled = false;
        grantPermission();
        assertTrue(state() instanceof PrintLabelState.BluetoothOff);

        viewModel.onEnableBluetoothClicked();
        assertTrue(state() instanceof PrintLabelState.EnableBluetooth);
        viewModel.onEnableBluetoothLaunched();
        bluetooth.enabled = true;
        viewModel.onEnableBluetoothResult();

        assertTrue(state() instanceof PrintLabelState.Choosing);
    }

    @Test
    public void bluetoothSigueApagado_vuelveAOfrecerActivarlo() {
        bluetooth.enabled = false;
        grantPermission();
        viewModel.onEnableBluetoothClicked();
        viewModel.onEnableBluetoothLaunched();

        viewModel.onEnableBluetoothResult();

        assertTrue(state() instanceof PrintLabelState.BluetoothOff);
    }

    @Test
    public void busqueda_anadeLasNuevasSinRepetirYConLasImpresorasPrimero() {
        grantPermission();

        bluetooth.find(PHONE);
        bluetooth.find(NEARBY);
        bluetooth.find(ZEBRA);
        bluetooth.find(NEARBY);
        bluetooth.finishDiscovery();

        PrintLabelState.Choosing choosing = choosing();
        assertEquals(List.of(ZEBRA, NEARBY, PHONE), choosing.printers());
        assertFalse(choosing.searching());
    }

    @Test
    public void ubicacionApagadaEnAndroidAntiguo_avisaYMuestraLasEmparejadas() {
        bluetooth.canDiscover = false;

        grantPermission();

        PrintLabelState.Choosing choosing = choosing();
        assertEquals(List.of(ZEBRA), choosing.printers());
        assertFalse(choosing.searching());
        assertEquals(new UiText.Res(R.string.print_location_off), choosing.notice());
        assertEquals(0, bluetooth.discoveries());
    }

    @Test
    public void buscarDeNuevo_soloCuandoHaTerminado() {
        grantPermission();
        viewModel.onSearchAgainClicked();
        assertEquals(1, bluetooth.discoveries());

        bluetooth.finishDiscovery();
        viewModel.onSearchAgainClicked();

        assertEquals(2, bluetooth.discoveries());
        assertTrue(choosing().searching());
    }

    @Test
    public void elegirImpresora_detieneLaBusquedaGeneraLaEtiquetaYLeEnviaSuZpl() {
        grantPermission();

        viewModel.onPrinterSelected(ZEBRA);

        assertTrue(bluetooth.discoveryCanceled());
        assertEquals(1, api.createCalls());
        assertEquals(ZEBRA.address(), bluetooth.lastAddress());
        assertArrayEquals(LabelResponses.ZPL.getBytes(StandardCharsets.UTF_8), bluetooth.lastData());
        assertEquals(new PrintLabelState.Printed(ZEBRA), state());
    }

    @Test
    public void mientrasSeGeneraLaEtiqueta_avisaYNoAdmiteOtraImpresora() {
        api.willReturnCreate(FakeCall.success(LabelResponses.onePage()).deferred());
        grantPermission();

        viewModel.onPrinterSelected(ZEBRA);
        viewModel.onPrinterSelected(NEARBY);

        assertEquals(new PrintLabelState.Printing(ZEBRA, false), state());
        assertEquals(1, api.createCalls());
    }

    @Test
    public void impresoraNoResponde_vuelveALaListaConElMotivo() {
        bluetooth.sendFailure = new PrinterConnectionException("apagada", new IOException());
        grantPermission();

        viewModel.onPrinterSelected(ZEBRA);

        PrintLabelState.Choosing choosing = choosing();
        assertEquals(List.of(ZEBRA), choosing.printers());
        assertEquals(ErrorUiMapper.toUiText(new AppError.Printer(PrinterFailure.CONNECTION)), choosing.notice());
    }

    @Test
    public void sinConexionAlGenerar_noLlegaAConectarConLaImpresora() {
        api.willReturnCreate(FakeCall.failure(new UnknownHostException()));
        grantPermission();

        viewModel.onPrinterSelected(ZEBRA);

        assertEquals(ErrorUiMapper.toUiText(new AppError.Network(NetworkType.NO_CONNECTION)),
                choosing().notice());
        assertNull(bluetooth.lastAddress());
    }

    @Test
    public void tocarDeNuevoTrasUnFallo_reintenta() {
        bluetooth.sendFailure = new IOException("broken pipe");
        grantPermission();
        viewModel.onPrinterSelected(ZEBRA);

        bluetooth.sendFailure = null;
        viewModel.onPrinterSelected(ZEBRA);

        assertEquals(new PrintLabelState.Printed(ZEBRA), state());
    }

    @Test
    public void cerrarLaHoja_detieneLaBusqueda() {
        grantPermission();

        viewModel.onCleared();

        assertTrue(bluetooth.discoveryCanceled());
    }

    private void grantPermission() {
        viewModel.onPermissionRequested();
        viewModel.onPermissionResult(PermissionStatus.GRANTED);
    }

    private PrintLabelState state() {
        return viewModel.getState().getValue();
    }

    private PrintLabelState.Choosing choosing() {
        return (PrintLabelState.Choosing) state();
    }
}
