/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeAndPullAllowedDockerImagesTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void resolveTestVersionUsesSharedTestVersionFromIndexEntry() throws IOException {
        Path indexPath = tempDir.resolve("index.json");
        Files.writeString(indexPath, """
                [
                  {
                    "metadata-version": "12.0.31",
                    "test-version": "12.0.31",
                    "tested-versions": [
                      "12.1.0"
                    ]
                  }
                ]
                """);

        assertThat(ComputeAndPullAllowedDockerImagesTask.resolveTestVersion(indexPath, "12.1.0"))
                .isEqualTo("12.0.31");
    }

    @Test
    void resolveTestVersionFallsBackToMetadataVersionWhenTestVersionIsAbsent() throws IOException {
        Path indexPath = tempDir.resolve("index.json");
        Files.writeString(indexPath, """
                [
                  {
                    "metadata-version": "11.0.12",
                    "tested-versions": [
                      "11.0.12"
                    ]
                  }
                ]
                """);

        assertThat(ComputeAndPullAllowedDockerImagesTask.resolveTestVersion(indexPath, "11.0.12"))
                .isEqualTo("11.0.12");
    }
}
