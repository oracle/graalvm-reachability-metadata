/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatCode;

class MetadataFilesCheckerTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void runUsesSharedMetadataVersionForSupportedVersion() throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/index.json"),
                """
                [
                  {
                    "latest": true,
                    "module": "com.example:demo",
                    "metadata-version": "1.0.0",
                    "tested-versions": [
                      "1.0.0",
                      "1.0.1"
                    ]
                  }
                ]
                """
        );
        Files.writeString(
                tempDir.resolve("metadata/index.json"),
                """
                [
                  {
                    "directory": "com.example/demo",
                    "module": "com.example:demo",
                    "allowed-packages": [
                      "com.example"
                    ]
                  }
                ]
                """
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        TestMetadataFilesCheckerTask task = project.getTasks().create("checkMetadataFiles", TestMetadataFilesCheckerTask.class);
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    abstract static class TestMetadataFilesCheckerTask extends MetadataFilesCheckerTask {
        @Inject
        public TestMetadataFilesCheckerTask() {
        }
    }
}
