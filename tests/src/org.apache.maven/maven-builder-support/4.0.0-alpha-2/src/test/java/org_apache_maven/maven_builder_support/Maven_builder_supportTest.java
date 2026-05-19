/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_builder_support;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.building.FileSource;
import org.apache.maven.building.Problem;
import org.apache.maven.building.ProblemCollector;
import org.apache.maven.building.ProblemCollectorFactory;
import org.apache.maven.building.Source;
import org.apache.maven.building.StringSource;
import org.apache.maven.building.UrlSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_builder_supportTest {
    @TempDir
    Path tempDir;

    @Test
    void stringSourcePreservesContentLocationAndUtf8Bytes() throws IOException {
        StringBuilder content = new StringBuilder("<project>h\u00e9llo</project>\n");
        StringSource source = new StringSource(content, "memory:pom.xml");

        content.append("mutated after construction");

        assertThat(source.getContent()).isEqualTo("<project>h\u00e9llo</project>\n");
        assertThat(source.getLocation()).isEqualTo("memory:pom.xml");
        assertThat(source.toString()).isEqualTo("memory:pom.xml");
        assertThat(readUtf8(source)).isEqualTo("<project>h\u00e9llo</project>\n");
    }

    @Test
    void stringSourceUsesDocumentedDefaultsForNullContentAndLocation() throws IOException {
        StringSource source = new StringSource(null);

        assertThat(source.getContent()).isEmpty();
        assertThat(source.getLocation()).isEqualTo("(memory)");
        assertThat(source.toString()).isEqualTo("(memory)");
        assertThat(readUtf8(source)).isEmpty();
    }

    @Test
    void fileSourceExposesAbsoluteFileAndCreatesFreshStreams() throws IOException {
        Path file = tempDir.resolve("pom.xml");
        Files.writeString(file, "<project>file-backed</project>", StandardCharsets.UTF_8);

        FileSource source = new FileSource(file.toFile());
        File expectedFile = file.toFile().getAbsoluteFile();

        assertThat(source.getFile()).isEqualTo(expectedFile);
        assertThat(source.getLocation()).isEqualTo(expectedFile.getPath());
        assertThat(source.toString()).isEqualTo(expectedFile.getPath());
        assertThat(readUtf8(source)).isEqualTo("<project>file-backed</project>");
        assertThat(readUtf8(source)).isEqualTo("<project>file-backed</project>");
    }

    @Test
    void fileSourceRejectsNullFile() {
        assertThatThrownBy(() -> new FileSource(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("file cannot be null");
    }

    @Test
    void fileSourceReportsMissingFileLocationBeforeOpeningStream() {
        Path missingFile = tempDir.resolve("missing-pom.xml");
        FileSource source = new FileSource(missingFile.toFile());
        File expectedFile = missingFile.toFile().getAbsoluteFile();

        assertThat(source.getFile()).isEqualTo(expectedFile);
        assertThat(source.getLocation()).isEqualTo(expectedFile.getPath());
        assertThatThrownBy(source::getInputStream)
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("missing-pom.xml");
    }

    @Test
    void urlSourceExposesUrlLocationAndReadableContent() throws IOException {
        Path file = tempDir.resolve("settings.xml");
        Files.writeString(file, "<settings>url-backed</settings>", StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();

        UrlSource source = new UrlSource(url);

        assertThat(source.getUrl()).isSameAs(url);
        assertThat(source.getLocation()).isEqualTo(url.toString());
        assertThat(source.toString()).isEqualTo(url.toString());
        assertThat(readUtf8(source)).isEqualTo("<settings>url-backed</settings>");
    }

    @Test
    void urlSourceRejectsNullUrl() {
        assertThatThrownBy(() -> new UrlSource(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("url cannot be null");
    }

    @Test
    void problemCollectorKeepsBackingListAndCapturesProblemDetails() {
        List<Problem> backingProblems = new ArrayList<>();
        ProblemCollector collector = ProblemCollectorFactory.newInstance(backingProblems);
        IllegalArgumentException cause = new IllegalArgumentException("ignored when message is present");

        collector.setSource("pom.xml");
        collector.add(Problem.Severity.WARNING, "invalid dependency", 12, 34, cause);

        assertThat(collector.getProblems()).isSameAs(backingProblems);
        assertThat(backingProblems).hasSize(1);

        Problem problem = backingProblems.get(0);
        assertThat(problem.getSeverity()).isEqualTo(Problem.Severity.WARNING);
        assertThat(problem.getMessage()).isEqualTo("invalid dependency");
        assertThat(problem.getSource()).isEqualTo("pom.xml");
        assertThat(problem.getLineNumber()).isEqualTo(12);
        assertThat(problem.getColumnNumber()).isEqualTo(34);
        assertThat(problem.getException()).isSameAs(cause);
        assertThat(problem.getLocation()).isEqualTo("pom.xml, line 12, column 34");
        assertThat(problem.toString()).isEqualTo("[WARNING] invalid dependency @ pom.xml, line 12, column 34");
    }

    @Test
    void problemCollectorAppliesCurrentSourceOnlyToSubsequentProblems() {
        ProblemCollector collector = ProblemCollectorFactory.newInstance(null);

        collector.setSource("first.xml");
        collector.add(Problem.Severity.ERROR, "first", 1, -1, null);
        collector.setSource("second.xml");
        collector.add(Problem.Severity.FATAL, "second", -1, 8, null);

        assertThat(collector.getProblems()).hasSize(2);
        assertThat(collector.getProblems().get(0).getLocation()).isEqualTo("first.xml, line 1");
        assertThat(collector.getProblems().get(1).getLocation()).isEqualTo("second.xml, column 8");
        assertThat(collector.getProblems().get(0).getSeverity()).isEqualTo(Problem.Severity.ERROR);
        assertThat(collector.getProblems().get(1).getSeverity()).isEqualTo(Problem.Severity.FATAL);
    }

    @Test
    void problemCollectorDefaultsSeveritySourceAndMessageWhenInputsAreAbsent() {
        ProblemCollector collector = ProblemCollectorFactory.newInstance(null);
        Exception cause = new Exception("message from cause");
        Exception causeWithoutMessage = new Exception();

        collector.add(null, null, 0, 0, cause);
        collector.add(null, "", -1, -1, causeWithoutMessage);

        Problem problemWithCauseMessage = collector.getProblems().get(0);
        assertThat(problemWithCauseMessage.getSeverity()).isEqualTo(Problem.Severity.ERROR);
        assertThat(problemWithCauseMessage.getSource()).isEmpty();
        assertThat(problemWithCauseMessage.getLocation()).isEmpty();
        assertThat(problemWithCauseMessage.getMessage()).isEqualTo("message from cause");
        assertThat(problemWithCauseMessage.toString()).isEqualTo("[ERROR] message from cause @ ");

        Problem problemWithoutMessage = collector.getProblems().get(1);
        assertThat(problemWithoutMessage.getSeverity()).isEqualTo(Problem.Severity.ERROR);
        assertThat(problemWithoutMessage.getMessage()).isEmpty();
        assertThat(problemWithoutMessage.getLocation()).isEmpty();
    }

    @Test
    void problemCollectorReportsLineAndColumnWithoutSource() {
        ProblemCollector collector = ProblemCollectorFactory.newInstance(null);

        collector.add(Problem.Severity.WARNING, "stream-backed descriptor warning", 7, 21, null);

        Problem problem = collector.getProblems().get(0);
        assertThat(problem.getSource()).isEmpty();
        assertThat(problem.getLineNumber()).isEqualTo(7);
        assertThat(problem.getColumnNumber()).isEqualTo(21);
        assertThat(problem.getLocation()).isEqualTo("line 7, column 21");
        assertThat(problem.toString()).isEqualTo("[WARNING] stream-backed descriptor warning @ line 7, column 21");
    }

    @Test
    void severityEnumOrderMatchesDescendingSeverityContract() {
        assertThat(Problem.Severity.values()).containsExactly(
                Problem.Severity.FATAL,
                Problem.Severity.ERROR,
                Problem.Severity.WARNING);
    }

    private static String readUtf8(Source source) throws IOException {
        try (InputStream inputStream = source.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
