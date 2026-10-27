package com.redur.electra.ui.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class EnvironmentViewModelTest {

    private final UserSession session = new UserSession();
    private final EnvironmentViewModel viewModel = new EnvironmentViewModel(session);

    @Test
    public void conEntornoEnSesion_loDevuelveSinEspacios() {
        startSession(" PRE ");

        assertEquals("PRE", viewModel.getEnvironment());
    }

    @Test
    public void entornoAusenteOVacio_devuelveNull() {
        startSession(null);
        assertNull(viewModel.getEnvironment());

        startSession("  ");
        assertNull(viewModel.getEnvironment());
    }

    @Test
    public void sinSesion_devuelveNull() {
        assertNull(viewModel.getEnvironment());
    }

    @Test
    public void cambiarDePlaza_conservaElEntorno() {
        startSession("PRO");

        session.updatePlaza("P02");

        assertEquals("PRO", viewModel.getEnvironment());
    }

    private void startSession(String environment) {
        session.start(new User("jperez", "Juan Pérez", "P01", List.of(), Set.of(), environment),
                new Credentials("jperez", "secreta"));
    }
}
