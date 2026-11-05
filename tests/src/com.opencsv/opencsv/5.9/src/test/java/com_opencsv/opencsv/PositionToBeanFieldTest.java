/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvBindAndJoinByPosition;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvBadConverterException;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Iterator;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class PositionToBeanFieldTest {
    @Test
    void joinsValuesFromConfiguredPositionRange() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("north,south,east\n"))) {
            Iterator<RangeBean> beans = new CsvToBeanBuilder<RangeBean>(reader)
                    .withType(RangeBean.class).build().iterator();
            RangeBean bean = beans.next();
            assertThat(bean.values.get(0)).containsExactly("north");
            assertThat(bean.values.get(1)).containsExactly("south");
            assertThat(bean.values.get(2)).containsExactly("east");
            assertThat(beans.hasNext()).isFalse();
        }
    }

    @Test
    void rejectsEmptyRangeInJoinAnnotation() {
        ColumnPositionMappingStrategy<EmptyRangeBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        assertThatThrownBy(() -> strategy.setType(EmptyRangeBean.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("range");
    }

    @Test
    void rejectsNonNumericRangeEndpoints() {
        ColumnPositionMappingStrategy<NonNumericRangeBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        assertThatThrownBy(() -> strategy.setType(NonNumericRangeBean.class))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("0-last")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    public static class RangeBean {
        @CsvBindAndJoinByPosition(position = "0-2", elementType = String.class)
        public MultiValuedMap<Integer, String> values;
    }

    public static class EmptyRangeBean {
        @CsvBindAndJoinByPosition(position = "", elementType = String.class)
        public MultiValuedMap<Integer, String> values;
    }

    public static class NonNumericRangeBean {
        @CsvBindAndJoinByPosition(position = "0-last", elementType = String.class)
        public MultiValuedMap<Integer, String> values;
    }
}
