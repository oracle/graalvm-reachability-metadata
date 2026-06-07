/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.ConverterEnum;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ConverterEnumTest {
    @Test
    void reportsInvalidEnumValueWhenReading() {
        ConverterEnum converter = new ConverterEnum(Color.class, null, null, Locale.ENGLISH);

        assertThatExceptionOfType(CsvDataTypeMismatchException.class)
                .isThrownBy(() -> converter.convertToRead("purple"))
                .satisfies(exception -> {
                    assertThat(exception.getSourceObject()).isEqualTo("purple");
                    assertThat(exception.getDestinationClass()).isEqualTo(Color.class);
                    assertThat(exception)
                            .hasMessageContaining("purple")
                            .hasMessageContaining(Color.class.getName());
                });
    }

    private enum Color {
        RED,
        GREEN,
        BLUE
    }
}
