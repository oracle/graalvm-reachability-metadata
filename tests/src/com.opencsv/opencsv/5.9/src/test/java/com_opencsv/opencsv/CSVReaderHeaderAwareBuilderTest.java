/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderHeaderAwareBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CSVReaderHeaderAwareBuilderTest {
    @Test
    void reportsMalformedHeaderAtConstruction() {
        try (StringReader input = new StringReader("\"unfinished header\n")) {
            CSVReaderHeaderAwareBuilder builder = new CSVReaderHeaderAwareBuilder(input)
                    .withErrorLocale(Locale.US);
            assertThatThrownBy(builder::build)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("CSVReaderHeaderAware")
                    .hasCauseInstanceOf(CsvMalformedLineException.class);
        }
    }
}
