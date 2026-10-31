/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServletOutputStreamTest {
    @Test
    void printsBooleansAndRejectsCharactersOutsideLatinOne() throws Exception {
        MemoryOutput output = new MemoryOutput();
        output.print(true);
        output.print(':');
        output.println(false);
        assertEquals("true:false\r\n", output.bytes.toString(StandardCharsets.ISO_8859_1));
        assertThrows(CharConversionException.class, () -> output.print("\u20ac"));
    }

    private static class MemoryOutput extends ServletOutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int value) {
            bytes.write(value);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            throw new UnsupportedOperationException("Synchronous output");
        }
    }
}
