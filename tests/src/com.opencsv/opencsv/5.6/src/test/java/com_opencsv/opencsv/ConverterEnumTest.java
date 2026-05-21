/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterEnum;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterEnumTest {
    @Test
    void readsEnumValuesIgnoringCase() throws CsvDataTypeMismatchException {
        ConverterEnum converter = new ConverterEnum(ExampleStatus.class, null, null, Locale.US);

        Object converted = converter.convertToRead("active");

        assertThat(converted).isEqualTo(ExampleStatus.ACTIVE);
    }

    @Test
    void loadsErrorBundleWhenValueIsNotInEnum() {
        ConverterEnum converter = new ConverterEnum(ExampleStatus.class, null, null, Locale.US);

        assertThatThrownBy(() -> converter.convertToRead("archived"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("The value [archived] is not a valid value")
                .hasMessageContaining(ExampleStatus.class.getName());
    }

    @Test
    void writesEnumValuesUsingDeclaredName() {
        ConverterEnum converter = new ConverterEnum(ExampleStatus.class, null, null, Locale.US);

        String converted = converter.convertToWrite(ExampleStatus.INACTIVE);

        assertThat(converted).isEqualTo("INACTIVE");
    }

    private enum ExampleStatus {
        ACTIVE,
        INACTIVE
    }
}
