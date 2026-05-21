/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.validators.LineValidator;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LineExecutorTest {
    @Test
    void reportsMalformedLineFromBackgroundReaderWithParserContext() throws Exception {
        try (CSVReader reader = csvReader("name\n\"unterminated")) {
            CsvToBean<LineBean> csvToBean = csvToBean(reader);

            assertThatThrownBy(csvToBean::parse)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(CsvMalformedLineException.class)
                    .hasMessageContaining("Error parsing CSV line: 2")
                    .hasMessageContaining("unterminated");
        }
    }

    @Test
    void reportsValidationFailureFromBackgroundReaderWithLineDetails() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("name\nblocked\n"))
                .withLineValidator(new LineValidator() {
                    @Override
                    public boolean isValid(String line) {
                        return !"blocked".equals(line);
                    }

                    @Override
                    public void validate(String line) throws CsvValidationException {
                        if (!isValid(line)) {
                            throw new CsvValidationException("blocked input");
                        }
                    }
                })
                .withErrorLocale(Locale.US)
                .build()) {
            CsvToBean<LineBean> csvToBean = csvToBean(reader);

            assertThatThrownBy(csvToBean::parse)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(CsvValidationException.class)
                    .hasMessageContaining("Error parsing CSV line")
                    .hasMessageContaining("values: null")
                    .satisfies(exception -> assertThat(exception.getCause()).hasMessageContaining("blocked input"));
        }
    }

    private static CSVReader csvReader(String csv) {
        return new CSVReaderBuilder(new StringReader(csv))
                .withErrorLocale(Locale.US)
                .build();
    }

    private static CsvToBean<LineBean> csvToBean(CSVReader reader) {
        HeaderColumnNameMappingStrategy<LineBean> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(LineBean.class);
        strategy.setErrorLocale(Locale.US);

        CsvToBean<LineBean> csvToBean = new CsvToBean<>();
        csvToBean.setCsvReader(reader);
        csvToBean.setMappingStrategy(strategy);
        csvToBean.setErrorLocale(Locale.US);
        return csvToBean;
    }

    public static class LineBean {
        @CsvBindByName(column = "name")
        public String name;
    }
}
