/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.source.UrlSource;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ingests a product's release-notes portal through langchain4j URL document loading and the
 * line, word, regex and character document splitters.
 *
 * The documentation team publishes release notes, a known-issues list and licence keys as
 * plain text on an internal web server. The ingestion job fetches each page with the URL
 * document loader, keeps the page URL as document metadata, and cuts every page along the
 * boundary that suits it: release headings for the changelog, lines for the known issues,
 * whole words for the long upgrade guide, and raw characters for keys that contain
 * no whitespace at all.
 */
class ReleaseNotesPortalUrlIngestionTest {

    private static final String CHANGELOG = """
            ## 3.2.0
            Adds offline mode for the mobile client.
            Fixes duplicated invoices after a retry.
            ## 3.1.1
            Fixes a crash when the export folder is missing.
            ## 3.1.0
            Adds dark theme.
            Adds CSV export for every report.
            Adds keyboard shortcuts for the editor.
            Removes the legacy sync protocol.""";

    private static final String KNOWN_ISSUES = """
            KI-101 Search ignores accents in customer names
            KI-102 Printing a report with more than ninety pages times out
            KI-103 The dark theme hides the focus ring on buttons""";

    private static final String UPGRADE_GUIDE = "Stop the scheduler before you upgrade the server "
            + "then back up the database and copy the new archive into the install folder "
            + "finally start the scheduler again and watch the log for migration messages";

    private static final String LICENCE_KEY = "QX7M2PLA9ZK4TRB8WN3E";

    private final Map<String, String> pages = new ConcurrentHashMap<>();

    private HttpServer portal;

    private String baseUrl;

    @BeforeEach
    void startPortal() throws IOException {
        pages.put("/notes/changelog.txt", CHANGELOG);
        pages.put("/notes/known-issues.txt", KNOWN_ISSUES);
        pages.put("/guides/upgrade.txt", UPGRADE_GUIDE);
        pages.put("/keys/licence.txt", LICENCE_KEY);
        portal = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        portal.createContext("/", this::serve);
        portal.start();
        baseUrl = "http://localhost:" + portal.getAddress().getPort();
    }

    @AfterEach
    void stopPortal() {
        portal.stop(0);
    }

    @Test
    void loadsChangelogFromUrlAndSplitsItPerRelease() {
        String changelogUrl = baseUrl + "/notes/changelog.txt";
        Document changelog = UrlDocumentLoader.load(changelogUrl, new TextDocumentParser());

        assertThat(changelog.text()).isEqualTo(CHANGELOG);
        assertThat(changelog.metadata().getString(Document.URL)).isEqualTo(changelogUrl);

        DocumentSplitter perRelease = new DocumentByRegexSplitter("\\n(?=## )", "\n", 100, 0,
                new DocumentByLineSplitter(100, 0));
        List<TextSegment> releases = perRelease.split(changelog);

        assertThat(releases).extracting(TextSegment::text).containsExactly(
                "## 3.2.0\nAdds offline mode for the mobile client.\nFixes duplicated invoices after a retry.",
                "## 3.1.1\nFixes a crash when the export folder is missing.",
                "## 3.1.0\nAdds dark theme.\nAdds CSV export for every report.\nAdds keyboard shortcuts for the editor.",
                "Removes the legacy sync protocol.");
        assertThat(releases).allSatisfy(segment ->
                assertThat(segment.metadata().getString(Document.URL)).isEqualTo(changelogUrl));
        assertThat(releases).extracting(segment -> segment.metadata().getInteger("index"))
                .containsExactly(0, 1, 2, 3);
    }

    @Test
    void loadsKnownIssuesThroughUrlSourceAndKeepsOneIssuePerSegment() throws IOException {
        URL issuesUrl = URI.create(baseUrl + "/notes/known-issues.txt").toURL();
        Document issues = DocumentLoader.load(UrlSource.from(issuesUrl), new TextDocumentParser());
        Document reloaded = UrlDocumentLoader.load(issuesUrl, new TextDocumentParser(StandardCharsets.UTF_8));

        assertThat(reloaded.text()).isEqualTo(issues.text()).isEqualTo(KNOWN_ISSUES);
        assertThat(reloaded.metadata()).isEqualTo(issues.metadata());

        List<TextSegment> perIssue = new DocumentByLineSplitter(70, 0).split(issues);

        assertThat(perIssue).extracting(TextSegment::text).containsExactly(
                "KI-101 Search ignores accents in customer names",
                "KI-102 Printing a report with more than ninety pages times out",
                "KI-103 The dark theme hides the focus ring on buttons");
        assertThat(perIssue).allSatisfy(segment ->
                assertThat(segment.metadata().getString(Document.URL)).isEqualTo(issuesUrl.toString()));
    }

    @Test
    void packsLongUpgradeGuideIntoWordWindowsThatNeverCutAWord() {
        Document guide = UrlDocumentLoader.load(baseUrl + "/guides/upgrade.txt", new TextDocumentParser());

        List<TextSegment> windows = new DocumentByWordSplitter(60, 0).split(guide);

        assertThat(windows).extracting(TextSegment::text).containsExactly(
                "Stop the scheduler before you upgrade the server then back",
                "up the database and copy the new archive into the install",
                "folder finally start the scheduler again and watch the log",
                "for migration messages");
        assertThat(windows).allSatisfy(window -> assertThat(window.text()).hasSizeLessThanOrEqualTo(60));
        assertThat(String.join(" ", windows.stream().map(TextSegment::text).toList())).isEqualTo(UPGRADE_GUIDE);
    }

    @Test
    void cutsWhitespaceFreeLicenceKeysIntoFixedCharacterBlocks() {
        Document licence = UrlDocumentLoader.load(baseUrl + "/keys/licence.txt", new TextDocumentParser());

        List<TextSegment> blocks = new DocumentByCharacterSplitter(5, 0).split(licence);

        assertThat(blocks).extracting(TextSegment::text)
                .containsExactly("QX7M2", "PLA9Z", "K4TRB", "8WN3E");
        assertThat(String.join("", blocks.stream().map(TextSegment::text).toList())).isEqualTo(LICENCE_KEY);
    }

    @Test
    void reportsAMissingPageAsAFailedLoad() {
        assertThatThrownBy(() -> UrlDocumentLoader.load(baseUrl + "/notes/retired.txt", new TextDocumentParser()))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IOException.class);
    }

    private void serve(HttpExchange exchange) throws IOException {
        String page = pages.get(exchange.getRequestURI().getPath());
        if (page == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] body = page.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
