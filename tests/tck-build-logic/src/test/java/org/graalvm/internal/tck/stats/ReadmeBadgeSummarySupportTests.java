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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadmeBadgeSummarySupportTests {

    @TempDir
    Path tempDir;

    @Test
    void buildSummaryAggregatesStatsLibraryListAndArtifactIndexes() throws IOException {
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        Files.createDirectories(statsFile.getParent());
        Files.writeString(
                statsFile,
                """
                {
                  "entries": {
                    "com.example:demo": {
                      "metadataVersions": {
                        "1.0.0": {
                          "versions": [
                            {
                              "dynamicAccess": {
                                "breakdown": {
                                  "reflection": {
                                    "coveredCalls": 1,
                                    "coverageRatio": 0.5,
                                    "totalCalls": 2
                                  }
                                },
                                "coveredCalls": 1,
                                "coverageRatio": 0.5,
                                "totalCalls": 2
                              },
                              "libraryCoverage": {
                                "instruction": {
                                  "covered": 2,
                                  "missed": 1,
                                  "ratio": 0.666667,
                                  "total": 3
                                },
                                "line": {
                                  "covered": 1,
                                  "missed": 0,
                                  "ratio": 1.0,
                                  "total": 1
                                },
                                "method": {
                                  "covered": 1,
                                  "missed": 0,
                                  "ratio": 1.0,
                                  "total": 1
                                }
                              },
                              "version": "1.0.0"
                            }
                          ]
                        }
                      }
                    },
                    "com.example:other": {
                      "metadataVersions": {
                        "2.0.0": {
                          "versions": [
                            {
                              "dynamicAccess": {
                                "breakdown": {
                                },
                                "coveredCalls": 0,
                                "coverageRatio": 1.0,
                                "totalCalls": 0
                              },
                              "libraryCoverage": {
                                "instruction": {
                                  "covered": 1,
                                  "missed": 0,
                                  "ratio": 1.0,
                                  "total": 1
                                },
                                "line": "N/A",
                                "method": {
                                  "covered": 1,
                                  "missed": 0,
                                  "ratio": 1.0,
                                  "total": 1
                                }
                              },
                              "version": "2.0.0"
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """,
                StandardCharsets.UTF_8
        );

        writeIndexFile(
                "com.example",
                "demo",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  },
                  {
                    "metadata-version": "0.9.0",
                    "tested-versions": [
                      "0.9.0"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "com.example",
                "other",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "2.0.0",
                    "tested-versions": [
                      "2.0.0"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "org.example",
                "library",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "0.0.1",
                    "tested-versions": [
                      "0.0.1"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "samples",
                "docker",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "image-pull",
                    "tested-versions": [
                      "image-pull"
                    ]
                  }
                ]
                """
        );

        ReadmeBadgeSummarySupport.ReadmeBadgeSummary summary = ReadmeBadgeSummarySupport.buildSummary(
                statsFile,
                tempDir.resolve("metadata"),
                LocalDate.of(2026, 4, 7)
        );

        assertThat(summary.date()).isEqualTo("2026-04-07");
        assertThat(summary.badges().librariesSupported()).isEqualTo("2");
        assertThat(summary.badges().dynamicAccessCoverage()).isEqualTo("75.0%");
        assertThat(summary.badges().testedLibraryVersions()).isEqualTo("4");
        assertThat(summary.badges().testedLinesOfCode()).isEqualTo("1");

        assertThat(summary.metrics().stats().coverageStatsArtifacts()).isEqualTo(2);
        assertThat(summary.metrics().stats().avgDynamicAccessCoveragePercent()).isEqualByComparingTo("75.0");
        assertThat(summary.metrics().stats().testedLinesOfCode()).isEqualTo(1);
        assertThat(summary.metrics().metadataIndexes().metadataIndexes()).isEqualTo(2);
        assertThat(summary.metrics().metadataIndexes().metadataBaselines()).isEqualTo(3);
        assertThat(summary.metrics().metadataIndexes().latestEntries()).isEqualTo(2);
        assertThat(summary.metrics().metadataIndexes().testedVersions()).isEqualTo(4);

        ReadmeBadgeSummarySupport.ReadmeMetricsHistory updatedHistory = ReadmeBadgeSummarySupport.withSnapshot(
                ReadmeBadgeSummarySupport.loadHistory(tempDir.resolve("history").resolve("history.json")),
                summary
        );
        assertThat(updatedHistory.history()).hasSize(1);
        assertThat(updatedHistory.history().get(0).date()).isEqualTo("2026-04-07");
        assertThat(updatedHistory.history().get(0).metrics().stats().avgDynamicAccessCoveragePercent()).isEqualByComparingTo("75.0");
    }

    @Test
    void withSnapshotReplacesExistingEntryForSameDate() {
        ReadmeBadgeSummarySupport.ReadmeMetricsHistory history = new ReadmeBadgeSummarySupport.ReadmeMetricsHistory(
                List.of(
                        new ReadmeBadgeSummarySupport.HistoryEntry(
                                "2026-04-07",
                                new ReadmeBadgeSummarySupport.HistoryMetrics(
                                        new ReadmeBadgeSummarySupport.MetadataIndexMetrics(4, 5, 6, 7),
                                        new ReadmeBadgeSummarySupport.StatsMetrics(new BigDecimal("9.0"), 10, 11)
                                )
                        )
                )
        );
        ReadmeBadgeSummarySupport.ReadmeBadgeSummary summary = new ReadmeBadgeSummarySupport.ReadmeBadgeSummary(
                "2026-04-07",
                new ReadmeBadgeSummarySupport.BadgeValues("11", "12.0%", "15", "16"),
                new ReadmeBadgeSummarySupport.Metrics(
                        new ReadmeBadgeSummarySupport.MetadataIndexMetrics(20, 21, 22, 24),
                        new ReadmeBadgeSummarySupport.StatsMetrics(new BigDecimal("25.0"), 26, 27)
                )
        );

        ReadmeBadgeSummarySupport.ReadmeMetricsHistory updatedHistory = ReadmeBadgeSummarySupport.withSnapshot(history, summary);
        assertThat(updatedHistory.history()).hasSize(1);
        assertThat(updatedHistory.history().get(0).metrics().stats().avgDynamicAccessCoveragePercent()).isEqualByComparingTo("25.0");
        assertThat(updatedHistory.history().get(0).metrics().metadataIndexes().testedVersions()).isEqualTo(24);
    }

    @Test
    void writeMetricsOverviewGraphGeneratesSvgFromHistory() throws IOException {
        Path graphFile = tempDir.resolve("latest").resolve("metrics-over-time.svg");
        ReadmeBadgeSummarySupport.ReadmeMetricsHistory history = new ReadmeBadgeSummarySupport.ReadmeMetricsHistory(
                List.of(
                        new ReadmeBadgeSummarySupport.HistoryEntry(
                                "2026-04-05",
                                new ReadmeBadgeSummarySupport.HistoryMetrics(
                                        new ReadmeBadgeSummarySupport.MetadataIndexMetrics(4, 5, 6, 8),
                                        new ReadmeBadgeSummarySupport.StatsMetrics(new BigDecimal("31.5"), 9, 120)
                                )
                        ),
                        new ReadmeBadgeSummarySupport.HistoryEntry(
                                "2026-04-06",
                                new ReadmeBadgeSummarySupport.HistoryMetrics(
                                        new ReadmeBadgeSummarySupport.MetadataIndexMetrics(4, 5, 6, 8),
                                        new ReadmeBadgeSummarySupport.StatsMetrics(new BigDecimal("34.0"), 9, 144)
                                )
                        ),
                        new ReadmeBadgeSummarySupport.HistoryEntry(
                                "2026-04-07",
                                new ReadmeBadgeSummarySupport.HistoryMetrics(
                                        new ReadmeBadgeSummarySupport.MetadataIndexMetrics(4, 5, 6, 8),
                                        new ReadmeBadgeSummarySupport.StatsMetrics(new BigDecimal("35.8"), 9, 159)
                                )
                        )
                )
        );

        ReadmeBadgeSummarySupport.writeMetricsOverviewGraph(graphFile, history);

        String svg = Files.readString(graphFile, StandardCharsets.UTF_8);
        assertThat(svg).contains("<svg");
        assertThat(svg).contains("Coverage over time");
        assertThat(svg).contains("Supported libraries");
        assertThat(svg).contains("Libraries with reachability metadata in the repository");
        assertThat(svg).contains("Dynamic access coverage");
        assertThat(svg).contains("Average dynamic-access call coverage across repository metadata");
        assertThat(svg).contains("Tested library versions");
        assertThat(svg).contains("Tested library versions recorded across metadata indexes");
        assertThat(svg).contains("Tested lines of code");
        assertThat(svg).contains("Covered lines reported across library coverage stats");
        assertThat(svg).contains("Updated 2026-04-07");
        assertThat(svg).contains("35.8%");
        assertThat(svg).contains("Apr 2026");
        assertThat(svg).contains("text-anchor=\"end\">Apr 2026</text>");
        assertThat(svg).contains("<path d=\"M ");
        assertThat(svg).contains("<circle");
        assertThat(svg.indexOf("Supported libraries")).isLessThan(svg.indexOf("Tested library versions"));
        assertThat(svg.indexOf("Tested library versions")).isLessThan(svg.indexOf("Dynamic access coverage"));
        assertThat(svg.indexOf("Dynamic access coverage")).isLessThan(svg.indexOf("Tested lines of code"));
    }

    private void writeIndexFile(String groupId, String artifactId, String content) throws IOException {
        Path indexFile = tempDir.resolve("metadata").resolve(groupId).resolve(artifactId).resolve("index.json");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, content, StandardCharsets.UTF_8);
    }
}
