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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                    "module": "com.example:demo",
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
    void getTestDirUsesSharedTestVersionForSupportedVersion() throws IOException {
        TckExtension extension = createExtension(
                """
                [
                  {
                    "latest": true,
                    "module": "com.example:demo",
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

    private TckExtension createExtension(String metadataIndexJson) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(tempDir.resolve("metadata/com.example/demo/index.json"), metadataIndexJson);
        Files.writeString(
                tempDir.resolve("metadata/index.json"),
                """
                [
                  {
                    "directory": "com.example/demo",
                    "module": "com.example:demo"
                  }
                ]
                """
        );
        Files.createDirectories(tempDir.resolve("tests/src/com.example/demo/0.9.0"));
        Files.createDirectories(tempDir.resolve("tests/tck-build-logic"));
        Files.writeString(tempDir.resolve("LICENSE"), "test");

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        return project.getExtensions().create("tck", TckExtension.class, project);
    }
}
