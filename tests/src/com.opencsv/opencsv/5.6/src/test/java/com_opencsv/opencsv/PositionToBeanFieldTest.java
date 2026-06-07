/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.CsvBindAndJoinByPosition;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvBadConverterException;
import java.io.StringReader;
import java.util.Locale;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;

public class PositionToBeanFieldTest {
    @Test
    void reportsEmptyJoinedPositionRangeDefinition() {
        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<EmptyRangeDefinitionBean>(new StringReader("value\n"))
                        .withType(EmptyRangeDefinitionBean.class)
                        .withErrorLocale(Locale.ROOT)
                        .build())
                .withMessageContaining("range");
    }

    @Test
    void reportsNonNumericJoinedPositionRangeDefinition() {
        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<NonNumericRangeDefinitionBean>(new StringReader("value\n"))
                        .withType(NonNumericRangeDefinitionBean.class)
                        .withErrorLocale(Locale.ROOT)
                        .build())
                .withMessageContaining("range")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    public static class EmptyRangeDefinitionBean {
        @CsvBindAndJoinByPosition(position = "", elementType = String.class)
        private MultiValuedMap<Integer, String> values;
    }

    public static class NonNumericRangeDefinitionBean {
        @CsvBindAndJoinByPosition(position = "not-a-number", elementType = String.class)
        private MultiValuedMap<Integer, String> values;
    }
}
