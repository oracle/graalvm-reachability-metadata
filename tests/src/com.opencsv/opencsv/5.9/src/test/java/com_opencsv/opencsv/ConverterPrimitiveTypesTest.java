/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterPrimitiveTypes;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ConverterPrimitiveTypesTest {
    @Test
    void convertsIntegersAndReportsInvalidInput() throws Exception {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(Integer.class, "", "", Locale.US);
        assertThat(converter.convertToRead("42")).isEqualTo(42);
        assertThat(converter.convertToWrite(42)).isEqualTo("42");
        assertThatThrownBy(() -> converter.convertToRead("not-an-integer"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not-an-integer")
                .hasMessageContaining("Integer");
    }

    @Test
    void rejectsUnsupportedTypesForLocalizedOutput() {
        ConverterPrimitiveTypes converter = new ConverterPrimitiveTypes(Integer.class, "en-US", "en-US", Locale.US);
        assertThatThrownBy(() -> converter.convertToWrite(new Date(0)))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("primitive");
    }
}
