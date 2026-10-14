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
    void printsTextUsingServletEncodingRules() throws Exception {
        RecordingServletOutputStream output = new RecordingServletOutputStream();

        output.print("Tomcat");
        output.println(11);

        assertThat(output.content()).isEqualTo("Tomcat11\r\n");
    }

    private static final class RecordingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
        }

        @Override
        public void write(int value) throws IOException {
            output.write(value);
        }

        private String content() {
            return output.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
