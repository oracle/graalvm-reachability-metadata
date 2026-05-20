/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletOutputStreamTest {

    @Test
    void printsLineThroughServletOutputStream() throws Exception {
        CapturingServletOutputStream stream = new CapturingServletOutputStream();

        stream.println("hello");

        assertThat(stream.content()).isEqualTo("hello\r\n");
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {

        private final StringBuilder content = new StringBuilder();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) throws IOException {
            content.append((char) b);
        }

        private String content() {
            return content.toString();
        }
    }
}
