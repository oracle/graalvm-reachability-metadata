/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterNumber;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterNumberTest {

    private static final Locale ERROR_LOCALE = Locale.US;
    private static final String US_LOCALE = "en-US";
    private static final String INTEGER_PATTERN = "#,##0";
    private static final String DECIMAL_PATTERN = "#,##0.00";
    private static final String INVALID_NUMBER_PATTERN = "#.#.#";

    @Test
    void convertToReadParsesBigDecimalWithLocalizedPattern() throws Exception {
        ConverterNumber converter = numberConverter(BigDecimal.class, DECIMAL_PATTERN, DECIMAL_PATTERN);

        Object converted = converter.convertToRead("1,234.50");

        assertThat(converted).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) converted).isEqualByComparingTo("1234.50");
    }

    @Test
    void convertToWriteFormatsNumberWithLocalizedPattern() {
        ConverterNumber converter = numberConverter(BigDecimal.class, DECIMAL_PATTERN, DECIMAL_PATTERN);

        String formatted = converter.convertToWrite(new BigDecimal("1234.5"));

        assertThat(formatted).isEqualTo("1,234.50");
    }

    @Test
    void nonNumberTypeIsRejectedWhenConverterIsCreated() {
        assertThatThrownBy(() -> numberConverter(String.class, INTEGER_PATTERN, INTEGER_PATTERN))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("Number");
    }

    @Test
    void invalidReadPatternIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> numberConverter(Integer.class, INVALID_NUMBER_PATTERN, INTEGER_PATTERN))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void invalidWritePatternIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> numberConverter(Integer.class, INTEGER_PATTERN, INVALID_NUMBER_PATTERN))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void unparsableNumberIsReportedWhenReadingNonBlankValue() {
        ConverterNumber converter = numberConverter(Integer.class, INTEGER_PATTERN, INTEGER_PATTERN);

        assertThatThrownBy(() -> converter.convertToRead("not-a-number"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not-a-number");
    }

    private static ConverterNumber numberConverter(
            Class<?> type, String readFormat, String writeFormat) {
        return new ConverterNumber(type, US_LOCALE, US_LOCALE, ERROR_LOCALE, readFormat, writeFormat);
    }
}
