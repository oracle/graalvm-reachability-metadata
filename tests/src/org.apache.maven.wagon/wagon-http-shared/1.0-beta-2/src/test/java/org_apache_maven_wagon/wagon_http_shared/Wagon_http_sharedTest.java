/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_http_shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.maven.wagon.shared.http.HtmlFileListParser;
import org.apache.maven.wagon.shared.http.NullOutputStream;
import org.junit.jupiter.api.Test;

public class Wagon_http_sharedTest {
    @Test
    void parsesSimpleFileAndDirectoryLinksFromHtmlListing() throws Exception {
        String html = """
                <html>
                  <body>
                    <h1>Index of /repository/releases/</h1>
                    <a href="library-1.0.jar">library-1.0.jar</a>
                    <div><a href=" library-1.0.pom ">library-1.0.pom</a></div>
                    <span><a href="checksums/">checksums/</a></span>
                  </body>
                </html>
                """;

        List<String> links = parseFileList("http://repo.example.test/repository/releases/", html);

        assertThat(links).containsExactly("library-1.0.jar", "library-1.0.pom", "checksums/");
    }

    @Test
    void acceptsSameBaseUrlAndHostRelativeLinks() throws Exception {
        String html = """
                <html>
                  <body>
                    <a href="http://repo.example.test:8080/repository/releases/app-1.0.jar">jar</a>
                    <a href="/repository/releases/app-1.0.pom">pom</a>
                    <a href="http://repo.example.test:8080/repository/releases/snapshots/">snapshots</a>
                    <a href="/repository/releases/metadata/">metadata</a>
                  </body>
                </html>
                """;

        List<String> links = parseFileList("http://repo.example.test:8080/repository/releases/", html);

        assertThat(links).containsExactly("app-1.0.jar", "app-1.0.pom", "snapshots/", "metadata/");
    }

    @Test
    void acceptsHostRelativeLinksWhenListingIsAtServerRoot() throws Exception {
        String html = """
                <html>
                  <body>
                    <a href="/maven-metadata.xml">metadata</a>
                    <a href="/releases/">releases</a>
                    <a href="/nested/artifact.jar">nested file</a>
                  </body>
                </html>
                """;

        List<String> links = parseFileList("http://repo.example.test/", html);

        assertThat(links).containsExactly("maven-metadata.xml", "releases/");
    }

    @Test
    void filtersNavigationAndNonListingLinks() throws Exception {
        String html = """
                <html>
                  <body>
                    <a href="valid-file.txt">valid-file.txt</a>
                    <a href="valid-directory/">valid-directory/</a>
                    <a href="https://other.example.test/file.txt">foreign absolute URL</a>
                    <a href="file.txt?download=true">query string</a>
                    <a href="mailto:users@example.test">mail</a>
                    <a href="deep/path/file.txt">nested file</a>
                    <a href="one-level/file.txt">path to file</a>
                    <a href="/repository/other/file.txt">wrong host-relative path</a>
                    <a href="">empty</a>
                  </body>
                </html>
                """;

        List<String> links = parseFileList("http://repo.example.test/repository/releases/", html);

        assertThat(links).containsExactly("valid-file.txt", "valid-directory/");
    }

    @Test
    void recoversAnchorLinksFromMalformedHtml() throws Exception {
        String html = """
                <HTML><BODY>
                  <A HREF="first.txt">first
                  <table><tr><td><a href="nested/">nested
                  <a href="bad:name.txt">bad</a>
                  <a href="second.txt">second</a>
                """;

        List<String> links = parseFileList("http://repo.example.test/repository/releases/", html);

        assertThat(links).containsExactly("first.txt", "nested/", "second.txt");
    }

    @Test
    void treatsBackslashPathSeparatorsLikeSlashWhenValidatingLinks() throws Exception {
        String html = """
                <html>
                  <body>
                    <a href="windows-directory\\">windows directory</a>
                    <a href="nested\\artifact.jar">nested file</a>
                    <a href="deep\\nested\\">deep directory</a>
                  </body>
                </html>
                """;

        List<String> links = parseFileList("http://repo.example.test/repository/releases/", html);

        assertThat(links).containsExactly("windows-directory\\");
    }

    @Test
    void nullOutputStreamIgnoresAllWritesAndRemainsUsableAfterClose() {
        NullOutputStream outputStream = new NullOutputStream();
        byte[] bytes = "discarded data".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> {
            outputStream.write('x');
            outputStream.write(bytes);
            outputStream.write(bytes, 2, 5);
            outputStream.flush();
            outputStream.close();
            outputStream.write('y');
            outputStream.write(bytes, 0, bytes.length);
            outputStream.flush();
        }).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseFileList(String baseUrl, String html) throws Exception {
        return HtmlFileListParser.parseFileList(baseUrl, input(html));
    }

    private static ByteArrayInputStream input(String html) {
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }
}
