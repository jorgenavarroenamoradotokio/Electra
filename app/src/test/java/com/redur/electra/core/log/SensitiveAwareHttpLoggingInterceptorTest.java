package com.redur.electra.core.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.redur.electra.data.remote.api.LoginApiService;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SensitiveAwareHttpLoggingInterceptorTest {

    private static final String BASE_URL = "https://electra.example.com/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String REQUEST_SECRET = "s3cr3t-password";
    private static final String RESPONSE_SECRET = "Nombre Apellido";

    private final List<String> logged = new ArrayList<>();
    private OkHttpClient client;

    @Before
    public void setUp() {
        client = new OkHttpClient.Builder()
                .addInterceptor(new SensitiveAwareHttpLoggingInterceptor(logged::add, Set.of(LoginApiService.LOGIN_PATH)))
                // Respuesta local: sin red
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Set-Cookie", "JSESSIONID=abc123")
                        .body(ResponseBody.create("{\"nombre\":\"" + RESPONSE_SECRET + "\"}", JSON))
                        .build())
                .build();
    }

    @Test
    public void loginRequest_doesNotLogRequestOrResponseBody() throws IOException {
        execute(BASE_URL + LoginApiService.LOGIN_PATH);

        assertFalse(loggedContains(REQUEST_SECRET));
        assertFalse(loggedContains(RESPONSE_SECRET));
        assertTrue(loggedContains("--> POST " + BASE_URL + LoginApiService.LOGIN_PATH));
    }

    @Test
    public void loginRequest_withoutTrailingSlash_isAlsoTreatedAsSensitive() throws IOException {
        execute(BASE_URL + "ElectraWS/mobile/login");

        assertFalse(loggedContains(REQUEST_SECRET));
        assertFalse(loggedContains(RESPONSE_SECRET));
    }

    @Test
    public void nonSensitiveRequest_logsBodies() throws IOException {
        execute(BASE_URL + "ElectraWS/mobile/bultos/");

        assertTrue(loggedContains(REQUEST_SECRET));
        assertTrue(loggedContains(RESPONSE_SECRET));
    }

    @Test
    public void similarPathPrefix_isNotTreatedAsSensitive() throws IOException {
        execute(BASE_URL + "ElectraWS/mobile/login-help/");

        assertTrue(loggedContains(REQUEST_SECRET));
    }

    @Test
    public void authHeaders_areAlwaysRedacted() throws IOException {
        client.newCall(new Request.Builder()
                .url(BASE_URL + "ElectraWS/mobile/bultos/")
                .header("Authorization", "Bearer token-value")
                .header("Cookie", "JSESSIONID=abc123")
                .build()).execute().close();

        assertFalse(loggedContains("token-value"));
        assertFalse(loggedContains("abc123"));
    }

    private void execute(String url) throws IOException {
        RequestBody body = RequestBody.create("{\"password\":\"" + REQUEST_SECRET + "\"}", JSON);
        client.newCall(new Request.Builder().url(url).post(body).build()).execute().close();
    }

    private boolean loggedContains(String text) {
        return logged.stream().anyMatch(line -> line.contains(text));
    }
}
