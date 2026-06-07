/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.util.Locale;
import org.apache.commons.beanutils.ConversionException;
import org.junit.jupiter.api.Test;

public class ConverterPrimitiveTypesTest {
    @Test
    void reportsInvalidReadPrimitiveValue() {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(
                Integer.class, null, null, Locale.ENGLISH);

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> converter.convertToRead("not-an-integer"))
                .withMessageContaining("Conversion of not-an-integer to java.lang.Integer failed")
                .withCauseInstanceOf(ConversionException.class);
    }

    @Test
    void reportsUnwritablePrimitiveValue() {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(
                Object.class, null, null, Locale.ENGLISH);

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> converter.convertToWrite(new UnwritableValue()))
                .withMessageContaining("The field must be primitive")
                .withCauseInstanceOf(ConversionException.class);
    }

    private static final class UnwritableValue {
        @Override
        public String toString() {
            throw new IllegalStateException("Value cannot be represented as text");
        }
    }
}
