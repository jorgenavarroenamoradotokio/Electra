package com.redur.electra.core.permission;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PermissionStatusTest {

    @Test
    public void concedido_daIgualSiSePuedeVolverAPreguntar() {
        assertEquals(PermissionStatus.GRANTED, PermissionStatus.from(true, true));
        assertEquals(PermissionStatus.GRANTED, PermissionStatus.from(true, false));
    }

    @Test
    public void denegadoPeroElSistemaVolveraAPreguntar_esUnaDenegacionNormal() {
        assertEquals(PermissionStatus.DENIED, PermissionStatus.from(false, true));
    }

    @Test
    public void denegadoYElSistemaYaNoPregunta_esDenegadoParaSiempre() {
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, PermissionStatus.from(false, false));
    }
}
