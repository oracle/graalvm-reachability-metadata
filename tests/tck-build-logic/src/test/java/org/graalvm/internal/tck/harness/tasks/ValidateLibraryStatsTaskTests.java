/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateLibraryStatsTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void validateAcceptsWellFormedStatsTree() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
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
                          "missed": 1,
                          "ratio": 0.5,
                          "total": 2
                        },
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateAcceptsExecutionMetricsForExistingTestedVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "1.0.0", "com.example:demo:1.0.0", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsExecutionMetricsForUnknownLibrary() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "other", "1.0.0", "com.example:other:1.0.0", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics library has no matching metadata index")
                .hasMessageContaining("com.example:other:1.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsForUntestedVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "2.0.0", "com.example:demo:2.0.0", "2.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics library version is not listed in metadata index tested-versions")
                .hasMessageContaining("com.example:demo:2.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsStatsVersionMismatch() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile("com.example", "demo", "1.0.0", "com.example:demo:1.0.0", "2.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics stats.version mismatch")
                .hasMessageContaining("expected 1.0.0")
                .hasMessageContaining("found 2.0.0");
    }

    @Test
    void validateRejectsExecutionMetricsForUntestedPreviousLibraryVersion() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));
        writeExecutionMetricsFile(
                "com.example",
                "demo",
                "1.0.0",
                "com.example:demo:1.0.0",
                "1.0.0",
                "com.example:demo:0.9.0",
                "0.9.0"
        );

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Execution metrics previous_library version is not listed in metadata index tested-versions")
                .hasMessageContaining("com.example:demo:0.9.0");
    }

    @Test
    void validateRejectsMissingMirroredStatsFile() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Missing metadata-version entry for com.example:demo:1.0.0");
    }

    @Test
    void validateListsEachMissingMetadataVersionWhenStatsFilesAreAbsent() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        createMetadataVersion("com.example", "demo", "1.1.0");

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Missing metadata-version entry for com.example:demo:1.0.0")
                .hasMessageContaining("Missing metadata-version entry for com.example:demo:1.1.0")
                .hasMessageContaining("Add the missing library stats entry with: ./gradlew generateLibraryStats -Pcoordinates=com.example:demo:1.0.0")
                .hasMessageContaining("Add the missing library stats entry with: ./gradlew generateLibraryStats -Pcoordinates=com.example:demo:1.1.0");
    }

    @Test
    void validateAcceptsUnavailableCoverageMetric() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": {
                        "breakdown": {
                          "reflection": {
                            "coveredCalls": 0,
                            "coverageRatio": 0.0,
                            "totalCalls": 2
                          }
                        },
                        "coveredCalls": 0,
                        "coverageRatio": 0.0,
                        "totalCalls": 2
                      },
                      "libraryCoverage": {
                        "instruction": {
                          "covered": 2,
                          "missed": 1,
                          "ratio": 0.666667,
                          "total": 3
                        },
                        "line": "N/A",
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateAcceptsUnavailableDynamicAccess() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
                      "libraryCoverage": {
                        "instruction": {
                          "covered": 2,
                          "missed": 1,
                          "ratio": 0.666667,
                          "total": 3
                        },
                        "line": {
                          "covered": 1,
                          "missed": 1,
                          "ratio": 0.5,
                          "total": 2
                        },
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsOrphanStatsFile() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "other",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Orphan stats file");
    }

    @Test
    void validateRejectsMetadataVersionWithoutVersionReportEntries() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": []
                }
                """
        );

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Missing version report entries");
    }

    @Test
    void validateRejectsUnexpectedJsonFileInStatsRoot() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": "N/A",
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
        Files.writeString(
                tempDir.resolve("stats").resolve("coverage-stats.json"),
                """
                {
                  "versions": []
                }
                """,
                StandardCharsets.UTF_8
        );

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Unexpected JSON file");
    }

    @Test
    void validateRejectsNonNormalizedStatsContent() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "version": "1.0.0",
                      "libraryCoverage": {
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        },
                        "line": {
                          "covered": 1,
                          "missed": 1,
                          "ratio": 0.5,
                          "total": 2
                        },
                        "instruction": {
                          "covered": 2,
                          "missed": 1,
                          "ratio": 0.666667,
                          "total": 3
                        }
                      },
                      "dynamicAccess": {
                        "totalCalls": 2,
                        "coverageRatio": 0.5,
                        "coveredCalls": 1,
                        "breakdown": {
                          "reflection": {
                            "totalCalls": 2,
                            "coverageRatio": 0.5,
                            "coveredCalls": 1
                          }
                        }
                      }
                    }
                  ]
                }
                """
        );

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("not normalized and sorted");
    }

    @Test
    void validateRejectsRatioMismatchBeyondTolerance() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
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
                          }
                        },
                        "coveredCalls": 1,
                        "coverageRatio": 0.25,
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
                          "missed": 1,
                          "ratio": 0.5,
                          "total": 2
                        },
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Ratio mismatch");
    }

    @Test
    void validateAcceptsRatioWithinTolerance() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                "com.example",
                "demo",
                "1.0.0",
                """
                {
                  "versions": [
                    {
                      "dynamicAccess": {
                        "breakdown": {
                          "reflection": {
                            "coveredCalls": 1,
                            "coverageRatio": 0.5000009,
                            "totalCalls": 2
                          }
                        },
                        "coveredCalls": 1,
                        "coverageRatio": 0.5000009,
                        "totalCalls": 2
                      },
                      "libraryCoverage": {
                        "instruction": {
                          "covered": 2,
                          "missed": 1,
                          "ratio": 0.6666673,
                          "total": 3
                        },
                        "line": {
                          "covered": 1,
                          "missed": 1,
                          "ratio": 0.5000009,
                          "total": 2
                        },
                        "method": {
                          "covered": 3,
                          "missed": 0,
                          "ratio": 1.0,
                          "total": 3
                        }
                      },
                      "version": "1.0.0"
                    }
                  ]
                }
                """
        );
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), "com.example", "demo", "1.0.0");
        LibraryStatsSupport.writeMetadataVersionStats(statsFile, LibraryStatsSupport.loadMetadataVersionStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    private Project createProjectSkeleton() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("tests"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.createDirectories(tempDir.resolve("stats/schemas"));
        Files.writeString(tempDir.resolve("LICENSE"), "test", StandardCharsets.UTF_8);
        Files.writeString(
                tempDir.resolve("stats/schemas/library-stats-schema-v1.0.2.json"),
                Files.readString(
                        locateRepoFile("stats/schemas/library-stats-schema-v1.0.2.json"),
                        StandardCharsets.UTF_8
                ),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("stats/schemas/run_metrics_output_schema.json"),
                Files.readString(
                        locateRepoFile("stats/schemas/run_metrics_output_schema.json"),
                        StandardCharsets.UTF_8
                ),
                StandardCharsets.UTF_8
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.getExtensions().create("tck", TckExtension.class, project);
        return project;
    }

    private void createMetadataVersion(String groupId, String artifactId, String metadataVersion) throws IOException {
        Path artifactRoot = tempDir.resolve("metadata").resolve(groupId).resolve(artifactId);
        Files.createDirectories(artifactRoot.resolve(metadataVersion));
        Files.writeString(
                artifactRoot.resolve("index.json"),
                """
                [
                  {
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "%s",
                    "tested-versions": [
                      "%s"
                    ]
                  }
                ]
                """.formatted(metadataVersion, metadataVersion),
                StandardCharsets.UTF_8
        );
    }

    private void writeStatsFile(String groupId, String artifactId, String metadataVersion, String statsJson) throws IOException {
        Path statsFile = LibraryStatsSupport.repositoryStatsFile(tempDir.resolve("stats"), groupId, artifactId, metadataVersion);
        Files.createDirectories(statsFile.getParent());
        Files.writeString(statsFile, statsJson, StandardCharsets.UTF_8);
    }

    private void writeExecutionMetricsFile(
            String groupId,
            String artifactId,
            String version,
            String library,
            String statsVersion
    ) throws IOException {
        writeExecutionMetricsFile(groupId, artifactId, version, library, statsVersion, null, null);
    }

    private void writeExecutionMetricsFile(
            String groupId,
            String artifactId,
            String version,
            String library,
            String statsVersion,
            String previousLibrary,
            String previousLibraryStatsVersion
    ) throws IOException {
        Path metricsFile = tempDir.resolve("stats")
                .resolve(groupId)
                .resolve(artifactId)
                .resolve(version)
                .resolve("execution-metrics.json");
        Files.createDirectories(metricsFile.getParent());
        String previousLibraryFields = "";
        if (previousLibrary != null) {
            previousLibraryFields = """
                    "previous_library": "%s",
                    "previous_library_stats": {
                      "version": "%s"
                    },
                """.formatted(previousLibrary, previousLibraryStatsVersion);
        }
        Files.writeString(
                metricsFile,
                """
                {
                  "add_new_library_support:2026-04-27": {
                    "artifacts": {
                      "metadata_file": "metadata/%s/%s/%s/reachability-metadata.json",
                      "test_file": "tests/src/%s/%s/%s/src/test/java/Test.java"
                    },
                    "library": "%s",
                %s
                    "metrics": {
                      "code_coverage_percent": 0.0,
                      "cost_usd": 0.0,
                      "input_tokens_used": 0,
                      "iterations": 0,
                      "metadata_entries": 0,
                      "output_tokens_used": 0,
                      "tested_library_loc": 0
                    },
                    "stats": {
                      "version": "%s"
                    },
                    "status": "success",
                    "strategy_name": "test",
                    "timestamp": "2026-04-27T20:43:22.869870Z"
                  }
                }
                """.formatted(
                        groupId,
                        artifactId,
                        version,
                        groupId,
                        artifactId,
                        version,
                        library,
                        previousLibraryFields,
                        statsVersion
                ),
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

    abstract static class TestValidateLibraryStatsTask extends ValidateLibraryStatsTask {
        @Inject
        public TestValidateLibraryStatsTask() {
        }
    }
}
