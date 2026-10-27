package com.redur.electra.data.remote.mapper;

import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class IncidenciaMapper {

    @Inject
    public IncidenciaMapper() {

    }


    @NotNull
    public List<BultoIncidencia> toIncidencia(@Nullable List<IncidenciaDTO> dtos) {
        List<BultoIncidencia> incidencias = new ArrayList<>();
        if (dtos == null) {
            return incidencias;
        }

        for (IncidenciaDTO dto : dtos) {
            if (dto != null && isPresent(dto.codigoIncidencia())) {
                String description = dto.descripcion() != null ? dto.descripcion() : "";
                // El backend aún no indica qué incidencias exigen observaciones: ninguna las exige
                incidencias.add(new BultoIncidencia(dto.codigoIncidencia(), description, dto.photoRequired(), false));
            }
        }
        return incidencias;
    }

    private static boolean isPresent(@androidx.annotation.Nullable String value) {
        return value != null && !value.isBlank();
    }

}
