/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.CSVReaderHeaderAwareBuilder;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CSVReaderHeaderAwareBuilderTest {
    @Test
    void buildWrapsHeaderInitializationIOExceptionWithLocalizedMessage() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CSVReaderHeaderAwareBuilder(new UnreadableReader())
                        .withVerifyReader(false)
                        .withErrorLocale(Locale.ROOT)
                        .build())
                .withMessage("It was not possible to initialize a CSVReaderHeaderAware.")
                .withCauseInstanceOf(IOException.class);
    }

    private static final class UnreadableReader extends Reader {
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("header source unavailable");
        }

        @Override
        public void close() {
        }
    }
}
