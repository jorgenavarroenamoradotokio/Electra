package com.redur.electra.data.remote.mapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Convierte las plazas del backend en modelos. Se descartan las entradas sin código, ya que no
 * se podrían seleccionar; una descripción ausente se trata como vacía.
 */
public class PlaceMapper {

    @Inject
    public PlaceMapper() {
    }

    @NonNull
    public List<Place> toPlaces(@Nullable List<PlaceDTO> dtos) {
        List<Place> places = new ArrayList<>();
        if (dtos == null) {
            return places;
        }
        for (PlaceDTO dto : dtos) {
            if (dto != null && dto.plzsId() != null && !dto.plzsId().isBlank()) {
                String description = dto.description() != null ? dto.description() : "";
                places.add(new Place(dto.plzsId(), description));
            }
        }
        return places;
    }
}
