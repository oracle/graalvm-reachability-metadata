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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionMetricsSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void buildIndexSplitsExecutionMetricsByLibrary() throws IOException {
        writeStatsFile(
                "com.example",
                "alpha",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": {
                        "breakdown": {
                          "reflection": {
                            "coveredCalls": 1,
                            "coverageRatio": 0.5,
                            "totalCalls": 2
                          },
                          "resources": {
                            "coveredCalls": 2,
                            "coverageRatio": 1.0,
                            "totalCalls": 2
                          }
                        },
                        "coveredCalls": 3,
                        "coverageRatio": 0.75,
                        "totalCalls": 4
                      },
                      "libraryCoverage": {
                        "instruction": {
                          "covered": 8,
                          "missed": 2,
                          "ratio": 0.8,
                          "total": 10
                        },
                        "line": {
                          "covered": 5,
                          "missed": 1,
                          "ratio": 0.833333,
                          "total": 6
                        },
                        "method": {
                          "covered": 2,
                          "missed": 1,
                          "ratio": 0.666667,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    },
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.1"
                    }
                  ]
                }
                """
        );
        writeStatsFile(
                "com.example",
                "empty",
                "1.0.0",
                """
                {
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
                        "instruction": "N/A",
                        "line": "N/A",
                        "method": "N/A"
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        writeIndexFile(
                "com.example",
                "alpha",
                """
                [
                  {
                    "metadata-version": "0.9.0",
                    "tested-versions": [
                      "0.9.0"
                    ]
                  },
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "description": "Alpha library",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "com.example",
                "empty",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "description": "No dynamic access",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "com.example",
                "missing-stats",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "description": "Missing stats",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """
        );
        writeIndexFile(
                "org.example",
                "ignored",
                """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0"
                  }
                ]
                """
        );

        ExecutionMetricsSupport.ExecutionMetricsIndex executionMetricsIndex = ExecutionMetricsSupport.buildIndex(
                tempDir.resolve("stats"),
                tempDir.resolve("metadata"),
                LocalDate.of(2026, 4, 9)
        );

        assertThat(executionMetricsIndex.date()).isEqualTo("2026-04-09");
        assertThat(executionMetricsIndex.executions().keySet())
                .containsExactly("com.example:alpha", "com.example:empty", "com.example:missing-stats");

        ExecutionMetricsSupport.ArtifactExecutionMetrics alpha = executionMetricsIndex.executions().get("com.example:alpha");
        assertThat(alpha.date()).isEqualTo("2026-04-09");
        assertThat(alpha.description()).isEqualTo("Alpha library");
        assertThat(alpha.latestMetadataVersion()).isEqualTo("1.0.0");
        assertThat(alpha.metadataBaselines()).isEqualTo(2);
        assertThat(alpha.testedVersions()).isEqualTo(3);
        assertThat(alpha.executionStatsAvailable()).isTrue();
        assertThat(alpha.dynamicAccess().coveragePercent()).isEqualByComparingTo("75.0");
        assertThat(alpha.dynamicAccess().coveredCalls()).isEqualTo(3);
        assertThat(alpha.dynamicAccess().totalCalls()).isEqualTo(4);
        assertThat(alpha.dynamicAccess().breakdown().get("reflection").coveragePercent()).isEqualByComparingTo("50.0");
        assertThat(alpha.dynamicAccess().breakdown().get("resources").coveragePercent()).isEqualByComparingTo("100.0");
        assertThat(alpha.codeCoverage().line().covered()).isEqualTo(5);
        assertThat(alpha.codeCoverage().line().missed()).isEqualTo(1);
        assertThat(alpha.codeCoverage().line().coverageRatio()).isEqualByComparingTo("0.833333");
        assertThat(alpha.testedLinesOfCode()).isEqualTo(5);

        ExecutionMetricsSupport.ArtifactExecutionMetrics empty = executionMetricsIndex.executions().get("com.example:empty");
        assertThat(empty.dynamicAccess().available()).isTrue();
        assertThat(empty.dynamicAccess().coveragePercent()).isEqualByComparingTo("100.0");
        assertThat(empty.dynamicAccess().totalCalls()).isZero();
        assertThat(empty.codeCoverage().line().available()).isFalse();

        ExecutionMetricsSupport.ArtifactExecutionMetrics missing = executionMetricsIndex.executions().get("com.example:missing-stats");
        assertThat(missing.executionStatsAvailable()).isFalse();
        assertThat(missing.dynamicAccess().available()).isFalse();

        Path statsRoot = tempDir.resolve("stats");
        ExecutionMetricsSupport.writeExecutionMetrics(statsRoot, executionMetricsIndex);
        Path alphaMetricsFile = statsRoot.resolve("com.example").resolve("alpha").resolve("execution-metrics.json");
        String json = Files.readString(alphaMetricsFile, StandardCharsets.UTF_8);
        assertThat(json).contains("\"com.example:alpha\"");
        assertThat(json).contains("\"date\" : \"2026-04-09\"");
        assertThat(json).contains("\"dynamicAccess\"");
        assertThat(json).contains("\"codeCoverage\"");
        assertThat(json).contains("\"latestMetadataVersion\" : \"1.0.0\"");
        assertThat(json).contains("\"testedLinesOfCode\" : 5");
        assertThat(statsRoot.resolve("com.example").resolve("empty").resolve("execution-metrics.json")).isRegularFile();
        assertThat(statsRoot.resolve("com.example").resolve("missing-stats").resolve("execution-metrics.json")).isRegularFile();
    }

    private void writeIndexFile(String groupId, String artifactId, String content) throws IOException {
        Path indexFile = tempDir.resolve("metadata").resolve(groupId).resolve(artifactId).resolve("index.json");
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, content, StandardCharsets.UTF_8);
    }

    private void writeStatsFile(String groupId, String artifactId, String metadataVersion, String content) throws IOException {
        Path statsFile = tempDir.resolve("stats").resolve(groupId).resolve(artifactId).resolve(metadataVersion).resolve("stats.json");
        Files.createDirectories(statsFile.getParent());
        Files.writeString(statsFile, content, StandardCharsets.UTF_8);
    }
}
