/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.Project;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateLibraryStatsTaskTests extends ValidateLibraryStatsTestSupport {

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
}
