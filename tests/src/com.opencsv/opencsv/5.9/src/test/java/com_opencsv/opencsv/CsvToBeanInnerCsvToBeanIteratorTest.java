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
import com.opencsv.exceptions.CsvMalformedLineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Iterator;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CsvToBeanInnerCsvToBeanIteratorTest {
    @Test
    void returnsBeansAndRejectsRemoval() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("name\nAda\nGrace\n"))) {
            Iterator<Person> iterator = new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class).withErrorLocale(Locale.US).build().iterator();
            assertThat(iterator.next().name).isEqualTo("Ada");
            assertThatThrownBy(iterator::remove)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("read-only iterator");
            assertThat(iterator.next().name).isEqualTo("Grace");
            assertThat(iterator.hasNext()).isFalse();
        }
    }

    @Test
    void reportsMalformedInputWhileReadingBeans() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("name\n\"unfinished\n"))) {
            CsvToBean<Person> parser = new CsvToBeanBuilder<Person>(reader)
                    .withType(Person.class).withErrorLocale(Locale.US).build();
            assertThatThrownBy(parser::iterator)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error parsing CSV")
                    .hasCauseInstanceOf(CsvMalformedLineException.class);
        }
    }

    public static class Person {
        @CsvBindByName
        public String name;
    }
}
