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

        assertThat(versionStats.coordinate()).isEqualTo("com.example:demo:1.0.0");
        assertThat(versionStats.version()).isEqualTo("1.0.0");
        assertThat(versionStats.dynamicAccess().totalCalls()).isEqualTo(3);
        assertThat(versionStats.dynamicAccess().coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().keySet()).containsExactly("reflection", "resource");
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").totalCalls()).isEqualTo(2);
        assertThat(versionStats.dynamicAccess().breakdown().get("reflection").coveredCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resource").totalCalls()).isEqualTo(1);
        assertThat(versionStats.dynamicAccess().breakdown().get("resource").coveredCalls()).isEqualTo(0);
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
        assertThat(versionStats.dynamicAccess().coverageRatio()).isEqualByComparingTo("0");
    }

    @Test
    void mergeStatsReplacesWholeMetadataVersionWhenRequested() {
        LibraryStatsModels.VersionStats existingVersion = new LibraryStatsModels.VersionStats(
                "com.example:demo:1.0.0",
                "1.0.0",
                new LibraryStatsModels.DynamicAccessStats(1, 1, java.math.BigDecimal.ONE, java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE)
                )
        );
        LibraryStatsModels.VersionStats replacement = new LibraryStatsModels.VersionStats(
                "com.example:demo:1.1.0",
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
                "com.example:demo:1.0.0",
                "1.0.0",
                new LibraryStatsModels.DynamicAccessStats(1, 1, java.math.BigDecimal.ONE, java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE),
                        new LibraryStatsModels.CoverageMetric(1, 0, 1, java.math.BigDecimal.ONE)
                )
        );
        LibraryStatsModels.VersionStats untouchedVersion = new LibraryStatsModels.VersionStats(
                "com.example:demo:1.1.0",
                "1.1.0",
                new LibraryStatsModels.DynamicAccessStats(2, 1, new java.math.BigDecimal("0.500000"), java.util.Map.of()),
                new LibraryStatsModels.LibraryCoverage(
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667")),
                        new LibraryStatsModels.CoverageMetric(2, 1, 3, new java.math.BigDecimal("0.666667"))
                )
        );
        LibraryStatsModels.VersionStats replacement = new LibraryStatsModels.VersionStats(
                "com.example:demo:1.0.0",
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
        assertThat(LibraryStatsSupport.requireVersionStats(merged, replacement.coordinate())).isEqualTo(replacement);
    }

    @Test
    void withMetadataVersionStatsStoresArtifactIndexedEntries() {
        LibraryStatsModels.VersionStats firstVersion = createVersionStats("com.example:demo:1.0.0", "1.0.0", 2, 1);
        LibraryStatsModels.VersionStats secondVersion = createVersionStats("com.example:demo:1.1.0", "1.1.0", 3, 2);
        LibraryStatsModels.VersionStats thirdVersion = createVersionStats("org.demo:alpha:0.9.0", "0.9.0", 1, 1);

        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of());
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "demo",
                "10.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "demo",
                "10.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(firstVersion, secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:demo",
                "demo",
                "11.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(secondVersion))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "org.demo:alpha",
                "alpha",
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
                "alpha",
                "1.0.0"
        ).versions()).extracting(LibraryStatsModels.VersionStats::version).containsExactly("0.9.0");
    }

    @Test
    void writeStatsProducesPayloadValidAgainstSchema() throws IOException {
        Path schemaFile = tempDir.resolve("library-stats-schema-v1.0.0.json");
        Files.copy(
                locateRepoFile("stats/schemas/library-stats-schema-v1.0.0.json"),
                schemaFile
        );

        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of(
                "com.example:demo",
                new LibraryStatsModels.ArtifactStats(
                        "demo",
                        Map.of(
                                "1.0.0",
                                new LibraryStatsModels.MetadataVersionStats(
                                        List.of(createVersionStats("com.example:demo:1.0.0", "1.0.0", 1, 1))
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

    private LibraryStatsModels.VersionStats createVersionStats(
            String coordinate,
            String version,
            long totalCalls,
            long coveredCalls
    ) {
        return new LibraryStatsModels.VersionStats(
                coordinate,
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
