package com.redur.electra.data.remote.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BultoTypeMapperTest {

    private final BultoTypeMapper mapper = new BultoTypeMapper();

    @Test
    public void conservaOrdenYSiExigeFoto() {
        List<BultoType> types = mapper.toBultoTypes(List.of(
                new BultoTypeDTO("010", "BU", "BULTO PAQUETE", false),
                new BultoTypeDTO("030", "+3", "+ DE 3 METROS", true)));

        assertEquals(List.of(
                new BultoType("010", "BU", "BULTO PAQUETE", false),
                new BultoType("030", "+3", "+ DE 3 METROS", true)), types);
    }

    @Test
    public void descartaEntradasSinCodigoOSinTipo() {
        List<BultoType> types = mapper.toBultoTypes(Arrays.asList(
                null,
                new BultoTypeDTO(null, "BU", "BULTO PAQUETE", false),
                new BultoTypeDTO("020", " ", "PALET", false),
                new BultoTypeDTO("040", "IR", "IRREGULAR", true)));

        assertEquals(List.of(new BultoType("040", "IR", "IRREGULAR", true)), types);
    }

    @Test
    public void descripcionAusente_seTrataComoVacia() {
        List<BultoType> types = mapper.toBultoTypes(List.of(new BultoTypeDTO("020", "PL", null, false)));

        assertEquals("", types.get(0).description());
    }

    @Test
    public void listaNula_devuelveVacia() {
        assertTrue(mapper.toBultoTypes(null).isEmpty());
    }
}
