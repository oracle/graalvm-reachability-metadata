/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FastBufferedInputStreamTest {
    @TempDir
    Path tempDir;

    @Test
    void measuresAndRepositionsAFileStream() throws IOException {
        Path file = tempDir.resolve("input.bin");
        byte[] content = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        Files.write(file, content);

        try (FastBufferedInputStream input = new FastBufferedInputStream(new FileInputStream(file.toFile()), 3)) {
            assertEquals(content.length, input.length());
            assertEquals(10, input.read());
            assertEquals(1L, input.position());
            input.position(8);
            assertEquals(18, input.read());
            assertEquals(9L, input.position());
            input.position(1);
            assertArrayEquals(new byte[]{11, 12, 13}, input.readNBytes(3));
            input.position(content.length);
            assertEquals(-1, input.read());
        }
    }
}
