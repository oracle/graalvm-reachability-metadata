/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvToBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CsvToBeanTest {
    @Test
    void reportsMissingStrategyOrReaderWithConfiguredLocale() {
        CsvToBean<SimpleBean> csvToBean = new CsvToBean<>();
        csvToBean.setErrorLocale(Locale.US);

        assertThatThrownBy(() -> csvToBean.parse())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strategy");
    }

    @Test
    void wrapsHeaderCaptureFailureWithConfiguredLocale() throws IOException {
        CsvToBean<SimpleBean> csvToBean = new CsvToBean<>();
        csvToBean.setErrorLocale(Locale.US);
        csvToBean.setMappingStrategy(new ColumnPositionMappingStrategy<SimpleBean>());

        try (CSVReader reader = new CSVReader(new StringReader("name\n"))) {
            csvToBean.setCsvReader(reader);

            assertThatThrownBy(() -> csvToBean.parse())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("CSV header")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    public static class SimpleBean {
        public String name;
    }
}
