package com.redur.electra.data.remote.dto.request.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

/** Las claves JSON son el contrato con el backend: si no coinciden, el campo llega vacío. */
public class PlaceRequestDTOTest {

    private final Gson gson = new Gson();

    @Test
    public void cambioDePlazaEnviaUsuarioYPlazaConLasClavesDelBackend() {
        JsonObject json = gson.toJsonTree(
                new ChangePlaceRequestDTO("jperez", "secreto", "es", "P02")).getAsJsonObject();

        assertEquals("jperez", json.get("userName").getAsString());
        assertEquals("P02", json.get("plzs_id").getAsString());
        assertFalse(json.has("username"));
        assertFalse(json.has("plzsId"));
    }

    @Test
    public void listadoDePlazasEnviaUsuarioConLaClaveDelBackend() {
        JsonObject json = gson.toJsonTree(
                new PlaceListRequestDTO("jperez", "secreto", "es")).getAsJsonObject();

        assertEquals("jperez", json.get("userName").getAsString());
        assertFalse(json.has("username"));
    }
}
