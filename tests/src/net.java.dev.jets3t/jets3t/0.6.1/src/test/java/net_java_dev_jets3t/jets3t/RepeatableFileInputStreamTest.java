/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jets3t.service.io.RepeatableFileInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RepeatableFileInputStreamTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    public void repeatsReadsFromBeginningWhenResetBeforeMarking() throws IOException {
        File file = writeFile("repeatable-file-stream.txt", "abcdef");

        try (RepeatableFileInputStream stream = new RepeatableFileInputStream(file)) {
            assertThat(stream.markSupported()).isTrue();
            assertThat(stream.getWrappedInputStream()).isNotNull();
            assertThat(readAscii(stream, 3)).isEqualTo("abc");

            stream.reset();

            assertThat(readAscii(stream, 6)).isEqualTo("abcdef");
            assertThat(stream.read()).isEqualTo(-1);
        }
    }

    @Test
    public void repeatsReadsFromLastMarkPoint() throws IOException {
        File file = writeFile("marked-repeatable-file-stream.txt", "0123456789");

        try (RepeatableFileInputStream stream = new RepeatableFileInputStream(file)) {
            assertThat(readAscii(stream, 2)).isEqualTo("01");
            stream.mark(1024);
            assertThat(readAscii(stream, 4)).isEqualTo("2345");

            stream.reset();

            assertThat(readAscii(stream, 4)).isEqualTo("2345");
            stream.mark(1024);
            assertThat(readAscii(stream, 2)).isEqualTo("67");

            stream.reset();

            assertThat(readAscii(stream, 4)).isEqualTo("6789");
        }
    }

    @Test
    public void rejectsNullFile() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RepeatableFileInputStream(null))
            .withMessage("File cannot be null");
    }

    private File writeFile(String fileName, String contents) throws IOException {
        Path file = temporaryDirectory.resolve(fileName);
        Files.write(file, contents.getBytes(StandardCharsets.US_ASCII));
        return file.toFile();
    }

    private static String readAscii(InputStream stream, int count) throws IOException {
        byte[] buffer = new byte[count];
        int bytesRead = stream.read(buffer, 0, buffer.length);
        assertThat(bytesRead).isEqualTo(count);
        return new String(buffer, StandardCharsets.US_ASCII);
    }
}
