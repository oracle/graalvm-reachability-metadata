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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryStatsPersistenceTests {

    @TempDir
    Path tempDir;

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
    void writeMetadataVersionStatsProducesPayloadValidAgainstSchema() throws IOException {
        Path schemaFile = tempDir.resolve("library-stats-schema-v1.0.2.json");
        Files.copy(
                locateRepoFile("stats/schemas/library-stats-schema-v1.0.2.json"),
                schemaFile
        );

        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
                List.of(createVersionStats("1.0.0", 1, 1))
        );
        Path statsFile = tempDir.resolve("stats.json");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);

        assertThatCode(() -> LibraryStatsSchemaValidator.validateOrThrow(
                statsFile,
                schemaFile
        )).doesNotThrowAnyException();
    }

    @Test
    void writeMetadataVersionStatsSerializesVersionBeforeOtherVersionFields() throws IOException {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
                List.of(createVersionStats("1.0.0", 1, 1))
        );
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);

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
    void writeMetadataVersionStatsSerializesUnavailableCoverageMetricAsNa() throws IOException {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
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
        );
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"line\" : \"N/A\"");
    }

    @Test
    void writeMetadataVersionStatsSerializesUnavailableDynamicAccessAsNa() throws IOException {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
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
        );
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);
        LibraryStatsModels.MetadataVersionStats loadedStats = LibraryStatsSupport.loadMetadataVersionStats(statsFile);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"dynamicAccess\" : \"N/A\"");
        assertThat(LibraryStatsSupport.requireVersionStats(loadedStats, "com.example:demo:1.0.0").dynamicAccess().isAvailable()).isFalse();
    }

    @Test
    void writeMetadataVersionStatsCanonicalizesTrailingZeroRatios() throws IOException {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
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
        );
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"coverageRatio\" : 1.0");
        assertThat(content).contains("\"coverageRatio\" : 0.5");
        assertThat(content).doesNotContain("\"coverageRatio\" : 1.000000");
        assertThat(content).doesNotContain("\"coverageRatio\" : 0.500000");
    }

    @Test
    void writeMetadataVersionStatsNormalizesZeroTotalCoverageMetricRatioToFullCoverage() throws IOException {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats = new LibraryStatsModels.MetadataVersionStats(
                List.of(new LibraryStatsModels.VersionStats(
                        "1.0.0",
                        LibraryStatsModels.DynamicAccessStatsValue.notAvailable(),
                        new LibraryStatsModels.LibraryCoverage(
                                LibraryStatsModels.CoverageMetricValue.available(
                                        new LibraryStatsModels.CoverageMetric(0, 0, 0, java.math.BigDecimal.ZERO)
                                ),
                                LibraryStatsModels.CoverageMetricValue.available(
                                        new LibraryStatsModels.CoverageMetric(0, 0, 0, java.math.BigDecimal.ZERO)
                                ),
                                LibraryStatsModels.CoverageMetricValue.available(
                                        new LibraryStatsModels.CoverageMetric(0, 0, 0, java.math.BigDecimal.ZERO)
                                )
                        )
                ))
        );
        Path statsFile = tempDir.resolve("stats.json");

        LibraryStatsSupport.writeMetadataVersionStats(statsFile, metadataVersionStats);
        LibraryStatsModels.MetadataVersionStats loadedStats = LibraryStatsSupport.loadMetadataVersionStats(statsFile);
        LibraryStatsModels.VersionStats versionStats = LibraryStatsSupport.requireVersionStats(loadedStats, "com.example:demo:1.0.0");

        String content = Files.readString(statsFile, StandardCharsets.UTF_8);
        assertThat(content).contains("\"ratio\" : 1.0");
        assertThat(content).doesNotContain("\"ratio\" : 0.0");
        assertThat(versionStats.libraryCoverage().line().ratio()).isEqualByComparingTo("1.0");
        assertThat(versionStats.libraryCoverage().instruction().ratio()).isEqualByComparingTo("1.0");
        assertThat(versionStats.libraryCoverage().method().ratio()).isEqualByComparingTo("1.0");
    }

    @Test
    void loadRepositoryStatsAggregatesExplodedStatsTree() throws IOException {
        Path firstStatsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        Path secondStatsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "org.demo", "alpha", "2.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(
                firstStatsFile,
                new LibraryStatsModels.MetadataVersionStats(List.of(createVersionStats("1.0.0", 1, 1)))
        );
        LibraryStatsSupport.writeMetadataVersionStats(
                secondStatsFile,
                new LibraryStatsModels.MetadataVersionStats(List.of(createVersionStats("2.0.1", 2, 1)))
        );

        LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadRepositoryStats(tempDir.resolve("stats"));

        assertThat(libraryStats.entries().keySet()).containsExactly("com.example:demo", "org.demo:alpha");
        assertThat(LibraryStatsSupport.metadataVersionStats(libraryStats, "com.example:demo", "1.0.0").versions())
                .extracting(LibraryStatsModels.VersionStats::version)
                .containsExactly("1.0.0");
        assertThat(LibraryStatsSupport.metadataVersionStats(libraryStats, "org.demo:alpha", "2.0.0").versions())
                .extracting(LibraryStatsModels.VersionStats::version)
                .containsExactly("2.0.1");
    }

    @Test
    void topCoordinatesByMetricRanksDynamicAccessAndDeduplicatesCoordinates() {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of());
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:alpha",
                "1.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(
                        createVersionStats("1.0.0", 10, 3),
                        createVersionStats("1.1.0", 6, 2)
                ))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "com.example:alpha",
                "2.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(createVersionStats("1.0.0", 12, 4)))
        );
        libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                libraryStats,
                "org.demo:beta",
                "1.0.0",
                new LibraryStatsModels.MetadataVersionStats(List.of(
                        createVersionStats("3.0.0", 12, 5),
                        new LibraryStatsModels.VersionStats(
                                "4.0.0",
                                LibraryStatsModels.DynamicAccessStatsValue.notAvailable(),
                                LibraryStatsSupport.unavailableLibraryCoverage()
                        )
                ))
        );

        List<LibraryStatsModels.CoordinateMetric> topCoordinates = LibraryStatsSupport.topCoordinatesByMetric(
                libraryStats,
                LibraryStatsSupport.DYNAMIC_ACCESSES_METRIC,
                3
        );

        assertThat(topCoordinates)
                .extracting(LibraryStatsModels.CoordinateMetric::coordinate)
                .containsExactly("com.example:alpha:1.0.0", "org.demo:beta:3.0.0", "com.example:alpha:1.1.0");
        assertThat(topCoordinates)
                .extracting(LibraryStatsModels.CoordinateMetric::value)
                .containsExactly(12L, 12L, 6L);
    }

    @Test
    void topCoordinatesByMetricRejectsUnsupportedInputs() {
        LibraryStatsModels.LibraryStats libraryStats = new LibraryStatsModels.LibraryStats(Map.of());

        assertThatThrownBy(() -> LibraryStatsSupport.topCoordinatesByMetric(libraryStats, "coverage", 1))
                .hasMessageContaining("Unsupported library stats metric 'coverage'");
        assertThatThrownBy(() -> LibraryStatsSupport.topCoordinatesByMetric(libraryStats, LibraryStatsSupport.DYNAMIC_ACCESSES_METRIC, 0))
                .hasMessageContaining("Top coordinate limit must be positive");
    }

    @Test
    void requireCoordinatesInMetadataAcceptsIndexedTestedVersion() throws IOException {
        writeMetadataIndex("com.example", "demo", "1.0.0", "1.0.0");

        assertThatCode(() -> LibraryStatsSupport.requireCoordinatesInMetadata(
                tempDir.resolve("metadata"),
                List.of("com.example:demo:1.0.0")
        )).doesNotThrowAnyException();
    }

    @Test
    void requireCoordinatesInMetadataRejectsUnknownCoordinates() throws IOException {
        writeMetadataIndex("com.example", "demo", "1.0.0", "1.0.0");

        assertThatThrownBy(() -> LibraryStatsSupport.requireCoordinatesInMetadata(
                tempDir.resolve("metadata"),
                List.of(
                        "com.example:demo:2.0.0",
                        "org.demo:missing:1.0.0",
                        "malformed"
                )
        ))
                .hasMessageContaining("Unknown reachability metadata coordinates: "
                        + "com.example:demo:2.0.0, org.demo:missing:1.0.0, malformed");
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

    private void writeMetadataIndex(
            String group,
            String artifact,
            String metadataVersion,
            String testedVersion
    ) throws IOException {
        Path artifactRoot = tempDir.resolve("metadata").resolve(group).resolve(artifact);
        Files.createDirectories(artifactRoot.resolve(metadataVersion));
        Files.writeString(
                artifactRoot.resolve("index.json"),
                """
                [
                  {
                    "metadata-version": "%s",
                    "tested-versions": [
                      "%s"
                    ]
                  }
                ]
                """.formatted(metadataVersion, testedVersion),
                StandardCharsets.UTF_8
        );
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
