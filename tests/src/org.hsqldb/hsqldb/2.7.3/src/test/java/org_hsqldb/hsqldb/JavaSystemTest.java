/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.hsqldb.lib.java.JavaSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSystemTest {
    @Test
    void unmapsWritableMappedBuffer(@TempDir Path temporaryDirectory) throws Exception {
        Path file = temporaryDirectory.resolve("mapped-data.bin");
        Files.write(file, new byte[] {1});

        try (FileChannel channel = FileChannel.open(
                file,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1);

            assertThat(JavaSystem.unmap(buffer)).isNull();
        }
    }
}
