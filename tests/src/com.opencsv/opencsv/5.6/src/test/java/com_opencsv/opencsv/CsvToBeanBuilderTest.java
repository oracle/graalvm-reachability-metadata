/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CsvToBeanBuilderTest {
    @Test
    void readerConstructorRejectsNullReader() {
        assertThrows(IllegalArgumentException.class,
                () -> new CsvToBeanBuilder<Object>((Reader) null));
    }

    @Test
    void csvReaderConstructorRejectsNullCsvReader() {
        assertThrows(IllegalArgumentException.class,
                () -> new CsvToBeanBuilder<Object>((CSVReader) null));
    }

    @Test
    void buildRejectsMissingTypeAndMappingStrategy() {
        CsvToBeanBuilder<Object> builder = new CsvToBeanBuilder<>(
                new StringReader("name\nAlice\n"));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void ignoreFieldRejectsInconsistentClassFieldPair() {
        CsvToBeanBuilder<Object> builder = new CsvToBeanBuilder<Object>(
                new StringReader("name\nAlice\n"))
                .withErrorLocale(Locale.ROOT);

        assertThrows(IllegalArgumentException.class, () -> builder.withIgnoreField(null, null));
    }
}
