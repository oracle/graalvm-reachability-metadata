/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import org.apache.commons.io.input.BufferedFileChannelInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BufferedFileChannelInputStreamTest {
    @Test
    void readsFromTheDirectBufferAndClosesItCleanly(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("buffered-file-channel-input-stream.txt");
        byte[] expected = "BufferedFileChannelInputStream reads across refills".getBytes(StandardCharsets.UTF_8);
        Files.write(file, expected);

        byte[] actual;
        try (BufferedFileChannelInputStream inputStream = new BufferedFileChannelInputStream(file, 4)) {
            assertThat(inputStream.available()).isZero();

            int firstByte = inputStream.read();
            assertThat(firstByte).isEqualTo(expected[0] & 0xFF);
            assertThat(inputStream.available()).isEqualTo(3);

            byte[] remaining = inputStream.readAllBytes();
            actual = new byte[1 + remaining.length];
            actual[0] = (byte) firstByte;
            System.arraycopy(remaining, 0, actual, 1, remaining.length);

            assertThat(inputStream.read()).isEqualTo(-1);
        }

        assertThat(actual).containsExactly(expected);
    }
}
