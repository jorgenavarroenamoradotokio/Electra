package com.redur.electra.core.log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Set;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Log HTTP que nunca vuelca cuerpos de endpoints sensibles (credenciales, datos de usuario).
 * Para esas rutas registra solo línea de petición y cabeceras; para el resto, cuerpo completo.
 * Las cabeceras de autenticación se redactan siempre.
 */
public final class SensitiveAwareHttpLoggingInterceptor implements Interceptor {

    private static final String[] REDACTED_HEADERS = {"Authorization", "Cookie", "Set-Cookie"};

    private final HttpLoggingInterceptor bodyLogging;
    private final HttpLoggingInterceptor headersOnlyLogging;
    private final Set<String> sensitivePaths;

    /**
     * @param sensitivePaths rutas relativas (tal como aparecen en la anotación Retrofit, p. ej.
     *                       {@code "ElectraWS/mobile/login/"}) cuyos cuerpos no se deben registrar.
     */
    public SensitiveAwareHttpLoggingInterceptor(@NonNull HttpLoggingInterceptor.Logger logger,
                                                @NonNull Set<String> sensitivePaths) {
        this.bodyLogging = create(logger, HttpLoggingInterceptor.Level.BODY);
        this.headersOnlyLogging = create(logger, HttpLoggingInterceptor.Level.HEADERS);
        this.sensitivePaths = Set.copyOf(sensitivePaths);
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        HttpLoggingInterceptor delegate = isSensitive(chain.request().url()) ? headersOnlyLogging : bodyLogging;
        return delegate.intercept(chain);
    }

    private boolean isSensitive(HttpUrl url) {
        String path = trimSlashes(url.encodedPath());
        for (String sensitivePath : sensitivePaths) {
            String normalized = trimSlashes(sensitivePath);
            if (path.equals(normalized) || path.endsWith("/" + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String trimSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }

    private static HttpLoggingInterceptor create(HttpLoggingInterceptor.Logger logger, HttpLoggingInterceptor.Level level) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(logger);
        interceptor.setLevel(level);
        for (String header : REDACTED_HEADERS) {
            interceptor.redactHeader(header);
        }
        return interceptor;
    }
}
