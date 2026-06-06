/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_web_client;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.CachingWebClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.multipart.MultipartForm;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Vertx_web_clientTest {
    private static final String HOST = "127.0.0.1";
    private static final int TIMEOUT_SECONDS = 10;

    @Test
    void getRequestSendsHeadersQueryParametersAndDecodesJsonBody() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        WebClient client = null;
        try {
            server = startServer(vertx, request -> {
                JsonObject payload = new JsonObject()
                        .put("method", request.method().name())
                        .put("path", request.path())
                        .put("query", request.query())
                        .put("clientHeader", request.getHeader("X-Client-Test"));
                request.response()
                        .putHeader("content-type", "application/json")
                        .putHeader("x-server-test", "json-response")
                        .end(payload.encode());
            });
            client = createClient(vertx, server.actualPort());

            HttpResponse<JsonObject> response = await(client.get("/search")
                    .addQueryParam("q", "native-image")
                    .putHeader("X-Client-Test", "web-client")
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_OK)
                    .expect(ResponsePredicate.JSON)
                    .send());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.getHeader("x-server-test")).isEqualTo("json-response");
            JsonObject responseBody = response.body();
            assertThat(responseBody.getString("method")).isEqualTo("GET");
            assertThat(responseBody.getString("path")).isEqualTo("/search");
            assertThat(responseBody.getString("query")).isEqualTo("q=native-image");
            assertThat(responseBody.getString("clientHeader")).isEqualTo("web-client");
        } finally {
            close(client, server, vertx);
        }
    }

    @Test
    void postJsonObjectUsesBasicAuthenticationAndMapsJsonResponse() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        WebClient client = null;
        try {
            server = startServer(vertx, request -> request.body().onComplete(bodyResult -> {
                if (bodyResult.failed()) {
                    request.response().setStatusCode(500).end(bodyResult.cause().getMessage());
                    return;
                }
                JsonObject requestBody = bodyResult.result().toJsonObject();
                JsonObject responseBody = new JsonObject()
                        .put("contentType", request.getHeader("content-type"))
                        .put("authorization", request.getHeader("authorization"))
                        .put("message", requestBody.getString("message"))
                        .put("count", requestBody.getInteger("count"));
                request.response()
                        .putHeader("content-type", "application/json")
                        .end(responseBody.encode());
            }));
            client = createClient(vertx, server.actualPort());

            JsonObject requestBody = new JsonObject()
                    .put("message", "hello")
                    .put("count", 3);
            HttpResponse<JsonObject> response = await(client.post("/json")
                    .basicAuthentication("tester", "secret")
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_SUCCESS)
                    .expect(ResponsePredicate.contentType("application/json"))
                    .sendJsonObject(requestBody));

            String credentials = Base64.getEncoder()
                    .encodeToString("tester:secret".getBytes(StandardCharsets.UTF_8));
            JsonObject responseBody = response.body();
            assertThat(responseBody.getString("contentType")).startsWith("application/json");
            assertThat(responseBody.getString("authorization")).isEqualTo("Basic " + credentials);
            assertThat(responseBody.getString("message")).isEqualTo("hello");
            assertThat(responseBody.getInteger("count")).isEqualTo(3);
        } finally {
            close(client, server, vertx);
        }
    }

    @Test
    void sendsUrlEncodedFormsAndMultipartUploads() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        WebClient client = null;
        Path upload = null;
        try {
            server = startServer(vertx, request -> request.body().onComplete(bodyResult -> {
                if (bodyResult.failed()) {
                    request.response().setStatusCode(500).end(bodyResult.cause().getMessage());
                    return;
                }
                String contentType = request.getHeader("content-type");
                String body = bodyResult.result().toString(StandardCharsets.UTF_8.name());
                JsonObject responseBody = new JsonObject()
                        .put("contentType", contentType)
                        .put("body", body);
                request.response().putHeader("content-type", "application/json").end(responseBody.encode());
            }));
            client = createClient(vertx, server.actualPort());

            MultiMap form = MultiMap.caseInsensitiveMultiMap()
                    .add("name", "Ada Lovelace")
                    .add("project", "native-image");
            HttpResponse<JsonObject> formResponse = await(client.post("/form")
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_OK)
                    .sendForm(form));
            assertThat(formResponse.body().getString("contentType"))
                    .startsWith("application/x-www-form-urlencoded");
            assertThat(formResponse.body().getString("body"))
                    .contains("name=Ada+Lovelace")
                    .contains("project=native-image");

            upload = Files.createTempFile("vertx-web-client-upload", ".txt");
            Files.writeString(upload, "file-body", StandardCharsets.UTF_8);
            MultipartForm multipartForm = MultipartForm.create()
                    .attribute("description", "metadata test")
                    .textFileUpload("attachment", "sample.txt", upload.toString(), "text/plain");
            HttpResponse<JsonObject> multipartResponse = await(client.post("/multipart")
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_OK)
                    .sendMultipartForm(multipartForm));

            assertThat(multipartResponse.body().getString("contentType"))
                    .startsWith("multipart/form-data");
            assertThat(multipartResponse.body().getString("body"))
                    .contains("name=\"description\"")
                    .contains("metadata test")
                    .contains("name=\"attachment\"")
                    .contains("filename=\"sample.txt\"")
                    .contains("file-body");
        } finally {
            if (upload != null) {
                Files.deleteIfExists(upload);
            }
            close(client, server, vertx);
        }
    }

    @Test
    void cachingClientServesFreshGetResponsesFromCache() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        WebClient client = null;
        AtomicInteger requestCount = new AtomicInteger();
        try {
            server = startServer(vertx, request -> {
                int currentRequest = requestCount.incrementAndGet();
                request.response()
                        .putHeader("cache-control", "public, max-age=60")
                        .putHeader("etag", "\"cached-resource\"")
                        .putHeader("content-type", "text/plain")
                        .end("response-" + currentRequest);
            });
            client = CachingWebClient.create(createClient(vertx, server.actualPort()));

            HttpResponse<Buffer> firstResponse = await(client.get("/cached-resource")
                    .as(BodyCodec.buffer())
                    .expect(ResponsePredicate.SC_OK)
                    .send());
            HttpResponse<Buffer> secondResponse = await(client.get("/cached-resource")
                    .as(BodyCodec.buffer())
                    .expect(ResponsePredicate.SC_OK)
                    .send());

            assertThat(firstResponse.bodyAsString()).isEqualTo("response-1");
            assertThat(secondResponse.bodyAsString()).isEqualTo("response-1");
            assertThat(secondResponse.getHeader("age")).isNotNull();
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            close(client, server, vertx);
        }
    }

    @Test
    void sessionPersistsCookiesAndClientFollowsRedirects() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        WebClientSession session = null;
        try {
            server = startServer(vertx, request -> {
                if ("/login".equals(request.path())) {
                    request.response()
                            .putHeader("set-cookie", "sid=abc123; Path=/; HttpOnly")
                            .end("logged-in");
                    return;
                }
                if ("/redirect".equals(request.path())) {
                    request.response()
                            .setStatusCode(302)
                            .putHeader("location", "/profile")
                            .end();
                    return;
                }
                JsonObject body = new JsonObject()
                        .put("cookie", request.getHeader("cookie"))
                        .put("sessionHeader", request.getHeader("X-Session-Header"));
                request.response().putHeader("content-type", "application/json").end(body.encode());
            });
            WebClient baseClient = createClient(vertx, server.actualPort());
            session = WebClientSession.create(baseClient)
                    .addHeader("X-Session-Header", "present");

            HttpResponse<String> loginResponse = await(session.get("/login")
                    .as(BodyCodec.string())
                    .expect(ResponsePredicate.SC_OK)
                    .send());
            assertThat(loginResponse.body()).isEqualTo("logged-in");

            HttpResponse<JsonObject> profileResponse = await(session.get("/redirect")
                    .followRedirects(true)
                    .as(BodyCodec.jsonObject())
                    .expect(ResponsePredicate.SC_OK)
                    .expect(ResponsePredicate.JSON)
                    .send());

            assertThat(profileResponse.followedRedirects()).isNotEmpty();
            assertThat(profileResponse.body().getString("cookie")).contains("sid=abc123");
            assertThat(profileResponse.body().getString("sessionHeader")).isEqualTo("present");
        } finally {
            close(session, server, vertx);
        }
    }

    private static WebClient createClient(Vertx vertx, int port) {
        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(HOST)
                .setDefaultPort(port)
                .setConnectTimeout(2_000)
                .setIdleTimeout(5)
                .setFollowRedirects(false)
                .setUserAgent("graalvm-reachability-metadata-test");
        return WebClient.create(vertx, options);
    }

    private static HttpServer startServer(Vertx vertx, Handler<HttpServerRequest> handler) throws Exception {
        HttpServer server = vertx.createHttpServer().requestHandler(handler);
        return await(server.listen(0, HOST));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void close(WebClient client, HttpServer server, Vertx vertx) throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            await(server.close());
        }
        await(vertx.close());
    }
}
