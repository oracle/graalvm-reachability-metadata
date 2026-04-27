/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.input.BufferedFileChannelInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BufferedFileChannelInputStreamTest {

    @Test
    void readsEntireFileUsingTheFileBuilderAndClosesItsDirectBuffer(@TempDir final Path tempDirectory)
            throws IOException {
        final String fileContents = "0123456789abcdef";
        final Path file = writeFile(tempDirectory, fileContents);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] buffer = new byte[3];

        try (BufferedFileChannelInputStream inputStream = BufferedFileChannelInputStream.builder()
                .setFile(file.toFile())
                .setBufferSize(4)
                .get()) {
            assertThat(inputStream.available()).isEqualTo(4);

            outputStream.write(inputStream.read());
            int read = inputStream.read(buffer, 0, buffer.length);
            while (read != -1) {
                outputStream.write(buffer, 0, read);
                read = inputStream.read(buffer, 0, buffer.length);
            }
        }

        assertThat(new String(outputStream.toByteArray(), StandardCharsets.UTF_8)).isEqualTo(fileContents);
    }

    @Test
    void skipsAcrossBufferedAndChannelBytesUsingThePathBuilder(@TempDir final Path tempDirectory)
            throws IOException {
        final Path file = writeFile(tempDirectory, "abcdefghij");
        final byte[] buffer = new byte[3];

        try (BufferedFileChannelInputStream inputStream = BufferedFileChannelInputStream.builder()
                .setPath(file)
                .setBufferSize(3)
                .get()) {
            assertThat(inputStream.read()).isEqualTo((int) 'a');
            assertThat(inputStream.skip(3)).isEqualTo(3L);
            assertThat(inputStream.read(buffer, 0, buffer.length)).isEqualTo(3);
            assertThat(new String(buffer, StandardCharsets.UTF_8)).isEqualTo("efg");
            assertThat(inputStream.skip(10)).isEqualTo(3L);
            assertThat(inputStream.read()).isEqualTo(-1);
        }
    }

    private static Path writeFile(final Path tempDirectory, final String contents) throws IOException {
        return Files.writeString(tempDirectory.resolve("buffered-file-channel-input-stream.txt"), contents,
                StandardCharsets.UTF_8);
    }
}
