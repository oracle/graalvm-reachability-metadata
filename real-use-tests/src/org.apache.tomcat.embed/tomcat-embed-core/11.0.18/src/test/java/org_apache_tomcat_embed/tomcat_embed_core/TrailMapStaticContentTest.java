/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceRoot.ResourceSetType;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serves static web content through the `DefaultServlet` over a `WebResourceRoot` that layers a
 * pre-resources directory and a `META-INF/resources` JAR over the document base, answering
 * welcome files, directory listings, conditional and range requests and precompressed variants.
 *
 * A hiking club ships its trail map site as plain files: the page shell lives in the document
 * base, a seasonal branding directory overrides the home page without touching it, and the
 * elevation widget arrives inside a resource JAR the way a WebJar does. Browsers hit the bare
 * context path and are redirected to the welcome page, browse the downloads folder through the
 * generated listing, revalidate cached files with `ETag` and `Last-Modified`, resume a large
 * guide download with byte ranges, and get the stylesheet gzip-encoded when they accept it.
 * Nothing under `WEB-INF` is ever served.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrailMapStaticContentTest {

    private static final int GUIDE_SIZE = 256 * 1024;

    private static final String STYLESHEET = "body { font-family: sans-serif; }\n".repeat(200);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Tomcat tomcat;

    private Path baseDir;

    private String baseUrl;

    private byte[] guide;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("trail-map-tomcat");
        Path docBase = Files.createDirectories(baseDir.resolve("site"));
        write(docBase.resolve("index.html"), "<h1>Trail map</h1>");
        write(docBase.resolve("css/site.css"), STYLESHEET);
        writeGzip(docBase.resolve("css/site.css.gz"), STYLESHEET);
        write(docBase.resolve("maps/ridge-loop.gpx"), "<gpx><trk><name>Ridge loop</name></trk></gpx>");
        write(docBase.resolve("WEB-INF/club-roster.txt"), "treasurer=alice");
        guide = new byte[GUIDE_SIZE];
        for (int i = 0; i < guide.length; i++) {
            guide[i] = (byte) (i * 31 % 251);
        }
        Files.createDirectories(docBase.resolve("downloads"));
        Files.write(docBase.resolve("downloads/trail-guide.pdf"), guide);
        write(docBase.resolve("downloads/permit-form.txt"), "Name:\nDate:\n");

        Path branding = Files.createDirectories(baseDir.resolve("branding"));
        write(branding.resolve("index.html"), "<h1>Trail map - autumn edition</h1>");

        Path widgetJar = baseDir.resolve("elevation-widget.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(widgetJar))) {
            jar.putNextEntry(new JarEntry("META-INF/resources/widgets/elevation.js"));
            jar.write("export function elevation(track) { return track.gain; }\n"
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("/trails", docBase.toString());
        context.addLifecycleListener(event -> {
            if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                WebResourceRoot resources = context.getResources();
                resources.createWebResourceSet(ResourceSetType.PRE, "/", branding.toString(), null, "/");
                resources.createWebResourceSet(ResourceSetType.RESOURCE_JAR, "/", widgetJar.toString(), null,
                        "/META-INF/resources");
            }
        });
        Tomcat.addDefaultMimeTypeMappings(context);
        context.addMimeMapping("gpx", "application/gpx+xml");
        context.addWelcomeFile("index.html");

        Wrapper defaultServlet = Tomcat.addServlet(context, "default", new DefaultServlet());
        defaultServlet.addInitParameter("listings", "true");
        defaultServlet.addInitParameter("precompressed", "true");
        context.addServletMappingDecoded("/", "default");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/trails";
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void redirectsTheBareContextPathAndServesTheBrandedWelcomePage() throws Exception {
        HttpResponse<String> bare = sendText(HttpRequest.newBuilder(URI.create(baseUrl)));
        assertThat(bare.statusCode()).isEqualTo(302);
        assertThat(bare.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).endsWith("/trails/"));

        HttpResponse<String> home = sendText(get("/"));
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/html"));
        assertThat(home.body()).isEqualTo("<h1>Trail map - autumn edition</h1>");
    }

    @Test
    void servesFilesFromTheResourceJarAndCustomMimeMappings() throws Exception {
        HttpResponse<String> widget = sendText(get("/widgets/elevation.js"));
        assertThat(widget.statusCode()).isEqualTo(200);
        assertThat(widget.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/javascript"));
        assertThat(widget.body()).contains("export function elevation");

        HttpResponse<String> track = sendText(get("/maps/ridge-loop.gpx"));
        assertThat(track.statusCode()).isEqualTo(200);
        assertThat(track.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/gpx+xml"));
        assertThat(track.body()).contains("<name>Ridge loop</name>");
    }

    @Test
    void neverServesWebInfOrMissingFiles() throws Exception {
        assertThat(sendText(get("/WEB-INF/club-roster.txt")).statusCode()).isEqualTo(404);
        assertThat(sendText(get("/maps/unknown-peak.gpx")).statusCode()).isEqualTo(404);
    }

    @Test
    void listsTheDownloadsFolder() throws Exception {
        HttpResponse<String> listing = sendText(get("/downloads/"));

        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(listing.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/html"));
        assertThat(listing.body()).contains("/downloads/", "trail-guide.pdf", "permit-form.txt");
    }

    @Test
    void revalidatesCachedFilesWithEtagAndLastModified() throws Exception {
        HttpResponse<String> first = sendText(get("/maps/ridge-loop.gpx"));
        String etag = first.headers().firstValue("ETag").orElseThrow();
        String lastModified = first.headers().firstValue("Last-Modified").orElseThrow();
        assertThat(etag).startsWith("W/\"");

        HttpResponse<String> byEtag = sendText(get("/maps/ridge-loop.gpx").header("If-None-Match", etag));
        assertThat(byEtag.statusCode()).isEqualTo(304);
        assertThat(byEtag.body()).isEmpty();

        HttpResponse<String> byDate = sendText(get("/maps/ridge-loop.gpx")
                .header("If-Modified-Since", lastModified));
        assertThat(byDate.statusCode()).isEqualTo(304);

        HttpResponse<String> stale = sendText(get("/maps/ridge-loop.gpx")
                .header("If-None-Match", "W/\"0-0\""));
        assertThat(stale.statusCode()).isEqualTo(200);
    }

    @Test
    void resumesTheGuideDownloadWithByteRanges() throws Exception {
        HttpResponse<byte[]> head = sendBytes(HttpRequest.newBuilder(URI.create(baseUrl + "/downloads/trail-guide.pdf"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()));
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.headers().firstValue("Content-Length")).hasValue(String.valueOf(GUIDE_SIZE));
        assertThat(head.headers().firstValue("Accept-Ranges")).hasValue("bytes");
        assertThat(head.body()).isEmpty();

        HttpResponse<byte[]> full = sendBytes(get("/downloads/trail-guide.pdf"));
        assertThat(full.statusCode()).isEqualTo(200);
        assertThat(full.body()).isEqualTo(guide);

        HttpResponse<byte[]> tail = sendBytes(get("/downloads/trail-guide.pdf").header("Range", "bytes=200000-"));
        assertThat(tail.statusCode()).isEqualTo(206);
        assertThat(tail.headers().firstValue("Content-Range"))
                .hasValue("bytes 200000-" + (GUIDE_SIZE - 1) + "/" + GUIDE_SIZE);
        assertThat(tail.body()).isEqualTo(Arrays.copyOfRange(guide, 200000, GUIDE_SIZE));

        HttpResponse<byte[]> outdated = sendBytes(get("/downloads/trail-guide.pdf")
                .header("Range", "bytes=0-9")
                .header("If-Range", "\"stale-copy\""));
        assertThat(outdated.statusCode()).isEqualTo(200);
        assertThat(outdated.body()).hasSize(GUIDE_SIZE);

        HttpResponse<byte[]> pieces = sendBytes(get("/downloads/trail-guide.pdf").header("Range", "bytes=0-3,1000-1003"));
        assertThat(pieces.statusCode()).isEqualTo(206);
        assertThat(pieces.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("multipart/byteranges"));
        assertThat(new String(pieces.body(), StandardCharsets.ISO_8859_1)).contains(
                "Content-Range: bytes 0-3/" + GUIDE_SIZE,
                "Content-Range: bytes 1000-1003/" + GUIDE_SIZE);

        HttpResponse<byte[]> beyond = sendBytes(get("/downloads/trail-guide.pdf").header("Range", "bytes=999999-"));
        assertThat(beyond.statusCode()).isEqualTo(416);
        assertThat(beyond.headers().firstValue("Content-Range")).hasValue("bytes */" + GUIDE_SIZE);
    }

    @Test
    void servesThePrecompressedStylesheetOnlyToGzipClients() throws Exception {
        HttpResponse<byte[]> gzipped = sendBytes(get("/css/site.css").header("Accept-Encoding", "gzip"));
        assertThat(gzipped.statusCode()).isEqualTo(200);
        assertThat(gzipped.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(gzipped.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/css"));
        assertThat(gzipped.headers().allValues("Vary")).anySatisfy(
                vary -> assertThat(vary).containsIgnoringCase("accept-encoding"));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped.body()))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(STYLESHEET);
        }

        HttpResponse<String> plain = sendText(get("/css/site.css"));
        assertThat(plain.statusCode()).isEqualTo(200);
        assertThat(plain.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(plain.body()).isEqualTo(STYLESHEET);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private HttpResponse<String> sendText(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writeGzip(Path file, String content) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
