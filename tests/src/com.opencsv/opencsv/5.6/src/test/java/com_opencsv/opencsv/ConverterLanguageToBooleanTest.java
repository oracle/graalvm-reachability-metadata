/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.customconverter.ConvertGermanToBoolean;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.apache.commons.beanutils.ConversionException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterLanguageToBooleanTest {
    @Test
    void loadsLocalizedErrorBundleWhenReadValueIsNotABoolean() throws NoSuchFieldException {
        ExposedGermanConverter converter = new ExposedGermanConverter();

        assertThatThrownBy(() -> converter.convertForRead("vielleicht"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasCauseInstanceOf(ConversionException.class)
                .hasMessageContaining("Input was not a boolean value.");
    }

    @Test
    void loadsLocalizedErrorBundleWhenWrittenValueIsNotABoolean() throws NoSuchFieldException {
        ExposedGermanConverter converter = new ExposedGermanConverter();

        assertThatThrownBy(() -> converter.convertForWrite("ja"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasCauseInstanceOf(ClassCastException.class)
                .hasMessageContaining("The field must be of type Boolean or boolean.");
    }

    private static final class ExposedGermanConverter extends ConvertGermanToBoolean<BooleanBean, String> {
        ExposedGermanConverter() throws NoSuchFieldException {
            setType(BooleanBean.class);
            setField(BooleanBean.class.getDeclaredField("value"));
            setErrorLocale(Locale.US);
        }

        private Object convertForRead(String value) throws CsvDataTypeMismatchException {
            return convert(value);
        }

        private String convertForWrite(Object value) throws CsvDataTypeMismatchException {
            return convertToWrite(value);
        }
    }

    public static class BooleanBean {
        public boolean value;
    }
}
