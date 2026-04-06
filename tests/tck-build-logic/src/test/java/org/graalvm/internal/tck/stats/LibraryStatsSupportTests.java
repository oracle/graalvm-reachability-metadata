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
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LibraryStatsSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void buildVersionStatsParsesDynamicAccessCoverageAndDeduplicatesCallSites() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of(
                "com/example/Foo.class",
                "com/example/Bar.class"
        ));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.noLine(Foo.java)",
                    "java.lang.String.valueOf(String.java:10)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Bar.java:20)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="Bar.java">
                      <line nr="20" mi="1" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="4" covered="6"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="2" covered="3"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.version()).isEqualTo("1.0.0");
        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(3);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().keySet()).containsExactly("reflection", "resources");
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").totalCalls()).isEqualTo(2);
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resources").totalCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resources").coveredCalls()).isEqualTo(0);
        assertThat(versionStats.libraryCoverage().line().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().total()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(6);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(4);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(3);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(2);
    }

    @Test
    void buildVersionStatsAllowsMissingDynamicAccessDirectory() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path missingDynamicAccessDir = tempDir.resolve("dynamic-access-missing");

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                missingDynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().breakdown()).isEmpty();
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("1");
    }

    @Test
    void buildVersionStatsWithoutDynamicAccessPreservesCoverageAndMarksDynamicAccessAsUnavailable() throws IOException {
        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStatsWithoutDynamicAccess(
                "com.example:demo:1.0.0",
                jacocoReport
        );

        assertThat(versionStats.version()).isEqualTo("1.0.0");
        assertThat(versionStats.dynamicAccess().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().line().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().line().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(0);
    }

    @Test
    void buildVersionStatsAllowsJacocoReportsWithoutLineCoverageData() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <class name="com/example/Foo">
                      <method name="load" desc="()V">
                        <counter type="INSTRUCTION" missed="1" covered="2"/>
                        <counter type="METHOD" missed="0" covered="1"/>
                      </method>
                      <counter type="INSTRUCTION" missed="1" covered="2"/>
                      <counter type="METHOD" missed="0" covered="1"/>
                      <counter type="CLASS" missed="0" covered="1"/>
                    </class>
                  </package>
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.buildVersionStats(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(0);
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("0");
        assertThat(versionStats.libraryCoverage().line().isAvailable()).isFalse();
        assertThat(versionStats.libraryCoverage().instruction().covered()).isEqualTo(2);
        assertThat(versionStats.libraryCoverage().instruction().missed()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().covered()).isEqualTo(1);
        assertThat(versionStats.libraryCoverage().method().missed()).isEqualTo(0);
    }

    @Test
    void buildDynamicAccessCoverageReportGroupsCallSitesByClassAndSortsByUncoveredCalls() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of(
                "com/example/Foo.class",
                "com/example/Bar.class"
        ));

        Path dynamicAccessDir = tempDir.resolve("dynamic-access");
        Files.createDirectories(dynamicAccessDir.resolve("demo"));
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("reflection-calls.json"),
                """
                {
                  "java.lang.Class#forName(java.lang.String)": [
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.load(Foo.java:10)",
                    "com.example.Foo.noLine(Foo.java)",
                    "com.example.Bar.load(Bar.java:20)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dynamicAccessDir.resolve("demo").resolve("resource-calls.json"),
                """
                {
                  "java.lang.Class#getResource(java.lang.String)": [
                    "com.example.Bar.lookup(Bar.java:21)"
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <package name="com/example">
                    <sourcefile name="Foo.java">
                      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
                    </sourcefile>
                    <sourcefile name="Bar.java">
                      <line nr="20" mi="1" ci="0" mb="0" cb="0"/>
                      <line nr="21" mi="0" ci="2" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                  <counter type="INSTRUCTION" missed="4" covered="6"/>
                  <counter type="LINE" missed="1" covered="2"/>
                  <counter type="METHOD" missed="2" covered="3"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                dynamicAccessDir,
                jacocoReport
        );

        assertThat(report.coordinate()).isEqualTo("com.example:demo:1.0.0");
        assertThat(report.hasDynamicAccess()).isTrue();
        assertThat(report.totals().totalCalls()).isEqualTo(4);
        assertThat(report.totals().coveredCalls()).isEqualTo(2);
        assertThat(report.classes()).extracting(LibraryStatsModels.DynamicAccessClassCoverage::className)
                .containsExactly("com.example.Bar", "com.example.Foo");
        assertThat(report.classes().get(0).sourceFile()).isEqualTo("Bar.java");
        assertThat(report.classes().get(0).totalCalls()).isEqualTo(2);
        assertThat(report.classes().get(0).coveredCalls()).isEqualTo(1);
        assertThat(report.classes().get(0).callSites())
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::frame)
                .containsExactly(
                        "com.example.Bar.load(Bar.java:20)",
                        "com.example.Bar.lookup(Bar.java:21)"
                );
        assertThat(report.classes().get(0).callSites())
                .extracting(LibraryStatsModels.DynamicAccessCallSiteCoverage::covered)
                .containsExactly(false, true);
        assertThat(report.classes().get(1).totalCalls()).isEqualTo(2);
        assertThat(report.classes().get(1).coveredCalls()).isEqualTo(1);
    }

    @Test
    void buildDynamicAccessCoverageReportReturnsEmptyClassesWhenDynamicAccessDirectoryIsMissing() throws IOException {
        Path libraryJar = createLibraryJar(tempDir.resolve("demo.jar"), List.of("com/example/Foo.class"));

        Path missingDynamicAccessDir = tempDir.resolve("dynamic-access-missing");

        Path jacocoReport = tempDir.resolve("jacoco.xml");
        Files.writeString(
                jacocoReport,
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="INSTRUCTION" missed="1" covered="2"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </report>
                """,
                StandardCharsets.UTF_8
        );

        LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                "com.example:demo:1.0.0",
                List.of(libraryJar),
                missingDynamicAccessDir,
                jacocoReport
        );

        assertThat(report.hasDynamicAccess()).isFalse();
        assertThat(report.totals().totalCalls()).isEqualTo(0);
        assertThat(report.totals().coveredCalls()).isEqualTo(0);
        assertThat(report.classes()).isEmpty();
    }

    @Test
    void mergeStatsReplacesWholeMetadataVersionWhenRequested() {
        LibraryStatsModels.VersionStats existingVersion = new LibraryStatsModels.VersionStats(
                "1.0.0",
                new LibraryStatsModels.DynamicAccessStats(1, 1, java.math.BigDecimal.ONE, java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE)
                )
        );
        LibraryStatsModels.VersionStats replacement = new LibraryStatsModels.VersionStats(
                "1.1.0",
                new LibraryStatsModels.DynamicAccessStats(3, 2, new java.math.BigDecimal("0.666667"), java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000")),
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000")),
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000"))
                )
        );

        LibraryStatsModels.MetadataVersionStats merged = LibraryStatsSupport.mergeStats(
                new LibraryStatsModels.MetadataVersionStats(List.of(existingVersion)),
                List.of(replacement),
                true
        );

        assertThat(merged.versions()).containsExactly(replacement);
    }

    @Test
    void mergeStatsUpdatesSingleVersionAndKeepsExistingOnPartialRefresh() {
        LibraryStatsModels.VersionStats existingVersion = new LibraryStatsModels.VersionStats(
                "1.0.0",
                new LibraryStatsModels.DynamicAccessStats(1, 1, java.math.BigDecimal.ONE, java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE)
                )
        );
        LibraryStatsModels.VersionStats untouchedVersion = new LibraryStatsModels.VersionStats(
                "1.1.0",
                new LibraryStatsModels.DynamicAccessStats(2, 1, new java.math.BigDecimal("0.500000"), java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667"))
                )
        );
        LibraryStatsModels.VersionStats replacement = new LibraryStatsModels.VersionStats(
                "1.0.0",
                new LibraryStatsModels.DynamicAccessStats(3, 2, new java.math.BigDecimal("0.666667"), java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000")),
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000")),
                        new LibraryStatsModels.CoverageMetric(3, 1, 4, new java.math.BigDecimal("0.750000"))
                )
        );

        LibraryStatsModels.MetadataVersionStats merged = LibraryStatsSupport.mergeStats(
                new LibraryStatsModels.MetadataVersionStats(List.of(existingVersion, untouchedVersion)),
                List.of(replacement),
                false
        );

        assertThat(merged.versions()).containsExactly(replacement, untouchedVersion);
        assertThat(LibraryStatsSupport.requireVersionStats(merged, "com.example:demo:1.0.0")).isEqualTo(replacement);
    }

    @Test
    void withMetadataVersionStatsStoresArtifactIndexedEntries() {
        LibraryStatsModels.VersionStats firstVersion = createVersionStats("1.0.0", 2, 1);
        LibraryStatsModels.VersionStats secondVersion = createVersionStats("1.1.0", 3, 2);
        LibraryStatsModels.VersionStats thirdVersion = createVersionStats("0.9.0", 1, 1);

        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of());
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "10.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "10.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(firstVersion, secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "11.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "org.demo:alpha",
                "1.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(thirdVersion))
        );

        assertThat(libraryStats.entries().keySet()).containsExactly("com.example:demo", "org.demo:alpha");
        assertThat(libraryStats.entries().get("com.example:demo").metadataVersions().keySet())
                .containsExactly("10.0.0", "11.0.0");
        assertThat(libraryStats.entries().get("com.example:demo").metadataVersions().get("10.0.0").versions())
                .extracting(LibraryStatsModels.VersionStats::version)
                .containsExactly("1.0.0", "1.1.0");
        assertThat(LibraryStatsSupport.metadataVersionStats(
                libraryStats,
                "org.demo:alpha",
                "1.0.0"
        ).versions()).extracting(LibraryStatsModels.VersionStats::version).containsExactly("0.9.0");
    }

    @Test
    void writeStatsProducesPayloadValidAgainstSchema() throws IOException {
        Path schemaFile = tempDir.resolve("library-stats-schema-v1.0.2.json");
        Files.copy(
                locateRepoFile("stats/schemas/library-stats-schema-v1.0.2.json"),
                schemaFile
        );

        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(createVersionStats("1.0.0", 1, 1))
                                )
                        )
                )
        ));
        Path statsFile = tempDir.resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, libraryStats);

        assertThatCode(() -> LibraryStatsSchemaValidator.validateOrThrow(
                statsFile,
                schemaFile
        )).doesNotThrowAnyException();
    }

    @Test
    void writeStatsSerializesVersionBeforeOtherVersionFields() throws IOException {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(createVersionStats("1.0.0", 1, 1))
                                )
                        )
                )
        ));
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeStats(statsFile, libraryStats);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        int versionIndex = content.indexOf("\"version\"");
        int dynamicAccessIndex = content.indexOf("\"dynamicAccess\"");
        int libraryCoverageIndex = content.indexOf("\"libraryCoverage\"");

        assertThat(versionIndex).isGreaterThanOrEqualTo(0);
        assertThat(dynamicAccessIndex).isGreaterThanOrEqualTo(0);
        assertThat(libraryCoverageIndex).isGreaterThanOrEqualTo(0);
        assertThat(versionIndex).isLessThan(dynamicAccessIndex);
        assertThat(versionIndex).isLessThan(libraryCoverageIndex);
    }

    @Test
    void writeStatsSerializesUnavailableCoverageMetricAsNa() throws IOException {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(new LibraryStatsModels.VersionStats(
                                                "1.0.0",
                                                new LibraryStatsModels.DynamicAccessStats(
                                                        1,
                                                        0,
                                                        java.math.BigDecimal.ZERO,
                                                        Map.of()
                                                ),
                                                new LibraryStatsModels.LibraryCoverage(
                                                        LibraryStatsModels.CoverageMetricValue.notAvailable(),
                                                        LibraryStatsModels.CoverageMetricValue.available(
                                                                new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667"))
                                                        ),
                                                        LibraryStatsModels.CoverageMetricValue.available(
                                                                new LibraryStatsModels.CoverageMetric(3, 0, 3, java.math.BigDecimal.ONE)
                                                        )
                                                )
                                        ))
                                )
                        )
                )
        ));
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeStats(statsFile, libraryStats);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"line\" : \"N/A\"");
    }

    @Test
    void writeStatsSerializesUnavailableDynamicAccessAsNa() throws IOException {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(new LibraryStatsModels.VersionStats(
                                                "1.0.0",
                                                LibraryStatsModels.DynamicAccessStatsValue.notAvailable(),
                                                new LibraryStatsModels.LibraryCoverage(
                                                        LibraryStatsModels.CoverageMetricValue.available(
                                                                new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667"))
                                                        ),
                                                        LibraryStatsModels.CoverageMetricValue.available(
                                                                new LibraryStatsModels.CoverageMetric(3, 0, 3, java.math.BigDecimal.ONE)
                                                        ),
                                                        LibraryStatsModels.CoverageMetricValue.available(
                                                                new LibraryStatsModels.CoverageMetric(4, 0, 4, java.math.BigDecimal.ONE)
                                                        )
                                                )
                                        ))
                                )
                        )
                )
        ));
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeStats(statsFile, libraryStats);
        LibraryStatsModels.LibraryStats loadedStats = LibraryStatsSupport.loadStats(statsFile);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"dynamicAccess\" : \"N/A\"");
        assertThat(LibraryStatsSupport.requireVersionStats(
                LibraryStatsSupport.metadataVersionStats(loadedStats, "com.example:demo", "1.0.0"),
                "com.example:demo:1.0.0"
        ).dynamicAccess().isAvailable()).isFalse();
    }

    @Test
    void writeStatsCanonicalizesTrailingZeroRatios() throws IOException {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(new LibraryStatsModels.VersionStats(
                                                "1.0.0",
                                                new LibraryStatsModels.DynamicAccessStats(
                                                        13,
                                                        13,
                                                        new java.math.BigDecimal("1.000000"),
                                                        Map.of(
                                                                "reflection",
                                                                new LibraryStatsModels.DynamicAccessBreakdown(
                                                                        9,
                                                                        9,
                                                                        new java.math.BigDecimal("1.000000")
                                                                ),
                                                                "resources",
                                                                new LibraryStatsModels.DynamicAccessBreakdown(
                                                                        4,
                                                                        4,
                                                                        new java.math.BigDecimal("0.500000")
                                                                )
                                                        )
                                                ),
                                                new LibraryStatsModels.LibraryCoverage(
                                                        new LibraryStatsModels.CoverageMetric(1, 1, 2, new java.math.BigDecimal("0.500000")),
                                                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                                                        new LibraryStatsModels.CoverageMetric(3, 0, 3, new java.math.BigDecimal("1.000000"))
                                                )
                                        ))
                                )
                        )
                )
        ));
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeStats(statsFile, libraryStats);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"coverageRatio\" : 1.0");
        assertThat(content).contains("\"coverageRatio\" : 0.5");
        assertThat(content).doesNotContain("\"coverageRatio\" : 1.000000");
        assertThat(content).doesNotContain("\"coverageRatio\" : 0.500000");
    }

    private LibraryStatsModels.VersionStats createVersionStats(
            String version,
            long totalCalls,
            long coveredCalls
    ) {
        return new LibraryStatsModels.VersionStats(
                version,
                new LibraryStatsModels.DynamicAccessStats(
                        totalCalls,
                        coveredCalls,
                        new java.math.BigDecimal("0.500000"),
                        Map.of()
                ),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667"))
                )
        );
    }

    private Path createLibraryJar(Path jarPath, List<String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String entry : entries) {
                jarOutputStream.putNextEntry(new JarEntry(entry));
                jarOutputStream.write(new byte[]{0});
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
    }

    private Path locateRepoFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10; i++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        throw new IOException("Failed to locate repository file " + relativePath);
    }
}
