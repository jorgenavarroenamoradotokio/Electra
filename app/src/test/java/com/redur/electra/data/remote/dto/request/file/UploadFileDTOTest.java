package com.redur.electra.data.remote.dto.request.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;

/** Los nombres de las partes son el contrato con el backend: si no coinciden, el campo llega vacío. */
public class UploadFileDTOTest {

    private static final Pattern PART_NAME = Pattern.compile("name=\"([^\"]+)\"");

    private final RequestBody file = RequestBody.create("contenido", MediaType.get("text/plain"));

    @Test
    public void enviaCadaCampoConElNombreDelBackend() throws IOException {
        UploadFileDTO dto = new UploadFileDTO("electra.log", file, "P01", "jperez", "contraseña", "es");

        Map<String, MultipartBody.Part> parts = byName(dto.toParts());

        assertEquals(List.of("file", "plzs_id", "userName", "password", "language"), List.copyOf(parts.keySet()));
        assertTrue(parts.get("file").headers().get("Content-Disposition").contains("filename=\"electra.log\""));
        assertEquals("P01", text(parts.get("plzs_id")));
        assertEquals("jperez", text(parts.get("userName")));
        assertEquals("contraseña", text(parts.get("password")));
        assertEquals("es", text(parts.get("language")));
    }

    @Test
    public void sinPlaza_omiteSuParte() {
        UploadFileDTO dto = new UploadFileDTO("electra.log", file, null, "jperez", "secreta", "es");

        assertFalse(byName(dto.toParts()).containsKey("plzs_id"));
    }

    @Test
    public void toStringNoExponeLaContrasena() {
        UploadFileDTO dto = new UploadFileDTO("electra.log", file, "P01", "jperez", "secreta", "es");

        assertFalse(dto.toString().contains("secreta"));
    }

    private static Map<String, MultipartBody.Part> byName(List<MultipartBody.Part> parts) {
        Map<String, MultipartBody.Part> byName = new LinkedHashMap<>();
        for (MultipartBody.Part part : parts) {
            Matcher matcher = PART_NAME.matcher(part.headers().get("Content-Disposition"));
            assertTrue(matcher.find());
            byName.put(matcher.group(1), part);
        }
        return byName;
    }

    private static String text(MultipartBody.Part part) throws IOException {
        Buffer buffer = new Buffer();
        part.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
