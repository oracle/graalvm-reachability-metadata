/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.javax_servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;

import org.junit.jupiter.api.Test;

public class ServletOutputStreamTests {
    @Test
    void printMethodsUseLocalizedBundleValues() throws IOException {
        RecordingServletOutputStream outputStream = new RecordingServletOutputStream();

        outputStream.print(true);
        outputStream.print(false);

        assertThat(outputStream.content()).isEqualTo("truefalse");
    }

    @Test
    void printRejectsNonIso88591CharactersWithLocalizedMessage() {
        RecordingServletOutputStream outputStream = new RecordingServletOutputStream();

        assertThatThrownBy(() -> outputStream.print('\u0100'))
                .isInstanceOf(CharConversionException.class)
                .hasMessage("Not an ISO 8859-1 character: \u0100");
    }

    static final class RecordingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int value) {
            bytes.write(value);
        }


        String content() {
            return bytes.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
