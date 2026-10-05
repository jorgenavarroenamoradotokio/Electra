package com.redur.electra.ui.photo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.redur.electra.R;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.core.permission.AppPermission;
import com.redur.electra.core.permission.PermissionStatus;
import com.redur.electra.core.ui.ErrorUiMapper;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.repository.ImgUploadRepository;
import com.redur.electra.data.repository.PhotoRepository;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.fake.FakeCall;
import com.redur.electra.fake.FakeFileApiService;
import com.redur.electra.fake.FileResponses;
import com.redur.electra.rule.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class PhotoSourceViewModelTest {

    private static final String CAPTURE_URI = "content://com.redur.electra.fileprovider/photos/IMG_1.jpg";
    private static final String GALLERY_URI = "content://media/picker/0/1";

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    /** Ejecuta en el acto: el trabajo "en segundo plano" termina antes de comprobar. */
    private final Executor direct = Runnable::run;
    private final FakeFileApiService api = new FakeFileApiService();
    private final UserSession session = new UserSession();
    /** Las imágenes se "leen" sin ContentResolver: el contenido no importa al ViewModel. */
    private final ImgUploadRepository uploadRepository = new ImgUploadRepository(
            uri -> new ImgUploadRepository.ImageContent("IMG_1.jpg", "image/jpeg", new byte[]{1}),
            api, session, direct, direct);
    private SavedStateHandle savedState;
    private PhotoSourceViewModel viewModel;

    @Before
    public void setUp() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()),
                new Credentials("jperez", "secreta"));
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));
        savedState = new SavedStateHandle();
        viewModel = newViewModel(new PhotoRepository(() -> tmp.getRoot()));
    }

    @Test
    public void estadoInicial_listoYSinAvisos() {
        PhotoSourceState.Ready ready = (PhotoSourceState.Ready) state();

        assertNull(ready.notice());
    }

    @Test
    public void elegirOrigen_bloqueaUnSegundoToqueHastaResolverElPermiso() {
        assertTrue(viewModel.onSourceSelected(PhotoSource.CAMERA));

        assertFalse(viewModel.onSourceSelected(PhotoSource.GALLERY));
        assertTrue(state() instanceof PhotoSourceState.Busy);
    }

    @Test
    public void camaraConcedida_preparaUnFicheroYPideAbrirLaCamara() {
        viewModel.onSourceSelected(PhotoSource.CAMERA);

        viewModel.onPermissionResult(PermissionStatus.GRANTED);

        PhotoSourceState.LaunchCamera launch = (PhotoSourceState.LaunchCamera) state();
        assertTrue(launch.output().exists());
        assertEquals(tmp.getRoot(), launch.output().getParentFile());
    }

    @Test
    public void galeriaConcedida_pideAbrirLaGaleria() {
        viewModel.onSourceSelected(PhotoSource.GALLERY);

        viewModel.onPermissionResult(PermissionStatus.GRANTED);

        assertTrue(state() instanceof PhotoSourceState.LaunchGallery);
    }

    @Test
    public void denegadoUnaVez_explicaQueSePuedeVolverAIntentar() {
        viewModel.onSourceSelected(PhotoSource.CAMERA);

        viewModel.onPermissionResult(PermissionStatus.DENIED);

        assertEquals(new UiText.Res(R.string.photo_camera_denied), ((PhotoSourceState.Ready) state()).notice());
        assertTrue(viewModel.onSourceSelected(PhotoSource.CAMERA));
    }

    @Test
    public void denegadoParaSiempre_ofreceIrAAjustesConElPermisoDelOrigen() {
        viewModel.onSourceSelected(PhotoSource.GALLERY);

        viewModel.onPermissionResult(PermissionStatus.PERMANENTLY_DENIED);

        assertEquals(AppPermission.GALLERY, ((PhotoSourceState.PermissionBlocked) state()).permission());
    }

    @Test
    public void avisoDeAjustesMostrado_noSeRepiteAlRecrearLaVista() {
        blockCamera();

        viewModel.onSettingsPromptShown();

        assertTrue(state() instanceof PhotoSourceState.Busy);
    }

    @Test
    public void ahoraNo_vuelveALaHojaSinInsistir() {
        blockCamera();
        viewModel.onSettingsPromptShown();

        viewModel.onSettingsDeclined();

        assertNull(((PhotoSourceState.Ready) state()).notice());
    }

    @Test
    public void alVolverDeAjustesConElPermisoActivado_continuaSinVolverAPulsar() {
        blockCamera();
        viewModel.onSettingsPromptShown();
        viewModel.onSettingsOpened();
        assertEquals(AppPermission.CAMERA, ((PhotoSourceState.WaitingForSettings) state()).permission());

        viewModel.onReturnedFromSettings(true);

        assertTrue(state() instanceof PhotoSourceState.LaunchCamera);
    }

    @Test
    public void alVolverDeAjustesSinActivarlo_quedaListaSinAvisos() {
        blockCamera();
        viewModel.onSettingsPromptShown();
        viewModel.onSettingsOpened();

        viewModel.onReturnedFromSettings(false);

        assertNull(((PhotoSourceState.Ready) state()).notice());
    }

    @Test
    public void volverAPrimerPlanoSinHaberIdoAAjustes_noHaceNada() {
        viewModel.onReturnedFromSettings(true);

        assertTrue(state() instanceof PhotoSourceState.Ready);
    }

    @Test
    public void fotoHecha_seEnviaYEntregaElUriDeLaCaptura() throws IOException {
        File output = openCamera();
        Files.write(output.toPath(), new byte[]{1, 2, 3});

        viewModel.onCameraClosed();

        assertEquals(1, api.imgUploadCalls());
        assertEquals(CAPTURE_URI, ((PhotoSourceState.Uploaded) state()).imageUri());
    }

    @Test
    public void camaraCanceladaSinFoto_borraElFicheroVacioYVuelveALaHoja() {
        File output = openCamera();

        viewModel.onCameraClosed();

        assertFalse(output.exists());
        assertNull(((PhotoSourceState.Ready) state()).notice());
    }

    @Test
    public void laCapturaSobreviveALaMuerteDelProceso() throws IOException {
        File output = openCamera();
        Files.write(output.toPath(), new byte[]{1});
        // El sistema mata el proceso con la cámara abierta: el ViewModel nuevo solo tiene el estado guardado
        PhotoSourceViewModel restored = newViewModel(new PhotoRepository(() -> tmp.getRoot()));

        restored.onCameraClosed();

        assertEquals(CAPTURE_URI, ((PhotoSourceState.Uploaded) restored.getState().getValue()).imageUri());
    }

    @Test
    public void sinAppDeCamara_borraLaCapturaYLoExplica() {
        File output = openCameraFile();

        viewModel.onSourceOpenFailed();

        assertFalse(output.exists());
        assertEquals(new UiText.Res(R.string.photo_camera_open_failed), ((PhotoSourceState.Ready) state()).notice());
    }

    @Test
    public void noSePuedePrepararElFichero_loExplica() throws IOException {
        File blocker = tmp.newFile("bloqueo");
        viewModel = newViewModel(new PhotoRepository(() -> new File(blocker, "photos")));
        viewModel.onSourceSelected(PhotoSource.CAMERA);

        viewModel.onPermissionResult(PermissionStatus.GRANTED);

        assertEquals(new UiText.Res(R.string.photo_capture_failed), ((PhotoSourceState.Ready) state()).notice());
    }

    @Test
    public void imagenElegidaEnLaGaleria_seEnviaYEntrega() {
        pickFromGallery();

        assertEquals(1, api.imgUploadCalls());
        assertEquals(GALLERY_URI, ((PhotoSourceState.Uploaded) state()).imageUri());
    }

    @Test
    public void duranteElEnvio_muestraElProgresoYNoDejaElegirOtroOrigen() {
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)).deferred());

        pickFromGallery();

        assertEquals(new PhotoSourceState.Uploading(0), state());
        assertFalse(viewModel.onSourceSelected(PhotoSource.CAMERA));
    }

    @Test
    public void envioFallido_explicaElErrorYPermiteElegirOtraImagen() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));

        pickFromGallery();

        UiText expected = ErrorUiMapper.toUiText(new AppError.Network(NetworkType.NO_CONNECTION));
        assertEquals(expected, ((PhotoSourceState.UploadFailed) state()).message());
        assertTrue(viewModel.onSourceSelected(PhotoSource.CAMERA));
    }

    @Test
    public void reintentarTrasFallo_reenviaLaMismaImagen() {
        api.willReturnUpload(FakeCall.failure(new UnknownHostException()));
        pickFromGallery();
        api.willReturnUpload(FakeCall.success(FileResponses.uploaded(true)));

        viewModel.onRetryUploadClicked();

        assertEquals(2, api.imgUploadCalls());
        assertEquals(GALLERY_URI, ((PhotoSourceState.Uploaded) state()).imageUri());
    }

    @Test
    public void reintentarSinFalloPrevio_noHaceNada() {
        viewModel.onRetryUploadClicked();

        assertEquals(0, api.imgUploadCalls());
        assertTrue(state() instanceof PhotoSourceState.Ready);
    }

    @Test
    public void salirDeLaGaleriaSinElegir_vuelveALaHoja() {
        viewModel.onSourceSelected(PhotoSource.GALLERY);
        viewModel.onPermissionResult(PermissionStatus.GRANTED);
        viewModel.onGalleryOpened();

        viewModel.onGalleryResult(null);

        assertNull(((PhotoSourceState.Ready) state()).notice());
    }

    private void pickFromGallery() {
        viewModel.onSourceSelected(PhotoSource.GALLERY);
        viewModel.onPermissionResult(PermissionStatus.GRANTED);
        viewModel.onGalleryOpened();
        viewModel.onGalleryResult(GALLERY_URI);
    }

    private void blockCamera() {
        viewModel.onSourceSelected(PhotoSource.CAMERA);
        viewModel.onPermissionResult(PermissionStatus.PERMANENTLY_DENIED);
    }

    /** Fichero de captura preparado, antes de abrir la cámara. */
    private File openCameraFile() {
        viewModel.onSourceSelected(PhotoSource.CAMERA);
        viewModel.onPermissionResult(PermissionStatus.GRANTED);
        return ((PhotoSourceState.LaunchCamera) state()).output();
    }

    private File openCamera() {
        File output = openCameraFile();
        viewModel.onCameraOpened(CAPTURE_URI);
        return output;
    }

    private PhotoSourceViewModel newViewModel(PhotoRepository repository) {
        return new PhotoSourceViewModel(savedState, repository, uploadRepository, direct, direct);
    }

    private PhotoSourceState state() {
        return viewModel.getState().getValue();
    }
}
