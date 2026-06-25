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

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HeaderNameBaseMappingStrategyTest {

    @Test
    void captureHeaderRequiresType() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        try (CSVReader reader = readerForHeader("name")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
        }
    }

    @Test
    void captureHeaderReportsMissingRequiredHeader() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = typedStrategy(RequiredNameBean.class);

        try (CSVReader reader = readerForHeader("other")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(CsvRequiredFieldEmptyException.class)
                    .satisfies(exception -> assertThat(exception.getMessage())
                            .contains("NAME")
                            .contains("other"));
        }
    }

    @Test
    void captureHeaderReportsMissingRequiredRegexHeader() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredRegexBean> strategy = typedStrategy(RequiredRegexBean.class);

        try (CSVReader reader = readerForHeader("other")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(CsvRequiredFieldEmptyException.class)
                    .satisfies(exception -> assertThat(exception.getMessage())
                            .contains("OPTION_.*")
                            .contains("other"));
        }
    }

    @Test
    void verifyLineLengthRejectsDataRowsWithDifferentLengthThanHeader() throws Exception {
        HeaderColumnNameMappingStrategy<RequiredNameBean> strategy = typedStrategy(RequiredNameBean.class);
        try (CSVReader reader = readerForHeader("name")) {
            strategy.captureHeader(reader);
        }

        assertThatThrownBy(() -> strategy.verifyLineLength(2))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    private static <T> HeaderColumnNameMappingStrategy<T> typedStrategy(Class<? extends T> type) {
        HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(type);
        return strategy;
    }

    private static CSVReader readerForHeader(String header) {
        return new CSVReader(new StringReader(header + "\n"));
    }

    public static class RequiredNameBean {
        @CsvBindByName(column = "name", required = true)
        public String name;
    }

    public static class RequiredRegexBean {
        @CsvBindAndJoinByName(column = "OPTION_.*", required = true, elementType = String.class)
        public MultiValuedMap<String, String> options;
    }
}
