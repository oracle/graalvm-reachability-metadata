/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HeaderNameBaseMappingStrategyTest {
    @Test
    void reportsUnsetTypeWhenCapturingHeader() throws IOException {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        try (CSVReader reader = csvReader("name\n")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("type");
        }
    }

    @Test
    void reportsMissingRequiredSimpleHeader() throws IOException {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(RequiredNameBean.class);

        try (CSVReader reader = csvReader("other\n")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(CsvRequiredFieldEmptyException.class)
                    .hasMessageContaining("NAME")
                    .hasMessageContaining("other");
        }
    }

    @Test
    void reportsMissingRequiredRegexHeader() throws IOException {
        HeaderColumnNameMappingStrategy<RequiredTagsBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(RequiredTagsBean.class);

        try (CSVReader reader = csvReader("other\n")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(CsvRequiredFieldEmptyException.class)
                    .hasMessageContaining("tag.*")
                    .hasMessageContaining("other");
        }
    }

    @Test
    void reportsDataLineLengthMismatchAgainstCapturedHeader() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(RequiredNameBean.class);

        try (CSVReader reader = csvReader("name\n")) {
            strategy.captureHeader(reader);
        }

        assertThatThrownBy(() -> strategy.verifyLineLength(2))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("header");
    }

    private static CSVReader csvReader(String csv) {
        return new CSVReader(new StringReader(csv));
    }

    public static class RequiredNameBean {
        @CsvBindByName(column = "name", required = true)
        public String name;
    }

    public static class RequiredTagsBean {
        @CsvBindAndJoinByName(column = "tag.*", elementType = String.class, required = true)
        public MultiValuedMap<String, String> tags;
    }
}
