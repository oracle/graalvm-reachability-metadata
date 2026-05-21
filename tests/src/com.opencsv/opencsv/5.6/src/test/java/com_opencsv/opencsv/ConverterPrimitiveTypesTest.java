/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.apache.commons.beanutils.ConversionException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterPrimitiveTypesTest {
    @Test
    void reportsImpossiblePrimitiveReadConversion() {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(Integer.class, "", "", Locale.US);

        assertThatThrownBy(() -> converter.convertToRead("not-a-number"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasCauseInstanceOf(ConversionException.class)
                .hasMessageContaining("Conversion of not-a-number to java.lang.Integer failed");
    }

    @Test
    void reportsPrimitiveWriteConversionFailure() {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(Integer.class, "", "", Locale.US);

        assertThatThrownBy(() -> converter.convertToWrite(new ValueWithFailingStringConversion()))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasCauseInstanceOf(ConversionException.class)
                .hasMessageContaining("primitive");
    }

    public static class ValueWithFailingStringConversion {
        @Override
        public String toString() {
            throw new UnsupportedOperationException("string conversion is intentionally unavailable");
        }
    }
}
