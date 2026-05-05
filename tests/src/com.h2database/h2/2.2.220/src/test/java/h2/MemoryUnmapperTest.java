/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.h2.engine.SysProperties;
import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    @Test
    void unmapsMappedBufferWithCleanerHack(@TempDir Path temporaryDirectory) throws IOException {
        Path mappedFile = temporaryDirectory.resolve("mapped.dat");
        Files.write(mappedFile, new byte[4096]);

        MappedByteBuffer mappedBuffer;
        try (FileChannel channel = FileChannel.open(mappedFile, READ, WRITE)) {
            mappedBuffer = channel.map(READ_WRITE, 0, 4096);
        }
        mappedBuffer.put(0, (byte) 42);

        assertThat(SysProperties.NIO_CLEANER_HACK).isTrue();
        assertThat(MemoryUnmapper.unmap(mappedBuffer)).isTrue();
    }
}
