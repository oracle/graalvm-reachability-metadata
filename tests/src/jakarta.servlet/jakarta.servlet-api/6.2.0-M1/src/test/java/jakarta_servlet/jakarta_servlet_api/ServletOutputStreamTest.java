/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import org.junit.jupiter.api.Test;

public class ServletOutputStreamTest {
    @Test
    void printBooleanUsesServletResourceBundleDuringOutputStreamInitialization() throws IOException {
        RecordingServletOutputStream outputStream = new RecordingServletOutputStream();

        outputStream.print(true);
        outputStream.println(false);

        assertThat(outputStream.toIso88591String()).isEqualTo("truefalse\r\n");
    }

    private static final class RecordingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            bytes.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException("Non-blocking writes are not used by this test");
        }

        private String toIso88591String() {
            return bytes.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
