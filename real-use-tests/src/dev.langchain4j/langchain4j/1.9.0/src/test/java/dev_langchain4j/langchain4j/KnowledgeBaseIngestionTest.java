/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ingests a support team's knowledge base through the langchain4j document loading and
 * splitting pipeline: the file-system document loader, the text document parser, and the
 * recursive document splitter.
 *
 * The help desk keeps its policies as plain-text files in a folder tree, next to drafts in
 * Markdown that must not reach the assistant. The ingestion job loads every `.txt` file
 * recursively, keeps the file name and folder of each document as metadata, and cuts the
 * documents into paragraph-aligned segments small enough for an embedding model, so each
 * segment still says which file and which position it came from.
 */
class KnowledgeBaseIngestionTest {

    private static final int MAX_SEGMENT_CHARS = 120;

    private static final String REFUNDS = """
            Refunds are issued to the original payment method within five business days.

            Orders cancelled before shipping are refunded in full, including the delivery fee.

            Opened electronics can be returned within fourteen days for a fifteen percent restocking fee.
            """;

    private static final String SHIPPING = """
            Standard shipping takes three to five business days within the country.

            Express orders placed before noon leave the warehouse on the same day. \
            Express delivery is not available for bulky items such as furniture or appliances. \
            Customers in remote regions should allow two extra days for any delivery option.
            """;

    private static final String ARCHIVED_WARRANTY = """
            Until last year every appliance carried a two year warranty from the purchase date.
            """;

    private static final String DRAFT = """
            # Draft loyalty program

            Not approved yet, do not answer customers from this file.
            """;

    @TempDir
    Path knowledgeBase;

    @Test
    void loadsTextPoliciesRecursivelyAndSplitsThemIntoTraceableSegments() throws IOException {
        Path archive = Files.createDirectories(knowledgeBase.resolve("archive"));
        write(knowledgeBase.resolve("refunds.txt"), REFUNDS);
        write(knowledgeBase.resolve("shipping.txt"), SHIPPING);
        write(archive.resolve("warranty.txt"), ARCHIVED_WARRANTY);
        write(knowledgeBase.resolve("loyalty-draft.md"), DRAFT);

        PathMatcher textFiles = FileSystems.getDefault().getPathMatcher("glob:**.txt");
        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(knowledgeBase, textFiles);
        Map<String, Document> byFileName = documents.stream()
                .collect(Collectors.toMap(document -> document.metadata().getString(Document.FILE_NAME),
                        Function.identity()));

        assertThat(byFileName).containsOnlyKeys("refunds.txt", "shipping.txt", "warranty.txt");
        assertThat(byFileName.get("refunds.txt").text()).isEqualTo(REFUNDS);
        assertThat(byFileName.get("warranty.txt").text()).isEqualTo(ARCHIVED_WARRANTY);
        assertThat(byFileName.get("warranty.txt").metadata().getString(Document.ABSOLUTE_DIRECTORY_PATH))
                .isEqualTo(archive.toAbsolutePath().toString());
        assertThat(byFileName.get("shipping.txt").metadata().getString(Document.ABSOLUTE_DIRECTORY_PATH))
                .isEqualTo(knowledgeBase.toAbsolutePath().toString());

        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_SEGMENT_CHARS, 0);
        List<TextSegment> segments = documents.stream()
                .sorted(Comparator.comparing(document -> document.metadata().getString(Document.FILE_NAME)))
                .flatMap(document -> splitter.split(document).stream())
                .toList();

        assertThat(segments).allSatisfy(segment -> {
            assertThat(segment.text()).hasSizeLessThanOrEqualTo(MAX_SEGMENT_CHARS);
            assertThat(segment.metadata().getString(Document.FILE_NAME)).endsWith(".txt");
        });
        assertThat(segments).extracting(TextSegment::text).containsExactly(
                "Refunds are issued to the original payment method within five business days.",
                "Orders cancelled before shipping are refunded in full, including the delivery fee.",
                "Opened electronics can be returned within fourteen days for a fifteen percent restocking fee.",
                "Standard shipping takes three to five business days within the country.",
                "Express orders placed before noon leave the warehouse on the same day.",
                "Express delivery is not available for bulky items such as furniture or appliances.",
                "Customers in remote regions should allow two extra days for any delivery option.",
                "Until last year every appliance carried a two year warranty from the purchase date.");
        assertThat(segments).extracting(segment -> segment.metadata().getString(Document.FILE_NAME)
                        + "#" + segment.metadata().getInteger("index"))
                .containsExactly(
                        "refunds.txt#0", "refunds.txt#1", "refunds.txt#2",
                        "shipping.txt#0", "shipping.txt#1", "shipping.txt#2", "shipping.txt#3",
                        "warranty.txt#0");
    }

    @Test
    void reloadsOneEditedPolicyWithAnExplicitParser() throws IOException {
        Path refunds = write(knowledgeBase.resolve("refunds.txt"), REFUNDS);
        Document before = FileSystemDocumentLoader.loadDocument(refunds, new TextDocumentParser());

        String amended = REFUNDS.replace("five business days", "three business days");
        write(refunds, amended);
        Document after = FileSystemDocumentLoader.loadDocument(refunds,
                new TextDocumentParser(StandardCharsets.UTF_8));

        assertThat(before.text()).contains("within five business days");
        assertThat(after.text()).isEqualTo(amended);
        assertThat(after.metadata().getString(Document.FILE_NAME)).isEqualTo("refunds.txt");

        List<TextSegment> segments = DocumentSplitters.recursive(MAX_SEGMENT_CHARS, 0).split(after);
        assertThat(segments).first()
                .extracting(TextSegment::text)
                .isEqualTo("Refunds are issued to the original payment method within three business days.");
    }

    private static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
