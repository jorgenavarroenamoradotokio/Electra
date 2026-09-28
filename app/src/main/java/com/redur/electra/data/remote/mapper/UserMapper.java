package com.redur.electra.data.remote.mapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.user.MenuItem;
import com.redur.electra.data.model.user.User;
import com.redur.electra.data.remote.dto.response.user.MenuDTO;
import com.redur.electra.data.remote.dto.response.user.PermisoDTO;
import com.redur.electra.data.remote.dto.response.user.PermisoUsuarioDTO;
import com.redur.electra.data.remote.dto.response.user.UserDTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Convierte los DTOs de usuario en modelos. Gson puede dejar a null cualquier campo que no venga
 * en el JSON: las colecciones ausentes se tratan como vacías.
 */
public class UserMapper {

    @Inject
    public UserMapper() {
    }

    @NonNull
    public User toUser(@NonNull UserDTO dto) {
        return new User(dto.username(), dto.fullname(), dto.plzsId(), toMenu(dto.menu()), toUserPermissions(dto.permission()));
    }

    /** Permisos generales del usuario. Se descartan entradas o ids ausentes en el JSON. */
    @NonNull
    private Set<Integer> toUserPermissions(@Nullable Collection<PermisoUsuarioDTO> permissions) {
        Set<Integer> values = new HashSet<>();
        if (permissions == null) {
            return values;
        }
        for (PermisoUsuarioDTO permission : permissions) {
            if (permission != null && permission.id() != null) {
                values.add(permission.id());
            }
        }
        return values;
    }

    @NonNull
    private List<MenuItem> toMenu(@Nullable List<MenuDTO> menu) {
        List<MenuItem> items = new ArrayList<>();
        if (menu == null) {
            return items;
        }
        for (MenuDTO dto : menu) {
            if (dto != null) {
                items.add(toMenuItem(dto));
            }
        }
        return items;
    }

    @NonNull
    private MenuItem toMenuItem(@NonNull MenuDTO dto) {
        String text = dto.text() != null ? dto.text() : "";
        return new MenuItem(dto.menuID(), text, dto.parentMenuID(), toPermissions(dto.permission()));
    }

    @NonNull
    private Set<Integer> toPermissions(@Nullable Collection<PermisoDTO> permissions) {
        Set<Integer> values = new HashSet<>();
        if (permissions == null) {
            return values;
        }
        for (PermisoDTO permission : permissions) {
            if (permission != null) {
                values.add(permission.value());
            }
        }
        return values;
    }
}
