/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// §AR-test-harness.8: the DOM of a large library's JaCoCo report does not fit the Gradle daemon
/// heap. The `lowHeapTest` task runs this class in a 64 MiB heap and the report it generates is
/// half as large again, so it cannot even be held as text, let alone as a DOM of about nine times
/// its size: only a parser that streams the report and keeps just its result passes.
@Tag("low-heap")
class JacocoReportParserLowHeapTests {

    /// Set by `lowHeapTest` to a directory under the build directory that it empties before each
    /// run, because a JVM that dies of `OutOfMemoryError` leaves the report behind.
    private static final String REPORT_DIR_PROPERTY = "tck.lowHeapReportDir";
    private static final int PACKAGES = 50;
    private static final int CLASSES_PER_PACKAGE = 40;
    private static final int METHODS_PER_CLASS = 20;
    private static final int LINES_PER_SOURCE = 1_200;
    /// Only every hundredth line is covered, so the parsed result is a small part of the heap.
    private static final int COVERED_LINE_STRIDE = 100;
    private static final int SOURCES = PACKAGES * CLASSES_PER_PACKAGE;
    private static final int COVERED_LINES_PER_SOURCE = LINES_PER_SOURCE / COVERED_LINE_STRIDE;

    @Test
    void parsesAReportLargerThanTheHeap() throws IOException {
        String reportDir = System.getProperty(REPORT_DIR_PROPERTY);
        assertThat(reportDir)
                .as("run this class through lowHeapTest, which sets " + REPORT_DIR_PROPERTY)
                .isNotNull();
        Path report = Files.createDirectories(Path.of(reportDir)).resolve("jacoco.xml");
        try {
            writeReport(report);
            long maxHeap = Runtime.getRuntime().maxMemory();
            assertThat(Files.size(report))
                    .as("the report must be half as large again as the heap: run this class through lowHeapTest")
                    .isGreaterThan(maxHeap + maxHeap / 2);

            JacocoReportParser.ParsedJacocoReport parsed = JacocoReportParser.parse(report);

            Set<Integer> coveredLines = new LinkedHashSet<>();
            for (int line = 1; line <= LINES_PER_SOURCE; line += COVERED_LINE_STRIDE) {
                coveredLines.add(line);
            }
            assertThat(parsed.coveredLinesBySource()).hasSize(SOURCES);
            assertThat(parsed.coveredLinesBySource().values())
                    .allSatisfy(lines -> assertThat(lines).isEqualTo(coveredLines));
            assertThat(parsed.coveredLinesBySource())
                    .containsKey("org/example/p" + (PACKAGES - 1) + "/C" + (CLASSES_PER_PACKAGE - 1) + ".java");
            assertThat(parsed.methodNamesByClass()).hasSize(SOURCES);
            assertThat(parsed.methodNamesByClass().get("org.example.p0.C0"))
                    .hasSize(METHODS_PER_CLASS)
                    .contains("m0", "m" + (METHODS_PER_CLASS - 1));
            assertThat(parsed.line().covered()).isEqualTo((long) SOURCES * COVERED_LINES_PER_SOURCE);
            assertThat(parsed.line().missed())
                    .isEqualTo((long) SOURCES * (LINES_PER_SOURCE - COVERED_LINES_PER_SOURCE));
            assertThat(parsed.method().covered()).isEqualTo((long) SOURCES * METHODS_PER_CLASS);
        } finally {
            Files.deleteIfExists(report);
        }
    }

    /// Writes a report shaped like JaCoCo's, all on one line, without holding it in memory.
    private static void writeReport(Path report) throws IOException {
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(report), StandardCharsets.UTF_8),
                1 << 20
        )) {
            writer.write("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>\
                    <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">\
                    <report name="large"><sessioninfo id="host-1" start="1" dump="2"/>""");
            for (int p = 0; p < PACKAGES; p++) {
                String packageName = "org/example/p" + p;
                writer.write("<package name=\"" + packageName + "\">");
                for (int c = 0; c < CLASSES_PER_PACKAGE; c++) {
                    writeClass(writer, packageName + "/C" + c, "C" + c + ".java");
                }
                for (int c = 0; c < CLASSES_PER_PACKAGE; c++) {
                    writeSourceFile(writer, "C" + c + ".java");
                }
                writeCounters(writer, CLASSES_PER_PACKAGE);
                writer.write("</package>");
            }
            writeCounters(writer, SOURCES);
            writer.write("</report>");
        }
    }

    private static void writeClass(Writer writer, String className, String sourceFile) throws IOException {
        writer.write("<class name=\"" + className + "\" sourcefilename=\"" + sourceFile + "\">");
        for (int m = 0; m < METHODS_PER_CLASS; m++) {
            writer.write("<method name=\"m" + m + "\" desc=\"(Ljava/lang/String;I)V\" line=\""
                    + (m * COVERED_LINE_STRIDE + 1) + "\">"
                    + "<counter type=\"INSTRUCTION\" missed=\"9\" covered=\"3\"/>"
                    + "<counter type=\"LINE\" missed=\"3\" covered=\"1\"/>"
                    + "<counter type=\"COMPLEXITY\" missed=\"0\" covered=\"1\"/>"
                    + "<counter type=\"METHOD\" missed=\"0\" covered=\"1\"/></method>");
        }
        writeCounters(writer, 1);
        writer.write("</class>");
    }

    private static void writeSourceFile(Writer writer, String sourceFile) throws IOException {
        writer.write("<sourcefile name=\"" + sourceFile + "\">");
        for (int line = 1; line <= LINES_PER_SOURCE; line++) {
            boolean covered = line % COVERED_LINE_STRIDE == 1;
            writer.write("<line nr=\"" + line + "\" mi=\"" + (covered ? 0 : 3) + "\" ci=\"" + (covered ? 3 : 0)
                    + "\" mb=\"0\" cb=\"0\"/>");
        }
        writeCounters(writer, 1);
        writer.write("</sourcefile>");
    }

    /// Writes the counters of an element that spans `sources` source files.
    private static void writeCounters(Writer writer, int sources) throws IOException {
        long coveredLines = (long) sources * COVERED_LINES_PER_SOURCE;
        long missedLines = (long) sources * LINES_PER_SOURCE - coveredLines;
        long methods = (long) sources * METHODS_PER_CLASS;
        writer.write("<counter type=\"INSTRUCTION\" missed=\"" + 3 * missedLines
                + "\" covered=\"" + 3 * coveredLines + "\"/>"
                + "<counter type=\"LINE\" missed=\"" + missedLines + "\" covered=\"" + coveredLines + "\"/>"
                + "<counter type=\"METHOD\" missed=\"0\" covered=\"" + methods + "\"/>");
    }
}
