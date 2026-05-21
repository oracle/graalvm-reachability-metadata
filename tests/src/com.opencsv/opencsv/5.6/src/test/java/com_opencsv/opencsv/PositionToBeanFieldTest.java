/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.PositionToBeanField;
import com.opencsv.exceptions.CsvBadConverterException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PositionToBeanFieldTest {
    @Test
    void reportsBlankRangeDefinition() {
        assertThatThrownBy(() -> new PositionToBeanField<>("", 3, null, Locale.US))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("range");
    }

    @Test
    void reportsNonNumericRangeDefinition() {
        assertThatThrownBy(() -> new PositionToBeanField<>("1,not-a-number", 3, null, Locale.US))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("not-a-number")
                .hasRootCauseInstanceOf(NumberFormatException.class);
    }
}
