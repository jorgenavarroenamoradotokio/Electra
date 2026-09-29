package com.redur.electra.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.redur.electra.data.model.user.User;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class UserSessionTest {

    private final UserSession session = new UserSession();
    private final User user = new User("jperez", "Juan Pérez", "P01", List.of(), Set.of());
    private final Credentials credentials = new Credentials("jperez", "secreta");

    @Test
    public void sinIniciar_noHayUsuarioNiCredenciales() {
        assertFalse(session.isActive());
        assertNull(session.getUser());
        assertNull(session.getCredentials());
    }

    @Test
    public void iniciar_guardaUsuarioYCredenciales() {
        session.start(user, credentials);

        assertTrue(session.isActive());
        assertSame(user, session.getUser());
        assertEquals(credentials, session.getCredentials());
    }

    @Test
    public void limpiar_borraTambienLasCredenciales() {
        session.start(user, credentials);

        session.clear();

        assertFalse(session.isActive());
        assertNull(session.getUser());
        assertNull(session.getCredentials());
    }

    @Test
    public void actualizarPlaza_cambiaSoloLaPlazaYConservaLasCredenciales() {
        session.start(user, credentials);

        session.updatePlaza("P02");

        assertEquals("P02", session.getUser().plazaId());
        assertEquals("jperez", session.getUser().username());
        assertEquals(credentials, session.getCredentials());
    }

    @Test
    public void actualizarPlazaSinSesion_noAbreNinguna() {
        session.updatePlaza("P02");

        assertFalse(session.isActive());
    }

    @Test
    public void credenciales_noExponenLaContrasenaEnToString() {
        assertFalse(credentials.toString().contains("secreta"));
        assertTrue(credentials.toString().contains("jperez"));
    }
}
