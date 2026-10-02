package com.redur.electra.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class PhotoRepositoryTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void creaLaCarpetaYUnFicheroVacioDistintoEnCadaCaptura() throws IOException {
        File dir = new File(tmp.getRoot(), PhotoRepository.PHOTO_DIR);
        PhotoRepository repository = new PhotoRepository(() -> dir);

        File first = repository.createCaptureFile();
        File second = repository.createCaptureFile();

        assertEquals(dir, first.getParentFile());
        assertEquals(0, first.length());
        assertNotEquals(first, second);
    }

    @Test(expected = IOException.class)
    public void siNoPuedeCrearLaCarpeta_fallaConIOException() throws IOException {
        // Un fichero ocupa la ruta de la carpeta
        File blocker = tmp.newFile(PhotoRepository.PHOTO_DIR);

        new PhotoRepository(() -> blocker).createCaptureFile();
    }

    @Test
    public void soloHayFotoSiLaCamaraEscribioAlgo() throws IOException {
        PhotoRepository repository = new PhotoRepository(() -> tmp.getRoot());
        File file = repository.createCaptureFile();

        assertFalse(repository.hasPhoto(file));

        Files.write(file.toPath(), new byte[]{1, 2, 3});

        assertTrue(repository.hasPhoto(file));
    }

    @Test
    public void descartarBorraElFichero_yNoFallaSiYaNoExiste() throws IOException {
        PhotoRepository repository = new PhotoRepository(() -> tmp.getRoot());
        File file = repository.createCaptureFile();

        repository.discard(file);
        repository.discard(file);

        assertFalse(file.exists());
    }
}
