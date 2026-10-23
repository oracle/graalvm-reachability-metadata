/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.ETag;
import io.undertow.util.Headers;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Static file serving with `ResourceHandler` over a `PathResourceManager`: welcome files and
 * directory redirects, MIME mapping with application overrides, content-hash ETags and
 * `Last-Modified` driving conditional requests, byte-range requests, directory listings with
 * their stylesheet blob, a deny-list predicate, and fall-through to a not-found handler.
 *
 * A documentation portal publishes a site directory written to a temporary folder under
 * `/docs` on an embedded Undertow server bound to an ephemeral port. Every file carries a fixed
 * modification time so the `Last-Modified` header is predictable, and the ETag of each file is a
 * hash of its bytes, so editing a file invalidates the tag a browser cached.
 */
public class DocsPortalStaticResourceTest {

    private static final Instant PUBLISHED = Instant.parse("2026-03-01T09:30:00Z");
    private static final String PUBLISHED_HTTP_DATE = "Sun, 01 Mar 2026 09:30:00 GMT";
    private static final String RELEASE_NOTES = """
            # Release 4.2
            - faster search
            - dark mode
            """;

    @TempDir
    Path site;

    private ResourceManager resources;
    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void publishSiteAndStartServer() throws IOException {
        publish("index.html", "<h1>Docs portal</h1>");
        publish("handbook/index.html", "<h1>Handbook</h1>");
        publish("assets/app.css", "body { color: navy; }");
        publish("downloads/release-notes.md", RELEASE_NOTES);
        publish("downloads/LICENSE", "Public domain.");
        publish("drafts/roadmap.bak", "unannounced plans");

        resources = PathResourceManager.builder()
                .setBase(site)
                .setETagFunction(DocsPortalStaticResourceTest::contentHash)
                .build();

        ResourceHandler files = new ResourceHandler(resources, DocsPortalStaticResourceTest::notFound)
                .setWelcomeFiles("index.html")
                .setDirectoryListingEnabled(true)
                .setMimeMappings(MimeMappings.builder(true).addMapping("md", "text/markdown").build())
                .setCacheTime(3600)
                .setAllowed(Predicates.not(Predicates.suffixes(".bak")));

        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.path().addPrefixPath("/docs", files))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.stop();
        resources.close();
    }

    @Test
    void servesWelcomeFilesAndRedirectsDirectoriesToTheirSlashForm() throws Exception {
        HttpResponse<String> home = send("/docs/", Map.of());
        assertEquals(200, home.statusCode());
        assertEquals("<h1>Docs portal</h1>", home.body());
        assertEquals("text/html", header(home, "Content-Type"));
        assertEquals("public, max-age=3600", header(home, "Cache-Control"));
        assertTrue(home.headers().firstValue("Expires").isPresent());

        HttpResponse<String> bare = send("/docs/handbook", Map.of());
        assertEquals(302, bare.statusCode());
        assertEquals(base + "/docs/handbook/", header(bare, "Location"));

        HttpResponse<String> handbook = send("/docs/handbook/", Map.of());
        assertEquals(200, handbook.statusCode());
        assertEquals("<h1>Handbook</h1>", handbook.body());
    }

    @Test
    void mapsFileExtensionsToContentTypesIncludingApplicationOverrides() throws Exception {
        HttpResponse<String> css = send("/docs/assets/app.css", Map.of());
        assertEquals(200, css.statusCode());
        assertEquals("text/css", header(css, "Content-Type"));
        assertEquals("21", header(css, "Content-Length"));
        assertEquals("body { color: navy; }", css.body());

        HttpResponse<String> notes = send("/docs/downloads/release-notes.md", Map.of());
        assertEquals("text/markdown", header(notes, "Content-Type"));
        assertEquals(RELEASE_NOTES, notes.body());

        HttpResponse<String> license = send("/docs/downloads/LICENSE", Map.of());
        assertEquals("application/octet-stream", header(license, "Content-Type"));
        assertEquals("Public domain.", license.body());
    }

    @Test
    void answersConditionalRequestsFromContentHashETagsAndModificationTimes() throws Exception {
        HttpResponse<String> first = send("/docs/assets/app.css", Map.of());
        String etag = header(first, "ETag");
        assertEquals("\"" + contentHash(site.resolve("assets/app.css")).getTag() + "\"", etag);
        assertEquals(PUBLISHED_HTTP_DATE, header(first, "Last-Modified"));

        HttpResponse<String> cachedByTag = send("/docs/assets/app.css", Map.of("If-None-Match", etag));
        assertEquals(304, cachedByTag.statusCode());
        assertEquals("", cachedByTag.body());

        HttpResponse<String> cachedByDate = send("/docs/assets/app.css",
                Map.of("If-Modified-Since", PUBLISHED_HTTP_DATE));
        assertEquals(304, cachedByDate.statusCode());

        HttpResponse<String> lostUpdate = send("/docs/assets/app.css", Map.of("If-Match", "\"stale\""));
        assertEquals(412, lostUpdate.statusCode());

        publish("assets/app.css", "body { color: teal; }", PUBLISHED.plusSeconds(60));
        HttpResponse<String> edited = send("/docs/assets/app.css", Map.of("If-None-Match", etag));
        assertEquals(200, edited.statusCode());
        assertEquals("body { color: teal; }", edited.body());
        assertNotEquals(etag, header(edited, "ETag"));
        assertEquals("Sun, 01 Mar 2026 09:31:00 GMT", header(edited, "Last-Modified"));
    }

    @Test
    void servesByteRangesOfAFile() throws Exception {
        int length = RELEASE_NOTES.length();
        String path = "/docs/downloads/release-notes.md";

        HttpResponse<String> head = send(path, Map.of("Range", "bytes=0-12"));
        assertEquals(206, head.statusCode());
        assertEquals("bytes", header(head, "Accept-Ranges"));
        assertEquals("bytes 0-12/" + length, header(head, "Content-Range"));
        assertEquals("# Release 4.2", head.body());

        HttpResponse<String> tail = send(path, Map.of("Range", "bytes=-10"));
        assertEquals(206, tail.statusCode());
        assertEquals("bytes " + (length - 10) + "-" + (length - 1) + "/" + length,
                header(tail, "Content-Range"));
        assertEquals("dark mode\n", tail.body());

        HttpResponse<String> beyond = send(path, Map.of("Range", "bytes=5000-"));
        assertEquals(416, beyond.statusCode());
        assertEquals("bytes */" + length, header(beyond, "Content-Range"));

        HttpResponse<String> staleRange = send(path, Map.of("Range", "bytes=0-12", "If-Range", "\"old\""));
        assertEquals(200, staleRange.statusCode());
        assertEquals(RELEASE_NOTES, staleRange.body());
    }

    @Test
    void listsDirectoriesWithoutAWelcomeFileAndServesTheListingStylesheet() throws Exception {
        HttpResponse<String> listing = send("/docs/downloads/", Map.of());
        assertEquals(200, listing.statusCode());
        assertEquals("text/html; charset=UTF-8", header(listing, "Content-Type"));
        assertEquals("must-revalidate", header(listing, "Cache-Control"));
        String html = listing.body();
        assertTrue(html.contains("Directory Listing - /docs/downloads/"), html);
        assertTrue(html.contains("href='/docs'>[..]</a>"), html);
        assertTrue(html.contains("href='/docs/downloads/release-notes.md'>release-notes.md</a>"), html);
        assertTrue(html.contains("href='/docs/downloads/LICENSE'>LICENSE</a>"), html);
        assertTrue(html.contains("<link rel='stylesheet' type='text/css' href='/docs/downloads/?css' />"), html);

        HttpResponse<String> stylesheet = send("/docs/downloads/?css", Map.of());
        assertEquals(200, stylesheet.statusCode());
        assertEquals("text/css", header(stylesheet, "Content-Type"));
        assertTrue(stylesheet.body().startsWith("body {"), stylesheet.body());
        String styleTag = header(stylesheet, "ETag");

        HttpResponse<String> cachedStyle = send("/docs/downloads/?css", Map.of("If-None-Match", styleTag));
        assertEquals(304, cachedStyle.statusCode());

        HttpResponse<String> bare = send("/docs/downloads", Map.of());
        assertEquals(302, bare.statusCode());
        assertEquals(base + "/docs/downloads/", header(bare, "Location"));
    }

    @Test
    void refusesDeniedFilesUnsupportedMethodsAndFallsThroughForMissingOnes() throws Exception {
        HttpResponse<String> draft = send("/docs/drafts/roadmap.bak", Map.of());
        assertEquals(403, draft.statusCode());
        assertFalse(draft.body().contains("unannounced"));

        HttpResponse<String> missing = send("/docs/assets/missing.css", Map.of());
        assertEquals(404, missing.statusCode());
        assertEquals("no such page: /assets/missing.css", missing.body());
        assertFalse(missing.headers().firstValue("Cache-Control").isPresent());

        HttpResponse<String> fileAsDirectory = send("/docs/assets/app.css/", Map.of());
        assertEquals(404, fileAsDirectory.statusCode());

        HttpResponse<String> delete = client.send(request("/docs/assets/app.css", Map.of())
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, delete.statusCode());
        assertEquals("GET, HEAD, POST", header(delete, "Allow"));

        HttpResponse<Void> head = client.send(request("/docs/assets/app.css", Map.of())
                .HEAD().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(200, head.statusCode());
        assertEquals("21", head.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("text/css", head.headers().firstValue("Content-Type").orElseThrow());
    }

    private void publish(String relative, String content) throws IOException {
        publish(relative, content, PUBLISHED);
    }

    private void publish(String relative, String content, Instant modified) throws IOException {
        Path file = site.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.from(modified));
    }

    private HttpRequest.Builder request(String path, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10));
        headers.forEach(request::header);
        return request;
    }

    private HttpResponse<String> send(String path, Map<String, String> headers)
            throws IOException, InterruptedException {
        return client.send(request(path, headers).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElseThrow(() -> new AssertionError("missing " + name));
    }

    /// Tags each file with a strong ETag derived from its bytes; directories get none.
    private static ETag contentHash(Path path) {
        if (Files.isDirectory(path)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return new ETag(false, HexFormat.of().formatHex(digest, 0, 8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void notFound(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.NOT_FOUND);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send("no such page: " + exchange.getRelativePath(), StandardCharsets.UTF_8);
    }
}
