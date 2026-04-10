/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServletOutputStreamTest {
    @Test
    void printBooleanLoadsServletOutputStreamResources() throws IOException {
        RecordingServletOutputStream outputStream = new RecordingServletOutputStream();

        outputStream.print(true);

        assertEquals("true", outputStream.writtenContent());
    }

    static final class RecordingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            output.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        String writtenContent() {
            return new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
