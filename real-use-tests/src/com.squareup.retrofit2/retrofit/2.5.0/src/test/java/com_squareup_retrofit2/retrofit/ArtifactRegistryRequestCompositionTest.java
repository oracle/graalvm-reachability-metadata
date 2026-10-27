/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.OPTIONS;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryName;

/**
 * Request-line and header composition from parameter annotations: base-URL resolution of relative
 * and absolute paths, `@Path` and `@Query` encoding, repeated and flag query parameters,
 * multi-valued and mapped headers, and the `@HEAD`, `@OPTIONS`, and custom `@HTTP` verbs.
 *
 * An artifact-registry client looks up packages whose coordinates contain spaces, slashes, and
 * reserved characters, probes artifacts without downloading them, and bulk-deletes snapshots with
 * a request body on `DELETE`. The registry is a `MockWebServer`; every assertion reads the exact
 * request line and headers Retrofit put on the wire.
 */
public class ArtifactRegistryRequestCompositionTest {

    private MockWebServer server;
    private RegistryApi api;

    @BeforeEach
    void startRegistry() throws IOException {
        server = new MockWebServer();
        server.start();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/registry/v2/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(RegistryApi.class);
    }

    @AfterEach
    void stopRegistry() throws IOException {
        server.shutdown();
    }

    @Test
    void resolvesRelativePathsAgainstTheBaseUrlAndAbsolutePathsAgainstTheHost() throws Exception {
        server.enqueue(json("""
                {"group": "org.acme", "name": "core", "versions": ["1.0", "1.1"]}
                """));
        server.enqueue(new MockResponse().setBody("UP"));

        PackageInfo info = api.packageInfo("org.acme", "core").execute().body();
        Response<ResponseBody> health = api.health().execute();

        assertNotNull(info);
        assertEquals(List.of("1.0", "1.1"), info.versions);
        try (ResponseBody body = health.body()) {
            assertNotNull(body);
            assertEquals("UP", body.string());
        }
        assertEquals("/registry/v2/packages/org.acme/core", take().getPath());
        assertEquals("/health", take().getPath());
    }

    @Test
    void encodesPathSegmentsUnlessTheyAreDeclaredAlreadyEncoded() throws Exception {
        server.enqueue(json("{\"group\": \"team tools\", \"name\": \"c#-sdk\", \"versions\": []}"));
        server.enqueue(new MockResponse().setBody("# Readme"));

        api.packageInfo("team tools", "c#-sdk").execute();
        api.file("team-tools", "docs/guide%20v2.md").execute().body().close();

        assertEquals("/registry/v2/packages/team%20tools/c%23-sdk", take().getPath());
        assertEquals("/registry/v2/files/team-tools/docs/guide%20v2.md", take().getPath());
    }

    @Test
    void expandsRepeatedFlagAndOptionalQueryParameters() throws Exception {
        server.enqueue(json("[]"));
        server.enqueue(json("[]"));

        api.search("json & yaml", List.of("java", "kotlin"), new int[] {11, 17}, null, Sort.DOWNLOADS,
                List.of("pretty", "explain")).execute();
        api.search("c++", List.of(), new int[0], 2, null, List.of()).execute();

        assertEquals(
                "/registry/v2/search?q=json%20%26%20yaml&lang=java&lang=kotlin&jdk=11&jdk=17"
                        + "&sort=downloads&pretty&explain",
                take().getPath());
        assertEquals("/registry/v2/search?q=c%2B%2B&page=2", take().getPath());
    }

    @Test
    void keepsPreEncodedQueryValuesVerbatim() throws Exception {
        server.enqueue(json("[]"));

        api.searchRaw("group%3Aorg.acme+name%3Acore").execute();

        assertEquals("/registry/v2/search?q=group%3Aorg.acme+name%3Acore", take().getPath());
    }

