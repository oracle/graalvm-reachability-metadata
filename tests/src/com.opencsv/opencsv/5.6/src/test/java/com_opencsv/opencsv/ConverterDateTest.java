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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.ValueRange;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterDateTest {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String TEST_LOCALE = "en-US";

    @Test
    void convertsSqlDateUsingPublicLongConstructor() throws CsvDataTypeMismatchException {
        ConverterDate converter = converterFor(java.sql.Date.class, DATE_FORMAT, DATE_FORMAT);

        Object converted = converter.convertToRead("2024-02-29");

        assertThat(converted).isInstanceOf(java.sql.Date.class);
        assertThat(converted.toString()).isEqualTo("2024-02-29");
    }

    @Test
    void reportsInvalidReadDateFormat() {
        assertThatThrownBy(() -> converterFor(java.sql.Date.class, "invalid", DATE_FORMAT))
                .isInstanceOf(CsvBadConverterException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsInvalidWriteDateFormat() {
        assertThatThrownBy(() -> converterFor(java.sql.Date.class, DATE_FORMAT, "invalid"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsUnsupportedTemporalAccessorType() {
        assertThatThrownBy(() -> converterFor(UnsupportedTemporalAccessor.class, DATE_FORMAT, DATE_FORMAT))
                .isInstanceOf(CsvBadConverterException.class);
    }

    @Test
    void reportsUnknownChronology() {
        assertThatThrownBy(() -> new ConverterDate(LocalDate.class, TEST_LOCALE, TEST_LOCALE, Locale.US,
                DATE_FORMAT, DATE_FORMAT, "not-a-chronology", ""))
                .isInstanceOf(CsvBadConverterException.class)
                .hasCauseInstanceOf(DateTimeException.class);
    }

    @Test
    void reportsXmlGregorianCalendarFactoryFailure() {
        String originalFactory = System.getProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY);
        System.setProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY, "com.example.DoesNotExistDatatypeFactory");
        try {
            ConverterDate converter = converterFor(XMLGregorianCalendar.class, DATE_FORMAT, DATE_FORMAT);

            assertThatThrownBy(() -> converter.convertToRead("2024-02-29"))
                    .isInstanceOf(CsvDataTypeMismatchException.class)
                    .hasCauseInstanceOf(DatatypeConfigurationException.class);
        } finally {
            restoreSystemProperty(DatatypeFactory.DATATYPEFACTORY_PROPERTY, originalFactory);
        }
    }

    @Test
    void reportsUnsupportedReadType() {
        ConverterDate converter = converterFor(String.class, DATE_FORMAT, DATE_FORMAT);

        assertThatThrownBy(() -> converter.convertToRead("2024-02-29"))
                .isInstanceOf(CsvDataTypeMismatchException.class);
    }

    @Test
    void reportsUnsupportedWriteType() {
        ConverterDate converter = converterFor(String.class, DATE_FORMAT, DATE_FORMAT);

        assertThatThrownBy(() -> converter.convertToWrite("2024-02-29"))
                .isInstanceOf(CsvDataTypeMismatchException.class);
    }

    private static ConverterDate converterFor(Class<?> type, String readFormat, String writeFormat) {
        return new ConverterDate(type, TEST_LOCALE, TEST_LOCALE, Locale.US,
                readFormat, writeFormat, "", "");
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class UnsupportedTemporalAccessor implements TemporalAccessor {
        @Override
        public boolean isSupported(TemporalField field) {
            return false;
        }

        @Override
        public long getLong(TemporalField field) {
            throw new UnsupportedOperationException("No temporal fields are supported");
        }

        @Override
        public ValueRange range(TemporalField field) {
            throw new UnsupportedOperationException("No temporal fields are supported");
        }

        @Override
        public <R> R query(TemporalQuery<R> query) {
            return TemporalAccessor.super.query(query);
        }
    }
}
