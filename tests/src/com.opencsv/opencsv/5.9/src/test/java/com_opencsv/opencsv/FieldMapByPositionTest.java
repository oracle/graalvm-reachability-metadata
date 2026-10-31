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
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class FieldMapByPositionTest {
    @Test
    void requiresJoinedPositionsBeforeWritingAndPreservesColumnOrder() throws Exception {
        ColumnPositionMappingStrategy<Options> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(Options.class);
        Options bean = new Options();
        bean.options = new ArrayListValuedHashMap<>();
        assertThatThrownBy(() -> strategy.generateHeader(bean))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("options");

        bean.options.put(1, "blue");
        bean.options.put(0, "amber");
        assertThat(strategy.generateHeader(bean)).isEmpty();
        assertThat(strategy.transmuteBean(bean)).containsExactly("amber", "blue");
    }

    public static class Options {
        @CsvBindAndJoinByPosition(position = "0-1", elementType = String.class, required = true)
        public MultiValuedMap<Integer, String> options;
    }
}
