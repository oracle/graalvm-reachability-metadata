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
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvMultilineLimitBrokenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class LineExecutorTest {
    @Test
    void parallelParsingReportsUnterminatedQuotedRecordWithContext() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("name\n\"unfinished\n"))) {
            CsvToBean<Person> parser = new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class).withErrorLocale(Locale.US).build();
            assertThatThrownBy(parser::parse)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error parsing CSV line")
                    .hasMessageContaining("unfinished")
                    .hasCauseInstanceOf(CsvMalformedLineException.class);
        }
    }

    @Test
    void parallelParsingReportsMultilineLimitViolation() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("name\n\"first\nsecond\"\n"))
                .withMultilineLimit(1).build()) {
            CsvToBean<Person> parser = new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class).withErrorLocale(Locale.US).build();
            assertThatThrownBy(parser::parse)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error parsing CSV line")
                    .hasCauseInstanceOf(CsvMultilineLimitBrokenException.class);
        }
    }

    public static class Person {
        @CsvBindByName
        public String name;
    }
}