    @Test
    void sendsMultiValuedAndMappedHeaders() throws Exception {
        server.enqueue(json("{\"group\": \"org.acme\", \"name\": \"core\", \"versions\": [\"2.0\"]}"));
        Map<String, String> context = new LinkedHashMap<>();
        context.put("X-Tenant", "acme");
        context.put("X-Trace-Id", "trace-77");

        Response<PackageInfo> response = api.packageInfoAs(
                "org.acme", "core", List.of("application/json", "text/plain;q=0.5"), context).execute();

        assertTrue(response.isSuccessful());
        RecordedRequest request = take();
        assertEquals(List.of("application/json", "text/plain;q=0.5"), request.getHeaders().values("Accept"));
        assertEquals("acme", request.getHeader("X-Tenant"));
        assertEquals("trace-77", request.getHeader("X-Trace-Id"));
    }

    @Test
    void probesAnArtifactWithHeadAndDiscoversVerbsWithOptions() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Length", "52428800")
                .addHeader("ETag", "\"sha256-abc\""));
        server.enqueue(new MockResponse().addHeader("Allow", "GET, HEAD, DELETE"));

        Response<Void> probe = api.probe("org.acme", "core", "1.1").execute();
        Response<ResponseBody> options = api.capabilities().execute();

        assertEquals(200, probe.code());
        assertNull(probe.body());
        assertEquals("\"sha256-abc\"", probe.headers().get("ETag"));
        assertEquals("52428800", probe.headers().get("Content-Length"));
        assertEquals("GET, HEAD, DELETE", options.headers().get("Allow"));
        options.body().close();

        RecordedRequest head = take();
        assertEquals("HEAD", head.getMethod());
        assertEquals("/registry/v2/packages/org.acme/core/1.1/artifact", head.getPath());
        RecordedRequest discovery = take();
        assertEquals("OPTIONS", discovery.getMethod());
        assertEquals("/registry/v2/packages", discovery.getPath());
    }

    @Test
    void bulkDeletesSnapshotsWithABodyOnACustomHttpVerb() throws Exception {
        server.enqueue(json("{\"deleted\": 2}"));
        PurgeRequest purge = new PurgeRequest();
        purge.versions = List.of("1.2-SNAPSHOT", "1.3-SNAPSHOT");

        PurgeResult result = api.purge("org.acme", "core", purge).execute().body();

        assertNotNull(result);
        assertEquals(2, result.deleted);
        RecordedRequest request = take();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/registry/v2/packages/org.acme/core/versions", request.getPath());
        assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"));
        assertEquals("{\"versions\":[\"1.2-SNAPSHOT\",\"1.3-SNAPSHOT\"]}", request.getBody().readUtf8());
    }

    private RecordedRequest take() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private static MockResponse json(String body) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(body);
    }

    /// The registry's HTTP surface; every method exercises a different part of request building.
    interface RegistryApi {

        @GET("packages/{group}/{name}")
        Call<PackageInfo> packageInfo(@Path("group") String group, @Path("name") String name);

        @GET("packages/{group}/{name}")
        Call<PackageInfo> packageInfoAs(
                @Path("group") String group,
                @Path("name") String name,
                @Header("Accept") List<String> accept,
                @HeaderMap Map<String, String> context);

        @GET("/health")
        Call<ResponseBody> health();

        @GET("files/{group}/{path}")
        Call<ResponseBody> file(@Path("group") String group, @Path(value = "path", encoded = true) String path);

        @GET("search")
        Call<List<PackageInfo>> search(
                @Query("q") String query,
                @Query("lang") List<String> languages,
                @Query("jdk") int[] jdks,
                @Query("page") Integer page,
                @Query("sort") Sort sort,
                @QueryName List<String> flags);

        @GET("search")
        Call<List<PackageInfo>> searchRaw(@Query(value = "q", encoded = true) String query);

        @HEAD("packages/{group}/{name}/{version}/artifact")
        Call<Void> probe(@Path("group") String group, @Path("name") String name, @Path("version") String version);

        @OPTIONS("packages")
        Call<ResponseBody> capabilities();

        @HTTP(method = "DELETE", path = "packages/{group}/{name}/versions", hasBody = true)
        Call<PurgeResult> purge(@Path("group") String group, @Path("name") String name, @Body PurgeRequest request);
    }

    /// Sort orders; Retrofit renders query values through `toString()`.
    enum Sort {
        DOWNLOADS, RECENT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final class PackageInfo {
        String group;
        String name;
        List<String> versions;
    }

    static final class PurgeRequest {
        List<String> versions;
    }

    static final class PurgeResult {
        int deleted;
    }
}
