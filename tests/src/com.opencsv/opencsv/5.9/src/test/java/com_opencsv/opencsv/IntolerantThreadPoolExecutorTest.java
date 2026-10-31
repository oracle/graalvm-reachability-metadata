/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class IntolerantThreadPoolExecutorTest {
    @Test
    void parallelParsingReportsConversionFailureWithLineAndInput() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("quantity\nnot-a-number\n"))) {
            CsvToBean<Inventory> parser = new CsvToBeanBuilder<Inventory>(reader)
                    .withType(Inventory.class).withErrorLocale(Locale.US).build();
            assertThatThrownBy(parser::parse)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("line: 2")
                    .hasMessageContaining("not-a-number")
                    .hasCauseInstanceOf(CsvDataTypeMismatchException.class);
        }
    }

    public static class Inventory {
        @CsvBindByName
        public int quantity;
    }
}
