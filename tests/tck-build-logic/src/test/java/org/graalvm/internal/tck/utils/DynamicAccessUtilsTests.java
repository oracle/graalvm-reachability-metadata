/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicAccessUtilsTests {

    @TempDir
    Path tempDir;

    @Test
    void buildArgsForClasspathEntriesAddsTrackAndPreserveArgsInStablePathOrder() throws IOException {
        Path secondJar = Files.createFile(tempDir.resolve("b.jar"));
        Path firstJar = Files.createFile(tempDir.resolve("a.jar"));

        assertThat(DynamicAccessUtils.buildArgsForClasspathEntries(List.of(secondJar.toFile(), firstJar.toFile())))
                .containsExactly(
                        "-H:TrackDynamicAccess=path=" + firstJar.toAbsolutePath(),
                        "-H:Preserve=path=" + firstJar.toAbsolutePath(),
                        "-H:TrackDynamicAccess=path=" + secondJar.toAbsolutePath(),
                        "-H:Preserve=path=" + secondJar.toAbsolutePath()
                );
    }

    @Test
    void buildArgsForClasspathEntriesReturnsEmptyListForEmptyClasspath() {
        assertThat(DynamicAccessUtils.buildArgsForClasspathEntries(List.of())).isEmpty();
    }
}
