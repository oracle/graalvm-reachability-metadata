/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.StringReader;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;

public class HeaderNameBaseMappingStrategyTest {
    @Test
    void captureHeaderReportsUnsetBeanType() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();

        try (CSVReader reader = new CSVReader(new StringReader("name\n"))) {
            assertThrows(IllegalStateException.class, () -> strategy.captureHeader(reader));
        }
    }

    @Test
    void captureHeaderReportsMissingRequiredRegexHeader() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredJoinedColumnsBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(RequiredJoinedColumnsBean.class);

        try (CSVReader reader = new CSVReader(new StringReader("other\n"))) {
            assertThrows(CsvRequiredFieldEmptyException.class, () -> strategy.captureHeader(reader));
        }
    }

    @Test
    void populateNewBeanReportsRecordLengthMismatch() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(RequiredNameBean.class);

        try (CSVReader reader = new CSVReader(new StringReader("name\n"))) {
            strategy.captureHeader(reader);
        }

        assertThrows(CsvRequiredFieldEmptyException.class,
                () -> strategy.populateNewBean(new String[] {"Ada", "unexpected"}));
    }

    public static class RequiredNameBean {
        @CsvBindByName(column = "name", required = true)
        private String name;
    }

    public static class RequiredJoinedColumnsBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class, required = true)
        private MultiValuedMap<String, String> values;
    }
}
