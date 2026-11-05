/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class StatefulBeanToCsvTest {
    @Test
    void writesBatchesInInputOrder() throws Exception {
        try (StringWriter output = new StringWriter()) {
            StatefulBeanToCsv<Person> writer = new StatefulBeanToCsvBuilder<Person>(output).build();
            Person first = new Person();
            first.name = "Ada";
            Person second = new Person();
            second.name = "Grace";
            writer.write(List.of(first, second));
            try (CSVReader reader = new CSVReader(new StringReader(output.toString()))) {
                assertThat(reader.readNext()).containsExactly("NAME");
                assertThat(reader.readNext()).containsExactly("Ada");
                assertThat(reader.readNext()).containsExactly("Grace");
                assertThat(reader.readNext()).isNull();
            }
        }
    }

    @Test
    void reportsRequiredValuesMissingFromBatch() throws Exception {
        try (StringWriter output = new StringWriter()) {
            StatefulBeanToCsv<Person> writer = new StatefulBeanToCsvBuilder<Person>(output)
                    .withErrorLocale(Locale.US).build();
            assertThatThrownBy(() -> writer.write(List.of(new Person())))
                    .isInstanceOf(CsvRequiredFieldEmptyException.class)
                    .hasMessageContaining("name");
        }
    }

    public static class Person {
        @CsvBindByName(required = true)
        public String name;
    }
}
