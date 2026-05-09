/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jets3t.service.io.RepeatableInputStream;
import org.jets3t.service.io.UnrecoverableIOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RepeatableInputStreamTest {
    @Test
    public void repeatsBytesReadSinceConstructionAfterReset() throws IOException {
        byte[] source = "abcdef".getBytes(StandardCharsets.US_ASCII);

        try (RepeatableInputStream stream = new RepeatableInputStream(new ByteArrayInputStream(source), 6)) {
            assertThat(stream.markSupported()).isTrue();
            assertThat(stream.getWrappedInputStream()).isInstanceOf(ByteArrayInputStream.class);
            assertThat(stream.available()).isEqualTo(source.length);
            assertThat(readAscii(stream, 3)).isEqualTo("abc");

            stream.reset();

            assertThat(readAscii(stream, 3)).isEqualTo("abc");
            assertThat(readAscii(stream, 3)).isEqualTo("def");
            assertThat(stream.read()).isEqualTo(-1);
        }
    }

    @Test
    public void markKeepsUnreadBufferedBytesRepeatable() throws IOException {
        byte[] source = "0123456789".getBytes(StandardCharsets.US_ASCII);

        try (RepeatableInputStream stream = new RepeatableInputStream(new ByteArrayInputStream(source), 6)) {
            assertThat(readAscii(stream, 2)).isEqualTo("01");
            assertThat(readAscii(stream, 2)).isEqualTo("23");

            stream.mark(1);
            assertThat(readAscii(stream, 3)).isEqualTo("456");

            stream.reset();

            assertThat(readAscii(stream, 3)).isEqualTo("456");
            assertThat(readAscii(stream, 2)).isEqualTo("78");
        }
    }

    @Test
    public void resetFailsAfterMoreBytesThanBufferSizeWereRead() throws IOException {
        byte[] source = "abcdef".getBytes(StandardCharsets.US_ASCII);

        try (RepeatableInputStream stream = new RepeatableInputStream(new ByteArrayInputStream(source), 3)) {
            assertThat(readAscii(stream, 4)).isEqualTo("abcd");

            assertThatExceptionOfType(UnrecoverableIOException.class)
                .isThrownBy(stream::reset)
                .withMessageContaining("exceeding the available buffer size of 3");
        }
    }

    @Test
    public void rejectsNullInputStream() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RepeatableInputStream(null, 1))
            .withMessage("InputStream cannot be null");
    }

    private static String readAscii(InputStream stream, int count) throws IOException {
        byte[] buffer = new byte[count];
        int bytesRead = stream.read(buffer, 0, buffer.length);
        assertThat(bytesRead).isEqualTo(count);
        return new String(buffer, StandardCharsets.US_ASCII);
    }
}
