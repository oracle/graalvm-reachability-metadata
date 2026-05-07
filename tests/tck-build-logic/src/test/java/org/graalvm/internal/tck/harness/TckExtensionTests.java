/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TckExtensionTests {

    @TempDir
    Path tempDir;

    @Test
    void getMatchingCoordinatesIncludesVersionsThatReuseMetadataDirectory() throws IOException {
        TckExtension extension = createExtension(
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "1.0.0",
                    "test-version": "0.9.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );

        assertThat(extension.getMatchingCoordinates("com.example:demo"))
                .containsExactlyInAnyOrder("com.example:demo:1.0.0", "com.example:demo:1.0.1");
        assertThat(extension.getMatchingCoordinates("com.example:demo:1.0.1"))
                .containsExactly("com.example:demo:1.0.1");
    }

    @Test
    void getMatchingCoordinatesStrictRequiresExactVersionDirectory() throws IOException {
        TckExtension extension = createExtension(
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "1.0.0",
                    "test-version": "0.9.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );

        assertThat(extension.getMatchingCoordinatesStrict("com.example:demo"))
                .containsExactly("com.example:demo:1.0.0");
        assertThat(extension.getMatchingCoordinatesStrict("com.example:demo:1.0.1"))
                .isEmpty();
    }

    @Test
    void getMatchingCoordinatesIncludesExplicitVersionWhenOnlyTestDirectoryExists() throws IOException {
        writeRepoFile("metadata/com.example/demo/index.json", """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """);
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        TckExtension extension = createExtensionForTempDir();

        assertThat(extension.getMatchingCoordinates("com.example:demo:1.0.0"))
                .containsExactly("com.example:demo:1.0.0");
        assertThat(extension.getMatchingCoordinates("com.example:demo"))
                .isEmpty();
    }

    @Test
    void getMetadataDirReturnsDeclaredDirectoryWhenOnlyTestDirectoryExists() throws IOException {
        writeRepoFile("metadata/com.example/demo/index.json", """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """);
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/1.0.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        TckExtension extension = createExtensionForTempDir();

        assertThat(extension.getMetadataDir("com.example:demo:1.0.0"))
                .isEqualTo(tempDir.resolve("metadata/com.example/demo/1.0.0"));
    }

    @Test
    void getMatchingCoordinatesSkipsNotForNativeImageMarker() throws IOException {
        TckExtension extension = createExtension(
                """
                [
                  {
                    "not-for-native-image": true,
                    "reason": "Scala.js artifact; not a JVM library consumed by native-image."
                  }
                ]
                """
        );

        assertThat(extension.getMatchingCoordinates("com.example:demo")).isEmpty();
        assertThat(extension.getMatchingCoordinatesStrict("com.example:demo")).isEmpty();
    }

    @Test
    void getTestDirUsesSharedTestVersionForSupportedVersion() throws IOException {
        TckExtension extension = createExtension(
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "1.0.0",
                    "test-version": "0.9.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );

        assertThat(extension.getTestDir("com.example:demo:1.0.1"))
                .isEqualTo(tempDir.resolve("tests/src/com.example/demo/0.9.0").toRealPath());
    }

    @Test
    void testedVersionBatchesSplitsVersionsIntoConfiguredBatchSize() throws IOException {
        List<String> testedVersions = new ArrayList<>();
        for (int i = 1; i <= 65; i++) {
            testedVersions.add("1.0." + i);
        }

        TckExtension extension = createExtension("""
                [
                  {
                    "latest": true,
                    "allowed-packages": [
                      "com.example"
                    ],
                    "metadata-version": "1.0.0",
                    "test-version": "0.9.0",
                    "tested-versions": %s
                  }
                ]
                """.formatted(toJsonArray(testedVersions)));

        assertThat(extension.testedVersionBatches("com.example:demo:1.0.0", 30))
                .containsExactly(
                        Map.of(
                                "coordinates", "com.example:demo:1.0.0",
                                "versions", testedVersions.subList(0, 30),
                                "batch", "1/3"
                        ),
                        Map.of(
                                "coordinates", "com.example:demo:1.0.0",
                                "versions", testedVersions.subList(30, 60),
                                "batch", "2/3"
                        ),
                        Map.of(
                                "coordinates", "com.example:demo:1.0.0",
                                "versions", testedVersions.subList(60, 65),
                                "batch", "3/3"
                        )
                );
    }

    @Test
    void diffCoordinatesIgnoresUnrelatedLibrariesWithoutTests() throws Exception {
        writeRepoFile("metadata/com.example/demo/1.0.0/reachability-metadata.json", """
                {
                  "reflection": []
                }
                """);
        writeRepoFile("metadata/com.example/demo/index.json", """
                [
                  {
                    "latest": true,
                    "metadata-version": "1.0.0",
                    "test-version": "0.9.0",
                    "tested-versions": [
                      "1.0.0"
                    ]
                  }
                ]
                """);
        writeRepoFile("metadata/com.example/orphan/2.0.0/reachability-metadata.json", """
                {
                  "reflection": []
                }
                """);
        writeRepoFile("metadata/com.example/orphan/index.json", """
                [
                  {
                    "latest": true,
                    "metadata-version": "2.0.0",
                    "tested-versions": [
                      "2.0.0"
                    ]
                  }
                ]
                """);
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/0.9.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        git("init");
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test User");
        git("add", ".");
        git("commit", "-m", "base");
        String baseCommit = gitOutput("rev-parse", "HEAD");

        writeRepoFile("metadata/com.example/demo/1.0.0/reachability-metadata.json", """
                {
                  "reflection": [
                    {
                      "type": "com.example.Demo"
                    }
                  ]
                }
                """);
        git("add", ".");
        git("commit", "-m", "head");
        String headCommit = gitOutput("rev-parse", "HEAD");

        TckExtension extension = createExtensionForTempDir();

        assertThat(extension.diffCoordinates(baseCommit, headCommit))
                .containsExactly("com.example:demo:1.0.0");
    }

    private TckExtension createExtension(String metadataIndexJson) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(tempDir.resolve("metadata/com.example/demo/index.json"), metadataIndexJson);
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/0.9.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        return createExtensionForTempDir();
    }

    private TckExtension createExtensionForTempDir() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        return project.getExtensions().create("tck", TckExtension.class, project);
    }

    private void writeRepoFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void git(String... args) throws Exception {
        int exitCode = new ProcessBuilder(argsWithGit(args))
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("git command failed: " + String.join(" ", argsWithGit(args)));
        }
    }

    private String gitOutput(String... args) throws Exception {
        Process process = new ProcessBuilder(argsWithGit(args))
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes()).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (exitCode != 0) {
            throw new IllegalStateException("git command failed: " + String.join(" ", argsWithGit(args)));
        }
        return output;
    }

    private String[] argsWithGit(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }

    private static String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .toList()
                .toString();
    }
}
