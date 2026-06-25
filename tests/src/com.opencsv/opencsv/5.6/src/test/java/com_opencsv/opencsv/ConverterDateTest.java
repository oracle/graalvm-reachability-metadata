/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterDate;
import com.opencsv.exceptions.CsvBadConverterException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Locale;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterDateTest {

    private static final Locale ERROR_LOCALE = Locale.US;
    private static final String ISO_CHRONOLOGY = "ISO";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @Test
    void convertToReadCreatesDateInstancesWithEpochConstructor() throws Exception {
        ConverterDate converter = dateConverter(Date.class, DATE_FORMAT, DATE_FORMAT);

        Object converted = converter.convertToRead("2022-03-04");

        assertThat(converted).isEqualTo(Date.valueOf("2022-03-04"));
    }

    @Test
    void invalidReadPatternIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> dateConverter(Date.class, "yyyy-MM-dd QQQ", DATE_FORMAT))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void invalidWritePatternIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> dateConverter(Date.class, DATE_FORMAT, "yyyy-MM-dd QQQ"))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void invalidChronologyIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> new ConverterDate(
                LocalDate.class,
                Locale.US.toLanguageTag(),
                Locale.US.toLanguageTag(),
                ERROR_LOCALE,
                DATE_FORMAT,
                DATE_FORMAT,
                "not-a-chronology",
                ISO_CHRONOLOGY))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void unsupportedTemporalAccessorTypeIsReportedWhenConverterIsCreated() {
        assertThatThrownBy(() -> new ConverterDate(
                UnsupportedTemporalAccessor.class,
                Locale.US.toLanguageTag(),
                Locale.US.toLanguageTag(),
                ERROR_LOCALE,
                DATE_FORMAT,
                DATE_FORMAT,
                ISO_CHRONOLOGY,
                ISO_CHRONOLOGY))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void unsupportedTypeIsReportedWhenReadingNonBlankValue() {
        ConverterDate converter = dateConverter(String.class, DATE_FORMAT, DATE_FORMAT);

        assertThatThrownBy(() -> converter.convertToRead("2022-03-04"))
                .isInstanceOf(CsvDataTypeMismatchException.class);
    }

    @Test
    void xmlGregorianCalendarFactoryConfigurationProblemsAreReported() {
        ConverterDate converter = dateConverter(XMLGregorianCalendar.class, DATE_FORMAT, DATE_FORMAT);
        String previousFactory = System.getProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY);
        System.setProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY, "example.MissingDatatypeFactory");
        try {
            assertThatThrownBy(() -> converter.convertToRead("2022-03-04"))
                    .isInstanceOf(CsvDataTypeMismatchException.class);
        } finally {
            restoreSystemProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY, previousFactory);
        }
    }

    @Test
    void unsupportedTypeIsReportedWhenWritingNonNullValue() {
        ConverterDate converter = dateConverter(String.class, DATE_FORMAT, DATE_FORMAT);

        assertThatThrownBy(() -> converter.convertToWrite("2022-03-04"))
                .isInstanceOf(CsvDataTypeMismatchException.class);
    }

    private static ConverterDate dateConverter(
            Class<?> type, String readFormat, String writeFormat) {
        return new ConverterDate(
                type,
                Locale.US.toLanguageTag(),
                Locale.US.toLanguageTag(),
                ERROR_LOCALE,
                readFormat,
                writeFormat,
                ISO_CHRONOLOGY,
                ISO_CHRONOLOGY);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    public static class UnsupportedTemporalAccessor implements TemporalAccessor {
        @Override
        public boolean isSupported(TemporalField field) {
            return false;
        }

        @Override
        public long getLong(TemporalField field) {
            throw new UnsupportedTemporalTypeException("Unsupported field");
        }
    }
}
