/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvBindAndJoinByPosition;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FieldMapByPositionTest {
    @Test
    void reportsMissingRequiredJoinedPositionFieldWhenGeneratingHeader() {
        ColumnPositionMappingStrategy<JoinedPositionBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(JoinedPositionBean.class);

        assertThatThrownBy(() -> strategy.generateHeader(new JoinedPositionBean()))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("Header is missing required fields")
                .hasMessageContaining("joinedValues");
    }

    public static class JoinedPositionBean {
        @CsvBindAndJoinByPosition(position = "0-2", required = true, elementType = String.class)
        public MultiValuedMap<Integer, String> joinedValues;
    }
}
