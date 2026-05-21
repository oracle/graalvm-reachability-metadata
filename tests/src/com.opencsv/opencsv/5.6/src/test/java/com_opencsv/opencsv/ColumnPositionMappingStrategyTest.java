/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ColumnPositionMappingStrategyTest {
    @Test
    void reportsUnsetTypeWhenCapturingPositionalHeader() throws IOException {
        ColumnPositionMappingStrategy<RequiredPositionBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);

        try (CSVReader reader = csvReader("value\n")) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("type");
        }
    }

    @Test
    void reportsMissingRequiredPositionalFieldAfterHeaderCapture() throws IOException {
        ColumnPositionMappingStrategy<RequiredPositionBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(RequiredPositionBean.class);

        try (CSVReader reader = csvReader("value\n")) {
            strategy.captureHeader(reader);
        }

        assertThatThrownBy(() -> strategy.populateNewBean(new String[0]))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("name");
    }

    private static CSVReader csvReader(String csv) {
        return new CSVReader(new StringReader(csv));
    }

    public static class RequiredPositionBean {
        @CsvBindByPosition(position = 0, required = true)
        public String name;
    }
}
