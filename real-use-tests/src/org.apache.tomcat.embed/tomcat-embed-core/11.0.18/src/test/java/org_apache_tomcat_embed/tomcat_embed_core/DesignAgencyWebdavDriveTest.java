/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shares project files over WebDAV through the `WebdavServlet`, which answers `OPTIONS`,
 * `MKCOL`, `PUT`, `PROPFIND`, `PROPPATCH`, `COPY`, `MOVE`, `LOCK`, `UNLOCK` and `DELETE`
 * against the context's document base and keeps dead properties in its property store.
 *
 * A design agency mounts a shared drive in its desktop clients: designers create a folder per
 * client project, upload drafts, browse the folder through multistatus listings, tag drafts
 * with the client they belong to, duplicate and rename files, and take an exclusive write lock
 * before editing a file so that a colleague's upload is refused until the lock is released.
 * Everything a client does lands as plain files in the drive's directory on disk.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesignAgencyWebdavDriveTest {

    private static final String DAV = "DAV:";

    private static final String AGENCY = "urn:design-agency:";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Tomcat tomcat;

    private Path baseDir;

    private Path driveDir;

    private String baseUrl;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("design-agency-webdav");
        driveDir = Files.createDirectories(baseDir.resolve("drive"));
        Files.writeString(driveDir.resolve("welcome.txt"), "Shared drive of the design agency\n",
                StandardCharsets.UTF_8);

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("/drive", driveDir.toString());
        Tomcat.addDefaultMimeTypeMappings(context);
        Wrapper webdav = Tomcat.addServlet(context, "webdav", new WebdavServlet());
        webdav.addInitParameter("readonly", "false");
        webdav.addInitParameter("listings", "true");
        context.addServletMappingDecoded("/*", "webdav");

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/drive";
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
    void advertisesWebdavClassesAndMethods() throws Exception {
        HttpResponse<String> options = send(request("OPTIONS", "/", null));

        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(options.headers().firstValue("DAV")).hasValueSatisfying(
                dav -> assertThat(dav).contains("1", "2"));
        assertThat(options.headers().firstValue("Allow")).hasValueSatisfying(allow -> assertThat(allow)
                .contains("PROPFIND", "PROPPATCH", "COPY", "MOVE", "LOCK", "UNLOCK")
                .doesNotContain("MKCOL"));

        HttpResponse<String> unmapped = send(request("OPTIONS", "/not-yet-created/", null));
        assertThat(unmapped.statusCode()).isEqualTo(200);
        assertThat(unmapped.headers().firstValue("Allow")).hasValueSatisfying(
                allow -> assertThat(allow).contains("MKCOL", "PUT"));
    }

    @Test
    void createsAProjectFolderAndListsTheUploadedDrafts() throws Exception {
        assertThat(send(request("MKCOL", "/harbor-cafe/", null)).statusCode()).isEqualTo(201);
        assertThat(send(request("MKCOL", "/harbor-cafe/", null)).statusCode()).isEqualTo(405);
        assertThat(send(request("MKCOL", "/no-such-client/sketches/", null)).statusCode()).isEqualTo(409);

        assertThat(put("/harbor-cafe/menu.txt", "Espresso 2.50\nFlat white 3.20\n").statusCode()).isEqualTo(201);
        assertThat(put("/harbor-cafe/logo.svg", "<svg><circle r=\"4\"/></svg>").statusCode()).isEqualTo(201);
        assertThat(put("/harbor-cafe/menu.txt", "Espresso 2.60\nFlat white 3.30\n").statusCode()).isEqualTo(204);

        HttpResponse<String> listing = send(request("PROPFIND", "/harbor-cafe/", """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:"><D:allprop/></D:propfind>
                """).header("Depth", "1"));
        assertThat(listing.statusCode()).isEqualTo(207);
        Map<String, Element> responses = responsesByHref(listing.body());
        assertThat(responses).containsOnlyKeys(
                "/drive/harbor-cafe/", "/drive/harbor-cafe/menu.txt", "/drive/harbor-cafe/logo.svg");
        assertThat(firstText(responses.get("/drive/harbor-cafe/menu.txt"), DAV, "getcontentlength"))
                .isEqualTo(String.valueOf("Espresso 2.60\nFlat white 3.30\n".length()));
        assertThat(firstText(responses.get("/drive/harbor-cafe/logo.svg"), DAV, "getcontenttype"))
                .startsWith("image/svg+xml");
        assertThat(responses.get("/drive/harbor-cafe/")
                .getElementsByTagNameNS(DAV, "collection").getLength()).isEqualTo(1);

        HttpResponse<String> menu = send(request("GET", "/harbor-cafe/menu.txt", null));
        assertThat(menu.statusCode()).isEqualTo(200);
        assertThat(menu.body()).isEqualTo("Espresso 2.60\nFlat white 3.30\n");
        assertThat(driveDir.resolve("harbor-cafe/logo.svg")).hasContent("<svg><circle r=\"4\"/></svg>");
    }

    @Test
    void tagsADraftWithTheClientItBelongsTo() throws Exception {
        assertThat(send(request("MKCOL", "/river-books/", null)).statusCode()).isEqualTo(201);
        assertThat(put("/river-books/poster.txt", "Autumn reading festival").statusCode()).isEqualTo(201);

        HttpResponse<String> patch = send(request("PROPPATCH", "/river-books/poster.txt", """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propertyupdate xmlns:D="DAV:" xmlns:A="urn:design-agency:">
                  <D:set><D:prop><A:client>River Books</A:client></D:prop></D:set>
                </D:propertyupdate>
                """));
        assertThat(patch.statusCode()).isEqualTo(207);
        Element patched = responsesByHref(patch.body()).get("/drive/river-books/poster.txt");
        assertThat(firstText(patched, DAV, "status")).contains("200");

        HttpResponse<String> found = send(request("PROPFIND", "/river-books/poster.txt", """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:A="urn:design-agency:">
                  <D:prop><A:client/><D:getcontentlength/></D:prop>
                </D:propfind>
                """).header("Depth", "0"));
        assertThat(found.statusCode()).isEqualTo(207);
        Element poster = responsesByHref(found.body()).get("/drive/river-books/poster.txt");
        assertThat(firstText(poster, AGENCY, "client")).isEqualTo("River Books");
        assertThat(firstText(poster, DAV, "getcontentlength"))
                .isEqualTo(String.valueOf("Autumn reading festival".length()));
    }

    @Test
    void duplicatesAndRenamesDrafts() throws Exception {
        assertThat(send(request("MKCOL", "/city-zoo/", null)).statusCode()).isEqualTo(201);
        assertThat(put("/city-zoo/banner-v1.txt", "Meet the penguins").statusCode()).isEqualTo(201);

        HttpResponse<String> copy = send(request("COPY", "/city-zoo/banner-v1.txt", null)
                .header("Destination", baseUrl + "/city-zoo/banner-v2.txt"));
        assertThat(copy.statusCode()).isEqualTo(201);

        HttpResponse<String> blockedMove = send(request("MOVE", "/city-zoo/banner-v2.txt", null)
                .header("Destination", baseUrl + "/city-zoo/banner-v1.txt")
                .header("Overwrite", "F"));
        assertThat(blockedMove.statusCode()).isEqualTo(412);

        HttpResponse<String> move = send(request("MOVE", "/city-zoo/banner-v2.txt", null)
                .header("Destination", baseUrl + "/city-zoo/banner-final.txt"));
        assertThat(move.statusCode()).isEqualTo(201);

        assertThat(send(request("GET", "/city-zoo/banner-v2.txt", null)).statusCode()).isEqualTo(404);
        assertThat(send(request("GET", "/city-zoo/banner-final.txt", null)).body()).isEqualTo("Meet the penguins");
        assertThat(send(request("GET", "/city-zoo/banner-v1.txt", null)).body()).isEqualTo("Meet the penguins");

        assertThat(send(request("DELETE", "/city-zoo/", null)).statusCode()).isEqualTo(204);
        assertThat(send(request("GET", "/city-zoo/banner-final.txt", null)).statusCode()).isEqualTo(404);
        assertThat(driveDir.resolve("city-zoo")).doesNotExist();
    }

    @Test
    void refusesUploadsToALockedDraftUntilItIsUnlocked() throws Exception {
        assertThat(send(request("MKCOL", "/mountain-gym/", null)).statusCode()).isEqualTo(201);
        assertThat(put("/mountain-gym/flyer.txt", "Open day on Saturday").statusCode()).isEqualTo(201);

        HttpResponse<String> lock = send(request("LOCK", "/mountain-gym/flyer.txt", """
                <?xml version="1.0" encoding="utf-8"?>
                <D:lockinfo xmlns:D="DAV:">
                  <D:lockscope><D:exclusive/></D:lockscope>
                  <D:locktype><D:write/></D:locktype>
                  <D:owner>alice@design-agency.example</D:owner>
                </D:lockinfo>
                """).header("Timeout", "Second-600"));
        assertThat(lock.statusCode()).isEqualTo(200);
        String lockToken = lock.headers().firstValue("Lock-Token").orElseThrow();
        assertThat(lockToken).startsWith("<urn:uuid:").endsWith(">");
        Document lockDiscovery = parse(lock.body());
        assertThat(lockDiscovery.getElementsByTagNameNS(DAV, "exclusive").getLength()).isEqualTo(1);
        assertThat(lockDiscovery.getElementsByTagNameNS(DAV, "owner").item(0).getTextContent())
                .contains("alice@design-agency.example");

        HttpResponse<String> colleague = put("/mountain-gym/flyer.txt", "Open day on Sunday");
        assertThat(colleague.statusCode()).isEqualTo(423);
        assertThat(send(request("DELETE", "/mountain-gym/flyer.txt", null)).statusCode()).isEqualTo(423);

        HttpResponse<String> owner = send(request("PUT", "/mountain-gym/flyer.txt", "Open day on Saturday, 10am")
                .header("If", "(" + lockToken + ")"));
        assertThat(owner.statusCode()).isEqualTo(204);

        HttpResponse<String> unlock = send(request("UNLOCK", "/mountain-gym/flyer.txt", null)
                .header("Lock-Token", lockToken));
        assertThat(unlock.statusCode()).isEqualTo(204);

        assertThat(put("/mountain-gym/flyer.txt", "Open day on Sunday").statusCode()).isEqualTo(204);
        assertThat(send(request("GET", "/mountain-gym/flyer.txt", null)).body()).isEqualTo("Open day on Sunday");
    }

    private HttpRequest.Builder request(String method, String path, String body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).method(method, publisher);
        if (body != null && body.startsWith("<?xml")) {
            builder.header("Content-Type", "application/xml; charset=utf-8");
        }
        return builder;
    }

    private HttpResponse<String> put(String path, String content) throws IOException, InterruptedException {
        return send(request("PUT", path, content).header("Content-Type", "text/plain; charset=utf-8"));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static Map<String, Element> responsesByHref(String multistatus)
            throws ParserConfigurationException, IOException, SAXException {
        NodeList responses = parse(multistatus).getElementsByTagNameNS(DAV, "response");
        Map<String, Element> byHref = new HashMap<>();
        List<String> hrefs = new ArrayList<>();
        for (int i = 0; i < responses.getLength(); i++) {
            Element response = (Element) responses.item(i);
            String href = firstText(response, DAV, "href");
            hrefs.add(href);
            byHref.put(href, response);
        }
        assertThat(hrefs).doesNotHaveDuplicates();
        return byHref;
    }

    private static String firstText(Element element, String namespace, String localName) {
        NodeList matches = element.getElementsByTagNameNS(namespace, localName);
        assertThat(matches.getLength()).as("<%s> in response", localName).isGreaterThan(0);
        return matches.item(0).getTextContent().trim();
    }

    private static Document parse(String xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
