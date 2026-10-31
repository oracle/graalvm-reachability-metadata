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
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CsvToBeanTest {
    @Test
    void requiresReaderAndMappingStrategy() throws Exception {
        CsvToBean<Person> withoutReader = new CsvToBean<>();
        withoutReader.setErrorLocale(Locale.US);
        HeaderColumnNameMappingStrategy<Person> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(Person.class);
        withoutReader.setMappingStrategy(strategy);
        assertThatThrownBy(withoutReader::iterator)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mapping strategy");

        try (CSVReader reader = new CSVReader(new StringReader("name\nAda\n"))) {
            CsvToBean<Person> withoutStrategy = new CsvToBean<>();
            withoutStrategy.setCsvReader(reader);
            withoutStrategy.setErrorLocale(Locale.US);
            assertThatThrownBy(withoutStrategy::iterator)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CSVReader");
        }
    }

    @Test
    void reportsMissingRequiredHeader() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("other\nAda\n"))) {
            CsvToBean<Person> parser = new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class).withErrorLocale(Locale.US).build();
            assertThatThrownBy(parser::iterator)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("header")
                    .hasCauseInstanceOf(CsvRequiredFieldEmptyException.class);
        }
    }

    public static class Person {
        @CsvBindByName(required = true)
        public String name;
    }
}
