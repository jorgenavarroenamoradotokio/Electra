package com.redur.electra.data.remote.mapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Convierte los tipos de bulto del backend en modelos. Se descartan las entradas sin código o sin
 * tipo, ya que no se podrían asignar ni mostrar; una descripción ausente se trata como vacía.
 */
public class BultoTypeMapper {

    @Inject
    public BultoTypeMapper() {
    }

    @NonNull
    public List<BultoType> toBultoTypes(@Nullable List<BultoTypeDTO> dtos) {
        List<BultoType> types = new ArrayList<>();
        if (dtos == null) {
            return types;
        }
        for (BultoTypeDTO dto : dtos) {
            if (dto != null && isPresent(dto.code()) && isPresent(dto.type())) {
                String description = dto.description() != null ? dto.description() : "";
                types.add(new BultoType(dto.code(), dto.type(), description, dto.photoRequired()));
            }
        }
        return types;
    }

    private static boolean isPresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
