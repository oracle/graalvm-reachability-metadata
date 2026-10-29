/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.impl.internal.DefaultFileProcessor;

public class Aether_implTest {
    @TempDir
    Path tempDir;

    @Test
    void replacesRepositoryFileContentsWithoutLeavingTrailingBytes() throws IOException {
        Path file = tempDir.resolve("artifact.txt");
        Files.writeString(file, "previous artifact contents", StandardCharsets.UTF_8);
        DefaultFileProcessor processor = new DefaultFileProcessor();

        processor.write(file.toFile(), "updated");

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("updated");
    }
}
