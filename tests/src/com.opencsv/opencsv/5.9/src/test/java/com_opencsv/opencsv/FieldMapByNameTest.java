/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class FieldMapByNameTest {
    @Test
    void requiresValuesToGenerateHeadersForRequiredJoinedFields() throws Exception {
        HeaderColumnNameMappingStrategy<Options> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(Options.class);
        Options bean = new Options();
        bean.options = new ArrayListValuedHashMap<>();
        assertThatThrownBy(() -> strategy.generateHeader(bean))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("options");

        bean.options.put("OPTION_B", "blue");
        bean.options.put("OPTION_A", "amber");
        assertThat(strategy.generateHeader(bean)).containsExactly("OPTION_A", "OPTION_B");
        assertThat(strategy.transmuteBean(bean)).containsExactly("amber", "blue");
    }

    public static class Options {
        @CsvBindAndJoinByName(column = "OPTION_.*", elementType = String.class, required = true)
        public MultiValuedMap<String, String> options;
    }
}
