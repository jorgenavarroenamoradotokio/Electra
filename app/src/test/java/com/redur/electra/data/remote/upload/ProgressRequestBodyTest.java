package com.redur.electra.data.remote.upload;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okio.Buffer;

public class ProgressRequestBodyTest {

    private final List<Integer> progress = new ArrayList<>();

    @Test
    public void escribeElContenidoCompletoYTerminaEn100() throws IOException {
        byte[] content = new byte[100 * 1024];
        content[content.length - 1] = 7;
        Buffer sink = new Buffer();

        new ProgressRequestBody(content, null, progress::add).writeTo(sink);

        assertArrayEquals(content, sink.readByteArray());
        assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
    }

    @Test
    public void informaDeAvancesCrecientesSinRepetirPorcentajes() throws IOException {
        new ProgressRequestBody(new byte[1024 * 1024], null, progress::add).writeTo(new Buffer());

        assertTrue(progress.size() > 1);
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i) > progress.get(i - 1));
        }
    }

    @Test
    public void alReescribirse_elProgresoEmpiezaDeNuevo() throws IOException {
        ProgressRequestBody body = new ProgressRequestBody(new byte[64 * 1024], null, progress::add);

        body.writeTo(new Buffer());
        int firstWrite = progress.size();
        body.writeTo(new Buffer());

        assertEquals(progress.subList(0, firstWrite), progress.subList(firstWrite, progress.size()));
    }

    @Test
    public void contenidoVacio_seDaPorCompletado() throws IOException {
        new ProgressRequestBody(new byte[0], null, progress::add).writeTo(new Buffer());

        assertEquals(List.of(100), progress);
    }
}
