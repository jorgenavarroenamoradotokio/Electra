package com.redur.electra.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.redur.electra.R;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.data.model.user.MenuItem;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;
import com.redur.electra.ui.menu.DrawerMenuRow;
import com.redur.electra.ui.menu.MenuActionRegistry;
import com.redur.electra.ui.menu.MenuArgs;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class MainViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    private static final int HOME = MenuActionRegistry.HOME_MENU_ID;
    /** Id que no está en MenuActionRegistry: se muestra con el texto del servidor. */
    private static final int UNREGISTERED_MENU_ID = 5;

    private final UserSession session = new UserSession();
    private final MainViewModel viewModel = new MainViewModel(session);

    @Test
    public void sinSesion_noHayNombre() {
        assertNull(viewModel.getUserDisplayName());
        assertNull(viewModel.getUserInitial());
    }

    @Test
    public void conNombreCompleto_seMuestraElNombreCompleto() {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of()), new Credentials("jperez", "secreta"));

        assertEquals("Juan Pérez", viewModel.getUserDisplayName());
    }

    @Test
    public void sinNombreCompleto_seMuestraElUsuario() {
        session.start(new User("jperez", "  ", "P01", List.of(), Set.of()), new Credentials("jperez", "secreta"));

        assertEquals("jperez", viewModel.getUserDisplayName());
    }

    @Test
    public void laInicialDelAvatarEsLaPrimeraLetraEnMayuscula() {
        session.start(new User("itsoporte", null, "P01", List.of(), Set.of()), new Credentials("itsoporte", "secreta"));

        assertEquals("I", viewModel.getUserInitial());
    }

    @Test
    public void inicioEsFijo_primeroYActivo_aunqueElServidorNoEnvieMenu() {
        startWithMenu();

        List<DrawerMenuRow> rows = rows();

        assertEquals(1, rows.size());
        assertEquals(HOME, rows.get(0).menuId());
        assertEquals(new UiText.Res(R.string.menu_0), rows.get(0).title());
        assertTrue(rows.get(0).selected());
    }

    @Test
    public void siElServidorEnviaInicio_noSeDuplica() {
        startWithMenu(new MenuItem(HOME, "Inicio", null, Set.of()));

        assertEquals(List.of(HOME), ids(rows()));
    }

    @Test
    public void losSubmenusEmpiezanPlegados() {
        startWithServerStructure();

        List<DrawerMenuRow> rows = rows();

        assertEquals(List.of(HOME, 1, 2, 3, 5), ids(rows));
        DrawerMenuRow administracion = rows.get(3);
        assertTrue(administracion.group());
        assertFalse(administracion.expanded());
        assertFalse(rows.get(1).group());
    }

    @Test
    public void alDesplegarUnGrupo_susHijosAparecenDebajoConSangria() {
        startWithServerStructure();

        viewModel.onGroupToggled(3);

        List<DrawerMenuRow> rows = rows();
        assertEquals(List.of(HOME, 1, 2, 3, 4, 5), ids(rows));
        assertTrue(rows.get(3).expanded());
        assertEquals(1, rows.get(4).depth());
        assertEquals(0, rows.get(5).depth());
    }

    @Test
    public void alPlegarUnGrupo_susHijosDesaparecen() {
        startWithServerStructure();
        viewModel.onGroupToggled(3);

        viewModel.onGroupToggled(3);

        assertEquals(List.of(HOME, 1, 2, 3, 5), ids(rows()));
    }

    @Test
    public void alMostrarseUnaPantallaDeSubmenu_seMarcaYSeDespliegaSuGrupo() {
        startWithServerStructure();

        viewModel.onMenuShown(4);

        List<DrawerMenuRow> rows = rows();
        assertEquals(List.of(HOME, 1, 2, 3, 4, 5), ids(rows));
        assertFalse(rows.get(0).selected());
        assertTrue(rows.get(4).selected());
    }

    @Test
    public void menuRegistrado_usaSuLiteral_yElNoRegistrado_elTextoDelServidor() {
        startWithServerStructure();

        List<DrawerMenuRow> rows = rows();

        assertEquals(new UiText.Res(R.string.menu_1), rows.get(1).title());
        assertEquals(new UiText.Raw("NUEVO"), rows.get(4).title());
        assertEquals(R.drawable.ic_description_24, rows.get(4).icon());
    }

    @Test
    public void hijoSinPadreEnLaLista_seMuestraEnElPrimerNivel() {
        startWithMenu(new MenuItem(4, "Cambiar plaza", 99, Set.of()));

        List<DrawerMenuRow> rows = rows();

        assertEquals(List.of(HOME, 4), ids(rows));
        assertEquals(0, rows.get(1).depth());
    }

    @Test
    public void idsRepetidos_noProducenBucles() {
        startWithMenu(
                new MenuItem(3, "Padre", null, Set.of()),
                new MenuItem(3, "Repetido", 3, Set.of()));

        List<DrawerMenuRow> rows = rows();

        assertEquals(List.of(HOME, 3), ids(rows));
        assertFalse(rows.get(1).group());
    }

    @Test
    public void alPulsarUnMenu_seLeenSusPermisosDeLaSesion() {
        startWithMenu(new MenuItem(1, "Carga", null, Set.of(7, 8)));

        MenuArgs args = viewModel.getMenuArgs(1);

        assertEquals(1, args.menuId());
        assertTrue(args.hasPermission(7));
        assertTrue(args.hasPermission(8));
        assertFalse(args.hasPermission(9));
    }

    @Test
    public void inicio_seAbreSinPermisos() {
        startWithMenu();

        MenuArgs args = viewModel.getMenuArgs(HOME);

        assertEquals(HOME, args.menuId());
        assertTrue(args.permissions().isEmpty());
    }

    @Test
    public void menuQueNoEsDelUsuario_noTienePermisos() {
        startWithMenu();

        assertNull(viewModel.getMenuArgs(1));
    }

    /** Estructura de la tabla de menús del servidor: Administración (3) agrupa Cambiar plaza (4). */
    private void startWithServerStructure() {
        startWithMenu(
                new MenuItem(1, "Carga Camion Internacional", null, Set.of()),
                new MenuItem(2, "Consulta Albaran", null, Set.of()),
                new MenuItem(3, "Administracion", null, Set.of()),
                new MenuItem(4, "Cambiar plaza", 3, Set.of()),
                new MenuItem(UNREGISTERED_MENU_ID, "NUEVO", null, Set.of()));
    }

    private void startWithMenu(MenuItem... menu) {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(menu), Set.of()), new Credentials("jperez", "secreta"));
    }

    private List<DrawerMenuRow> rows() {
        return viewModel.getMenuRows().getValue();
    }

    private static List<Integer> ids(List<DrawerMenuRow> rows) {
        return rows.stream().map(DrawerMenuRow::menuId).toList();
    }
}
