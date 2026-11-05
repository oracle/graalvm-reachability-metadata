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
import org.junit.jupiter.api.Timeout;

import java.time.DayOfWeek;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ConverterEnumTest {
    @Test
    void convertsCaseInsensitiveNamesAndRejectsUnknownValues() throws Exception {
        ConverterEnum converter = new ConverterEnum(DayOfWeek.class, "", "", Locale.US);
        assertThat(converter.convertToRead("monday")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(converter.convertToWrite(DayOfWeek.FRIDAY)).isEqualTo("FRIDAY");
        assertThatThrownBy(() -> converter.convertToRead("not-a-weekday"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not-a-weekday")
                .hasMessageContaining("DayOfWeek");
    }
}
