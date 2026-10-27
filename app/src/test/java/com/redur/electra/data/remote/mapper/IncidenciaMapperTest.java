package com.redur.electra.data.remote.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.redur.electra.data.model.bulto.BultoIncidencia;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class IncidenciaMapperTest {

    private final IncidenciaMapper mapper = new IncidenciaMapper();

    @Test
    public void conservaOrdenYSiExigeFoto_yNingunaExigeObservaciones() {
        List<BultoIncidencia> incidencias = mapper.toIncidencia(List.of(
                new IncidenciaDTO("A16", "BULTO DAÑADO", false),
                new IncidenciaDTO("A21", "BULTO ABIERTO", true)));

        assertEquals(List.of(
                new BultoIncidencia("A16", "BULTO DAÑADO", false, false),
                new BultoIncidencia("A21", "BULTO ABIERTO", true, false)), incidencias);
    }

    @Test
    public void descartaEntradasSinCodigo() {
        List<BultoIncidencia> incidencias = mapper.toIncidencia(Arrays.asList(
                null,
                new IncidenciaDTO(null, "BULTO DAÑADO", false),
                new IncidenciaDTO(" ", "BULTO ABIERTO", true),
                new IncidenciaDTO("A40", "FALTA", false)));

        assertEquals(List.of(new BultoIncidencia("A40", "FALTA", false, false)), incidencias);
    }

    @Test
    public void descripcionAusente_seTrataComoVacia() {
        List<BultoIncidencia> incidencias = mapper.toIncidencia(List.of(new IncidenciaDTO("A16", null, false)));

        assertEquals("", incidencias.get(0).descripcion());
    }

    @Test
    public void listaNula_devuelveVacia() {
        assertTrue(mapper.toIncidencia(null).isEmpty());
    }
}
