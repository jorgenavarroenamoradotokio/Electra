package com.redur.electra.data.remote.mapper;

import static org.junit.Assert.assertEquals;

import com.redur.electra.data.model.place.Place;
import com.redur.electra.data.remote.dto.response.place.PlaceDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PlaceMapperTest {

    private final PlaceMapper mapper = new PlaceMapper();

    @Test
    public void listaAusente_seTrataComoVacia() {
        assertEquals(List.of(), mapper.toPlaces(null));
    }

    @Test
    public void descartaEntradasSinCodigo() {
        List<PlaceDTO> dtos = Arrays.asList(
                null,
                new PlaceDTO(null, "Sin código"),
                new PlaceDTO("  ", "Código en blanco"),
                new PlaceDTO("P01", "Madrid Centro"));

        assertEquals(List.of(new Place("P01", "Madrid Centro")), mapper.toPlaces(dtos));
    }

    @Test
    public void descripcionAusente_seTrataComoVacia() {
        assertEquals(List.of(new Place("P01", "")),
                mapper.toPlaces(List.of(new PlaceDTO("P01", null))));
    }
}
