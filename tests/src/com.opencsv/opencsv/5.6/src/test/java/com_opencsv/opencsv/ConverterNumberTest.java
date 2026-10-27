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
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.spi.NumberFormatProvider;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class ConverterNumberTest {

    private static final Locale ERROR_LOCALE = Locale.US;
    private static final Locale NON_DECIMAL_FORMAT_LOCALE = Locale.forLanguageTag("zz-ZZ");
    private static final String REQUIRED_LOCALE_PROVIDER = "SPI";
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
    void nonDecimalNumberFormatProviderIsRejectedWhenConverterIsCreated() {
        assumeFalse(isNativeImageRuntime(),
                "Native-image runtime does not route custom NumberFormatProvider fixtures through NumberFormat");
        assertThat(System.getProperty("java.locale.providers"))
                .contains(REQUIRED_LOCALE_PROVIDER);
        assertThat(NumberFormat.getInstance(NON_DECIMAL_FORMAT_LOCALE))
                .isNotInstanceOf(DecimalFormat.class);

        assertThatThrownBy(() -> new ConverterNumber(
                Integer.class,
                NON_DECIMAL_FORMAT_LOCALE.toLanguageTag(),
                US_LOCALE,
                ERROR_LOCALE,
                INTEGER_PATTERN,
                INTEGER_PATTERN))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("DecimalFormat");
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

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public static class NonDecimalNumberFormatProvider extends NumberFormatProvider {
        @Override
        public Locale[] getAvailableLocales() {
            return new Locale[] {NON_DECIMAL_FORMAT_LOCALE};
        }

        @Override
        public NumberFormat getCurrencyInstance(Locale locale) {
            return getNumberInstance(locale);
        }

        @Override
        public NumberFormat getIntegerInstance(Locale locale) {
            return getNumberInstance(locale);
        }

        @Override
        public NumberFormat getNumberInstance(Locale locale) {
            return new NonDecimalNumberFormat();
        }

        @Override
        public NumberFormat getPercentInstance(Locale locale) {
            return getNumberInstance(locale);
        }
    }

    private static final class NonDecimalNumberFormat extends NumberFormat {
        @Override
        public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(number);
        }

        @Override
        public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
            return toAppendTo.append(number);
        }

        @Override
        public Number parse(String source, ParsePosition parsePosition) {
            parsePosition.setIndex(source.length());
            return 0;
        }
    }
}
