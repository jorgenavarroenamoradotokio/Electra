package com.redur.electra.core.permission;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.os.Build;

import org.junit.Test;

import java.util.Map;

public class AppPermissionTest {

    @Test
    public void camara_pideElPermisoDeCamaraEnCualquierVersion() {
        assertArrayEquals(new String[]{Manifest.permission.CAMERA},
                AppPermission.CAMERA.manifestPermissions(Build.VERSION_CODES.Q));
        assertArrayEquals(new String[]{Manifest.permission.CAMERA},
                AppPermission.CAMERA.manifestPermissions(Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }

    @Test
    public void galeria_hastaAndroid12_pideLeerAlmacenamiento() {
        assertArrayEquals(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                AppPermission.GALLERY.manifestPermissions(Build.VERSION_CODES.S_V2));
    }

    @Test
    public void galeria_enAndroid13_pideLeerImagenes() {
        assertArrayEquals(new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                AppPermission.GALLERY.manifestPermissions(Build.VERSION_CODES.TIRAMISU));
    }

    @Test
    public void galeria_desdeAndroid14_admiteTambienElAccesoParcial() {
        assertArrayEquals(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},
                AppPermission.GALLERY.manifestPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
    }

    @Test
    public void elResultadoIdentificaElPermisoPedido() {
        assertEquals(AppPermission.CAMERA,
                AppPermission.fromResults(Map.of(Manifest.permission.CAMERA, false)));
        assertNull(AppPermission.fromResults(Map.of()));
    }

    @Test
    public void concedidoSoloSiElSistemaLoConcede() {
        assertTrue(AppPermission.CAMERA.isGrantedIn(Map.of(Manifest.permission.CAMERA, true)));
        assertFalse(AppPermission.CAMERA.isGrantedIn(Map.of(Manifest.permission.CAMERA, false)));
        assertFalse(AppPermission.CAMERA.isGrantedIn(Map.of()));
    }
}
