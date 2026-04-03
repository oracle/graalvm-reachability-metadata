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
                      }
                    }
                  }
                }
                """
        );
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, LibraryStatsSupport.loadStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsMissingMirroredStatsFile() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Missing stats file");
    }

    @Test
    void validateListsEachMissingMetadataVersionWhenArtifactEntryIsAbsent() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        createMetadataVersion("com.example", "demo", "1.1.0");
        writeStatsFile(
                """
                {
                  "entries": {
                  }
                }
                """
        );

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
                      }
                    }
                  }
                }
                """
        );
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, LibraryStatsSupport.loadStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateAcceptsUnavailableDynamicAccess() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                """
                {
                  "entries": {
                    "com.example:demo": {
                      "metadataVersions": {
                        "1.0.0": {
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
                      }
                    }
                  }
                }
                """
        );
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, LibraryStatsSupport.loadStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().create("validateLibraryStats", TestValidateLibraryStatsTask.class);
        assertThatCode(task::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsOrphanArtifactEntry() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                """
                {
                  "entries": {
                    "com.example:other": {
                      "metadataVersions": {
                        "1.0.0": {
                          "versions": []
                        }
                      }
                    }
                  }
                }
                """
        );

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Orphan artifact entry");
    }

    @Test
    void validateRejectsMetadataVersionWithoutVersionReportEntries() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
                """
                {
                  "entries": {
                    "com.example:demo": {
                      "metadataVersions": {
                        "1.0.0": {
                          "versions": []
                        }
                      }
                    }
                  }
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
                """
                {
                  "entries": {
                    "com.example:demo": {
                      "metadataVersions": {
                        "1.0.0": {
                          "versions": []
                        }
                      }
                    }
                  }
                }
                """
        );
        Files.writeString(
                tempDir.resolve("stats").resolve("coverage-stats.json"),
                """
                {
                  "entries": []
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
                """
                {
                  "entries": {
                    "com.example:demo": {
                      "metadataVersions": {
                        "1.0.0": {
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
                      }
                    }
                  }
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
                      }
                    }
                  }
                }
                """
        );
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, LibraryStatsSupport.loadStats(statsFile));

        TestValidateLibraryStatsTask task = project.getTasks().register("validateLibraryStats", TestValidateLibraryStatsTask.class).get();
        assertThatThrownBy(task::validate)
                .hasMessageContaining("Ratio mismatch");
    }

    @Test
    void validateAcceptsRatioWithinTolerance() throws IOException {
        Project project = createProjectSkeleton();
        createMetadataVersion("com.example", "demo", "1.0.0");
        writeStatsFile(
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
                      }
                    }
                  }
                }
                """
        );
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        LibraryStatsSupport.writeStats(statsFile, LibraryStatsSupport.loadStats(statsFile));

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

    private void writeStatsFile(String statsJson) throws IOException {
        Path statsFile = tempDir.resolve("stats").resolve("stats.json");
        Files.createDirectories(statsFile.getParent());
        Files.writeString(statsFile, statsJson, StandardCharsets.UTF_8);
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
