/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderHeaderAwareBuilder;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class CSVReaderHeaderAwareBuilderTest {
    @Test
    void buildWrapsHeaderReadFailuresWithLocalizedMessage() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CSVReaderHeaderAwareBuilder(new HeaderReadFailureReader())
                        .withErrorLocale(Locale.US)
                        .build())
                .withMessage("It was not possible to initialize a CSVReaderHeaderAware.")
                .withCauseInstanceOf(FileNotFoundException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessage("header source unavailable"));
    }

    private static final class HeaderReadFailureReader extends Reader {
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            throw new FileNotFoundException("header source unavailable");
        }

        @Override
        public void close() {
        }
    }
}
