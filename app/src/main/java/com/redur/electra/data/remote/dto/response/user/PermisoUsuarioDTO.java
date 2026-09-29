package com.redur.electra.data.remote.dto.response.user;

import com.google.gson.annotations.SerializedName;

/**
 * Permiso general del usuario (lista "permiso" del login), independiente del menú.
 * No confundir con {@link PermisoDTO}: los permisos de menú llegan con otra clave ("permisosId").
 * El id es Integer para distinguir un permiso sin id (null) de un id real.
 */
public record PermisoUsuarioDTO(
        @SerializedName("permisoId") Integer id,
        @SerializedName("descripcionPermiso") String description) {
}
