package com.redur.electra.data.remote.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.redur.electra.data.model.user.MenuItem;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.user.MenuDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;
import com.redur.electra.fake.LoginResponses;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    public void mapeaUsuarioConMenuYPermisos() {
        User user = mapper.toUser(LoginResponses.user());

        assertEquals("jperez", user.username());
        assertEquals("Juan Pérez", user.fullName());
        assertEquals("P01", user.plazaId());
        assertEquals(List.of(
                new MenuItem(1, "Recepción", null, Set.of(10, 11)),
                new MenuItem(2, "Descarga", 1, Set.of(20))), user.menu());
    }

    @Test
    public void conservaLaJerarquiaComoListaPlana() {
        List<MenuItem> menu = mapper.toUser(LoginResponses.user()).menu();

        assertTrue(menu.get(0).isRoot());
        assertFalse(menu.get(1).isRoot());
        assertEquals(Integer.valueOf(1), menu.get(1).parentId());
    }

    @Test
    public void coleccionesAusentesEnElJson_seTratanComoVacias() {
        User user = mapper.toUser(new UserDTO("jperez", null, null, null));

        assertTrue(user.menu().isEmpty());
        assertNull(user.fullName());
    }

    @Test
    public void elementosNulosYTextoAusente_noRompenElMapeo() {
        UserDTO dto = new UserDTO("jperez", null, null, Arrays.asList(
                null,
                new MenuDTO(3, null, null, Arrays.asList(null, null))));

        List<MenuItem> menu = mapper.toUser(dto).menu();

        assertEquals(List.of(new MenuItem(3, "", null, Set.of())), menu);
    }
}
