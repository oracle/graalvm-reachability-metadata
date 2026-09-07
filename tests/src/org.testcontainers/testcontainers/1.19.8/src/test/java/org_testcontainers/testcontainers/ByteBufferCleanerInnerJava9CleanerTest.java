/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.shaded.org.apache.commons.io.input.MemoryMappedFileInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufferCleanerInnerJava9CleanerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void releasesMappedBuffersWhenTheInputStreamCloses() throws Exception {
        Path input = temporaryDirectory.resolve("mapped.bin");
        Files.write(input, new byte[] { 1, 2, 3, 4 });

        try (MemoryMappedFileInputStream stream = MemoryMappedFileInputStream.builder().setPath(input).get()) {
            assertThat(stream.readAllBytes()).containsExactly(1, 2, 3, 4);
        }

        assertThat(Files.deleteIfExists(input)).isTrue();
    }
}
