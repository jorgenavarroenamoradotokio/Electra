package com.redur.electra.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.redur.electra.data.model.user.User;
import com.redur.electra.data.session.Credentials;
import com.redur.electra.data.session.UserSession;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class MainViewModelTest {

    private final UserSession session = new UserSession();
    private final MainViewModel viewModel = new MainViewModel(session);

    @Test
    public void sinSesion_noHayNombre() {
        assertNull(viewModel.getUserDisplayName());
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
}
