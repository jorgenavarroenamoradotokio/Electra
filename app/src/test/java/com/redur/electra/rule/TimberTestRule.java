package com.redur.electra.rule;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.rules.ExternalResource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * Regla JUnit que captura los logs de Timber durante un test.
 * Uso:
 * <pre>
 * &#64;Rule public final TimberTestRule timber = new TimberTestRule();
 *
 * &#64;Test public void registraError() {
 *     sut.hacerAlgo();
 *     assertTrue(timber.contains(Log.ERROR, "Error enviando bulto"));
 * }
 * </pre>
 */
public class TimberTestRule extends ExternalResource {

    public record Entry(int priority, @Nullable String tag, String message, @Nullable Throwable throwable) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    private final Timber.Tree tree = new Timber.Tree() {
        @Override
        protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
            entries.add(new Entry(priority, tag, message, t));
        }
    };

    @Override
    protected void before() {
        Timber.plant(tree);
    }

    @Override
    protected void after() {
        Timber.uproot(tree);
        entries.clear();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean contains(int priority, String fragment) {
        return entries.stream().anyMatch(e -> e.priority() == priority && e.message().contains(fragment));
    }
}
