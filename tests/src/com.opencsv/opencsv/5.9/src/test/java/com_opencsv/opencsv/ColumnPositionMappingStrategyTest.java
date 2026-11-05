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
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ColumnPositionMappingStrategyTest {
    @Test
    void requiresBeanTypeBeforeCapturingColumns() throws Exception {
        ColumnPositionMappingStrategy<Person> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        try (CSVReader reader = new CSVReader(new StringReader("Ada,London\n"))) {
            assertThatThrownBy(() -> strategy.captureHeader(reader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("type");
        }
    }

    @Test
    void mapsPositionsAndRejectsRowsMissingRequiredTrailingFields() throws Exception {
        ColumnPositionMappingStrategy<Person> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(Person.class);
        try (CSVReader reader = new CSVReader(new StringReader("Ada,London\n"))) {
            strategy.captureHeader(reader);
            Person person = strategy.populateNewBean(reader.readNext());
            assertThat(person.name).isEqualTo("Ada");
            assertThat(person.city).isEqualTo("London");
        }
        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"Grace"}))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("city");
    }

    public static class Person {
        @CsvBindByPosition(position = 0)
        public String name;

        @CsvBindByPosition(position = 1, required = true)
        public String city;
    }
}
