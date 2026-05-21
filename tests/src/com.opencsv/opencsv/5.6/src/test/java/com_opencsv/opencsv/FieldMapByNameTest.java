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
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FieldMapByNameTest {
    @Test
    void generateHeaderReportsMissingRequiredJoinFieldUsingConfiguredLocale() {
        HeaderColumnNameMappingStrategy<RequiredJoinBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(RequiredJoinBean.class);

        RequiredJoinBean bean = new RequiredJoinBean();

        assertThatThrownBy(() -> strategy.generateHeader(bean))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessage("Header is missing required fields [tags]. The list of headers encountered is [].");
    }

    public static class RequiredJoinBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class, required = true)
        public MultiValuedMap<String, String> tags;
    }
}
