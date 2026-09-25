package com.redur.electra.core.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import com.redur.electra.rule.TimberTestRule;
import com.redur.electra.util.MutableClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class FileLoggingTreeTest {

    private static final long FLUSH_MS = 2000;
    private static final long LARGE = 1024 * 1024;
    private static final long NO_TOTAL_LIMIT = Long.MAX_VALUE;

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Captura lo que pasa por Timber durante el test (tests de integración).
     */
    @Rule
    public final TimberTestRule timber = new TimberTestRule();

    private File dir;
    private MutableClock clock;
    private final List<FileLoggingTree> trees = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        dir = tmp.newFolder("logs");
        clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
    }

    @After
    public void tearDown() {
        trees.forEach(FileLoggingTree::shutdown);
    }

    // ---------- Formato y filtrado ----------

    @Test
    public void escribeLineaConFormatoEsperado() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);

        tree.log(Log.INFO, "Scanner", "Código leído 123", null);
        tree.flushBlocking(FLUSH_MS);

        List<String> lines = readLines();
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line, line.startsWith("2026-09-24 10:00:00.000 I/Scanner ["));
        assertTrue(line, line.endsWith("]: Código leído 123"));
    }

    @Test
    public void descartaMensajesPorDebajoDelNivelMinimo() throws IOException {
        FileLoggingTree tree = newTree(Log.INFO, LARGE);

        // API pública: pasa por isLoggable()
        tree.d("mensaje debug");
        tree.i("mensaje info");
        tree.flushBlocking(FLUSH_MS);

        String content = readAll("test_2026-09-24.log");
        assertFalse(content.contains("mensaje debug"));
        assertTrue(content.contains("mensaje info"));
    }

    @Test
    public void incluyeStackTraceDeLaExcepcion() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);

        tree.e(new IllegalStateException("boom"), "Fallo enviando bulto %s", "B-42");
        tree.flushBlocking(FLUSH_MS);

        String content = readAll("test_2026-09-24.log");
        assertTrue(content.contains("Fallo enviando bulto B-42"));
        assertTrue(content.contains("java.lang.IllegalStateException: boom"));
    }

    // ---------- Persistencia y rotación ----------

    @Test
    public void anadeAlFicheroExistenteTrasReiniciar() throws IOException {
        FileLoggingTree first = newTree(Log.VERBOSE, LARGE);
        first.log(Log.INFO, "T", "antes", null);
        first.flushBlocking(FLUSH_MS);
        first.shutdown();

        FileLoggingTree second = newTree(Log.VERBOSE, LARGE);
        second.log(Log.INFO, "T", "despues", null);
        second.flushBlocking(FLUSH_MS);

        List<String> lines = readLines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("antes"));
        assertTrue(lines.get(1).endsWith("despues"));
    }

    @Test
    public void rotaAlSuperarElTamanoMaximo() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, 200);
        String payload = "x".repeat(120);

        for (int i = 0; i < 5; i++) {
            tree.log(Log.INFO, "T", i + payload, null);
        }
        tree.flushBlocking(FLUSH_MS);

        File[] files = logFiles();
        assertTrue("Esperaba varios ficheros, hay " + files.length, files.length > 1);
        assertTrue(new File(dir, "test_2026-09-24_1.log").exists());

        int total = 0;
        for (File f : files) {
            total += Files.readAllLines(f.toPath(), StandardCharsets.UTF_8).size();
        }
        assertEquals("No se debe perder ninguna línea al rotar", 5, total);
    }

    @Test
    public void rotaAlCambiarDeDia() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);

        tree.log(Log.INFO, "T", "dia 1", null);
        tree.flushBlocking(FLUSH_MS);
        clock.advance(Duration.ofDays(1));
        tree.log(Log.INFO, "T", "dia 2", null);
        tree.flushBlocking(FLUSH_MS);

        assertTrue(readAll("test_2026-09-24.log").contains("dia 1"));
        assertTrue(readAll("test_2026-09-25.log").contains("dia 2"));
    }

    // ---------- Retención ----------

    @Test
    public void purgaFicherosMasAntiguosQueLaRetencion() throws IOException {
        File old = createLogFile("test_2026-09-10.log", Duration.ofDays(8));
        File recent = createLogFile("test_2026-09-23.log", Duration.ofDays(1));
        File other = createFile("notas.txt", Duration.ofDays(30));

        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE); // la purga se lanza en el constructor
        tree.flushBlocking(FLUSH_MS);

        assertFalse("Debe borrar el log antiguo", old.exists());
        assertTrue("Debe conservar el log reciente", recent.exists());
        assertTrue("No debe tocar ficheros que no son .log", other.exists());
    }

    @Test
    public void purgaLosMasAntiguosAlSuperarElTamanoTotal() throws IOException {
        File oldest = createLogFile("test_2026-09-21.log", Duration.ofDays(3), 400);
        File middle = createLogFile("test_2026-09-22.log", Duration.ofDays(2), 400);
        File newest = createLogFile("test_2026-09-23.log", Duration.ofDays(1), 400);

        // 1200 bytes en disco con un tope de 1000: sobra con borrar el más antiguo
        FileLoggingTree tree = track(new FileLoggingTree(
                dir, Log.VERBOSE, "test_", 500, 7, 1000, clock));
        tree.flushBlocking(FLUSH_MS);

        assertFalse("Debe borrar el más antiguo", oldest.exists());
        assertTrue(middle.exists());
        assertTrue(newest.exists());
    }

    @Test
    public void purgaTambienAlRotarDeDiaSinReiniciar() throws IOException {
        // A 6 días todavía está dentro de la retención al arrancar
        File almostExpired = createLogFile("test_2026-09-18.log", Duration.ofDays(6));
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);
        tree.log(Log.INFO, "T", "primer dia", null); // deja el writer abierto
        tree.flushBlocking(FLUSH_MS);
        assertTrue(almostExpired.exists());

        // El proceso sigue vivo dos días más: la rotación diaria debe purgarlo
        clock.advance(Duration.ofDays(2));
        tree.log(Log.INFO, "T", "nuevo dia", null);
        tree.flushBlocking(FLUSH_MS);

        assertFalse(almostExpired.exists());
        assertTrue(readAll("test_2026-09-26.log").contains("nuevo dia"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaTopeTotalMenorQueElTamanoDeFichero() {
        track(new FileLoggingTree(dir, Log.VERBOSE, "test_", 1000, 7, 999, clock));
    }

    // ---------- Integración con Timber ----------
    @Test
    public void recibeLogsViaTimberConTagExplicito() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);

        Timber.plant(tree);
        try {
            Timber.tag("Scanner").i("Código leído %s", "123");
            tree.flushBlocking(FLUSH_MS);
        } finally {
            Timber.uproot(tree); // Timber es global: no dejar el árbol plantado para otros tests
        }

        String content = readAll("test_2026-09-24.log");
        assertTrue(content, content.contains(" I/Scanner ["));
        assertTrue(content, content.contains("]: Código leído 123"));
        // El mismo log llega al resto de árboles plantados
        assertTrue(timber.contains(Log.INFO, "Código leído 123"));
    }

    @Test
    public void nivelMinimoTambienFiltraViaTimber() throws IOException {
        FileLoggingTree tree = newTree(Log.WARN, LARGE);

        Timber.plant(tree);
        try {
            Timber.i("solo en memoria");
            Timber.w("tambien en fichero");
            tree.flushBlocking(FLUSH_MS);
        } finally {
            Timber.uproot(tree);
        }

        String content = readAll("test_2026-09-24.log");
        assertFalse(content.contains("solo en memoria"));
        assertTrue(content.contains("tambien en fichero"));
        // El filtro es propio de FileLoggingTree: Timber sí entrega ambos al árbol de test
        assertTrue(timber.contains(Log.INFO, "solo en memoria"));
    }

    // ---------- Robustez ----------

    @Test
    public void errorDeEscrituraNoLanzaExcepcionNiSeReenviaATimber() throws IOException {
        // Un fichero en lugar de un directorio: mkdirs() falla al escribir
        File notADir = tmp.newFile("no_soy_directorio");
        FileLoggingTree tree = track(new FileLoggingTree(
                notADir, Log.VERBOSE, "test_", LARGE, 7, NO_TOTAL_LIMIT, clock));

        Timber.plant(tree);
        try {
            Timber.tag("T").e("provoca fallo de escritura");
            tree.flushBlocking(FLUSH_MS);
        } finally {
            Timber.uproot(tree);
        }

        // Si FileLoggingTree registrara sus errores vía Timber habría más de una entrada
        // (y en producción, recursión infinita). Solo debe estar el log original.
        assertEquals(1, timber.entries().size());
        assertEquals("provoca fallo de escritura", timber.entries().get(0).message());
    }

    @Test
    public void ignoraLogsTrasShutdown() throws IOException {
        FileLoggingTree tree = newTree(Log.VERBOSE, LARGE);
        tree.log(Log.INFO, "T", "antes", null);
        tree.shutdown();

        tree.log(Log.INFO, "T", "despues", null); // no debe lanzar RejectedExecutionException

        String content = readAll("test_2026-09-24.log");
        assertTrue(content.contains("antes"));
        assertFalse(content.contains("despues"));
    }

    // ---------- Helpers ----------

    private FileLoggingTree newTree(int minPriority, long maxBytes) {
        return track(new FileLoggingTree(dir, minPriority, "test_", maxBytes, 7, NO_TOTAL_LIMIT, clock));
    }

    private FileLoggingTree track(FileLoggingTree tree) {
        trees.add(tree);
        return tree;
    }

    private List<String> readLines() throws IOException {
        return Files.readAllLines(new File(dir, "test_2026-09-24.log").toPath(), StandardCharsets.UTF_8);
    }

    private String readAll(String name) throws IOException {
        return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
    }

    private File[] logFiles() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".log"));
        return files != null ? files : new File[0];
    }

    private File createLogFile(String name, Duration age) throws IOException {
        return createFile(name, age);
    }

    private File createLogFile(String name, Duration age, int sizeBytes) throws IOException {
        return writeFile(name, age, "x".repeat(sizeBytes).getBytes(StandardCharsets.UTF_8));
    }

    private File createFile(String name, Duration age) throws IOException {
        return writeFile(name, age, "contenido\n".getBytes(StandardCharsets.UTF_8));
    }

    private File writeFile(String name, Duration age, byte[] content) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), content);
        assertTrue(f.setLastModified(clock.millis() - age.toMillis()));
        return f;
    }
}