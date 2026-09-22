/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletOutputStreamTest {

    @Test
    void writesTextUsingServletConvenienceMethods() throws Exception {
        RecordingServletOutputStream output = new RecordingServletOutputStream();

        output.print("Tomcat");
        output.println(11);

        assertThat(output.contents()).isEqualTo("Tomcat11\r\n");
        assertThat(output.isReady()).isTrue();
    }

    private static final class RecordingServletOutputStream extends ServletOutputStream {
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
        public void setWriteListener(WriteListener writeListener) throws IOException {
            writeListener.onWritePossible();
        }

        private String contents() {
            return bytes.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
