/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.graalvm.internal.tck.stats.CoverageMeasurementTests.createLibraryJar;

/// §AR-test-harness.8.1: which call sites agent origins may decide. Only a line-less frame, or one
/// in a method JaCoCo filtered out of a class it analyzed, falls back to agent evidence; every
/// other frame is decided by its line whatever the agent saw.
class UndecidedCallSiteTests {

    private static final String FOR_NAME = "java.lang.Class#forName(java.lang.String)";

    @TempDir
    Path tempDir;

    /// nekohtml's `Writer.class$` shares line 469 with `printUsage`, so JaCoCo reports the line,
    /// missed when `printUsage` never runs. The helper's method is still absent from the class.
    @Test
    void classHelperSharingALineWithAnotherMethodFallsBackToAgentOrigins() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Writer.class"));
        Path dynamicAccessDir = writeForNameCalls("com.example.Writer.class$(Writer.java:469)");
        Path jacocoReport = writeJacocoReport("""
                <class name="com/example/Writer" sourcefilename="Writer.java">
                  <method name="printUsage" desc="()V" line="469"/>
                </class>
                <sourcefile name="Writer.java">
                  <line nr="469" mi="4" ci="0" mb="0" cb="0"/>
                </sourcefile>
                """);

        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(libraryJar), jacocoReport)).isTrue();
        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                Set.of(new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "com.example.Writer", "class$"))
        );

        assertThat(report.totals().totalCalls()).isEqualTo(1);
        assertThat(report.totals().coveredCalls()).isEqualTo(1);
    }

    /// An upstream test JAR is on the library classpath, but JaCoCo analyzes only the main JAR, so
    /// the report has no `<class>` for its classes. Their frames keep the pre-fallback rule.
    @Test
    void framesInClassesJacocoNeverAnalyzedStayDecidedByLine() throws IOException {
        Path mainJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));
        Path testJar = createLibraryJar(tempDir.resolve("demo-tests.jar"), List.of("com/example/FooTestUtil.class"));
        Path dynamicAccessDir = writeForNameCalls("com.example.FooTestUtil.helper(FooTestUtil.java:5)");
        Path jacocoReport = writeJacocoReport("""
                <class name="com/example/Foo" sourcefilename="Foo.java">
                  <method name="load" desc="()V" line="10"/>
                </class>
                <sourcefile name="Foo.java">
                  <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                </sourcefile>
                """);

        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(mainJar, testJar), jacocoReport)).isFalse();
        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(mainJar, testJar),
                dynamicAccessDir,
                jacocoReport,
                Set.of(new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "com.example.FooTestUtil", "helper"))
        );

        assertThat(report.totals().totalCalls()).isEqualTo(1);
        assertThat(report.totals().coveredCalls()).isZero();
    }

    /// The agent key cannot tell sites of `load` apart. Line 99 sits in a method JaCoCo kept, so its
    /// line decides it; the line-less frame shares its key with line-decided sites and is refused.
    @Test
    void agentOriginsNeverCreditAKeyThatAlsoNamesALineDecidedSite() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));
        Path dynamicAccessDir = writeForNameCalls(
                "com.example.Foo.load(Foo.java:10)",
                "com.example.Foo.load(Foo.java:99)",
                "com.example.Foo.load(Unknown Source)"
        );
        Path jacocoReport = writeJacocoReport("""
                <class name="com/example/Foo" sourcefilename="Foo.java">
                  <method name="load" desc="()V" line="10"/>
                </class>
                <sourcefile name="Foo.java">
                  <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                </sourcefile>
                """);

        assertThat(CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, List.of(libraryJar), jacocoReport)).isFalse();
        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                Set.of(new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "com.example.Foo", "load"))
        );

        assertThat(report.totals().totalCalls()).isEqualTo(3);
        assertThat(report.classes().getFirst().callSites())
                .filteredOn(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::frame)
                .containsExactly("com.example.Foo.load(Foo.java:10)");
    }

    @Test
    void agentOriginsNeverOverrideALineJacocoReportsAsMissed() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Legacy.class"));
        Path dynamicAccessDir = writeForNameCalls(
                "com.example.Legacy.class$(Legacy.java:30)",
                "com.example.Legacy.load(Legacy.java:12)"
        );
        Path jacocoReport = writeJacocoReport("""
                <class name="com/example/Legacy" sourcefilename="Legacy.java">
                  <method name="load" desc="()V" line="11"/>
                </class>
                <sourcefile name="Legacy.java">
                  <line nr="11" mi="0" ci="2" mb="0" cb="0"/>
                  <line nr="12" mi="3" ci="0" mb="0" cb="0"/>
                </sourcefile>
                """);

        LibraryStatsModels.DynamicAccessCoverageReport report = CoverageMeasurement.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport,
                Set.of(
                        new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "com.example.Legacy", "class$"),
                        new CoverageMeasurement.AgentCoveredCallSite(FOR_NAME, "com.example.Legacy", "load")
                )
        );

        assertThat(report.totals().totalCalls()).isEqualTo(2);
        assertThat(report.classes().getFirst().callSites())
                .filteredOn(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::frame)
                .containsExactly("com.example.Legacy.class$(Legacy.java:30)");
    }

    private Path writeForNameCalls(String... frames) throws IOException {
        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        String quotedFrames = String.join(",\n    ", List.of(frames).stream().map(frame -> '"' + frame + '"').toList());
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "%s": [
                    %s
                  ]
                }
                """.formatted(FOR_NAME, quotedFrames),
                StandardCharsets.UTF_8
        );
        return dynamicAccessDir;
    }

    /// A JaCoCo XML report whose single `com/example` package holds `packageContent`.
    private Path writeJacocoReport(String packageContent) throws IOException {
        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                %s  </package>
                  <counter type="INSTRUCTION" missed="3" covered="3"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """.formatted(packageContent),
                StandardCharsets.UTF_8
        );
        return jacocoReport;
    }
}
