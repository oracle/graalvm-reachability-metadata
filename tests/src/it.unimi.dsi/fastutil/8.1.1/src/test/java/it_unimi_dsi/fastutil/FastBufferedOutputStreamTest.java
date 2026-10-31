/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FastBufferedOutputStreamTest {
    @TempDir
    Path tempDir;

    @Test
    void measuresAndRepositionsAFileStream() throws IOException {
        Path file = tempDir.resolve("output.bin");
        try (FastBufferedOutputStream output = new FastBufferedOutputStream(new FileOutputStream(file.toFile()), 4)) {
            output.write(new byte[]{1, 2, 3, 4, 5});
            assertEquals(5L, output.position());
            assertEquals(5L, output.length());
            output.position(1);
            output.write(new byte[]{9, 8});
            assertEquals(3L, output.position());
            assertEquals(5L, output.length());
            output.position(7);
            output.write(6);
            assertEquals(8L, output.length());
        }
        assertArrayEquals(new byte[]{1, 9, 8, 4, 5, 0, 0, 6}, Files.readAllBytes(file));
    }
}
