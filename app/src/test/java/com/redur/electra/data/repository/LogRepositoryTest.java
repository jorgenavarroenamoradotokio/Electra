package com.redur.electra.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LogRepositoryTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void sinCarpetaDeLogs_noHayFichero() {
        LogRepository repository = new LogRepository(() -> null);

        assertNull(repository.findCurrentLogFile());
    }

    @Test
    public void carpetaVacia_noHayFichero() throws IOException {
        LogRepository repository = new LogRepository(tmp.newFolder("logs")::getAbsoluteFile);

        assertNull(repository.findCurrentLogFile());
    }

    @Test
    public void devuelveElLogModificadoMasRecientemente() throws IOException {
        File dir = tmp.newFolder("logs");
        File older = write(dir, "electra_2026-09-27.log", 1_000L);
        File newer = write(dir, "electra_2026-09-28.log", 2_000L);
        write(dir, "notas.txt", 3_000L);

        File current = new LogRepository(() -> dir).findCurrentLogFile();

        assertEquals(newer, current);
        assertTrue(older.exists());
    }

    @Test
    public void ignoraLosLogsVacios() throws IOException {
        File dir = tmp.newFolder("logs");
        File withContent = write(dir, "electra_2026-09-27.log", 1_000L);
        File empty = new File(dir, "electra_2026-09-28.log");
        assertTrue(empty.createNewFile());
        assertTrue(empty.setLastModified(2_000L));

        assertEquals(withContent, new LogRepository(() -> dir).findCurrentLogFile());
    }

    private static File write(File dir, String name, long lastModified) throws IOException {
        File file = new File(dir, name);
        Files.write(file.toPath(), "linea".getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(lastModified));
        return file;
    }
}
