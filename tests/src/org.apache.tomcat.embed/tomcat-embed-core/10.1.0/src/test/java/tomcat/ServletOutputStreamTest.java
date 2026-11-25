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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServletOutputStreamTest {
    @Test
    void printsLatinOneAndReportsUnencodableCharacters() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ServletOutputStream stream = new ServletOutputStream() {
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
                throw new UnsupportedOperationException();
            }
        };
        stream.print("hello");
        stream.println(true);
        assertThat(bytes.toString(StandardCharsets.ISO_8859_1)).isEqualTo("hellotrue\r\n");
        assertThatThrownBy(() -> stream.print("\u20ac")).isInstanceOf(CharConversionException.class)
                .hasMessageContaining("ISO");
        stream.close();
    }
}
