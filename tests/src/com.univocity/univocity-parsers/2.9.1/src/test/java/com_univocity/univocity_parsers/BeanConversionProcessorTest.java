/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Trim;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanConversionProcessorTest {
    @Test
    public void appliesAnnotatedConversionBeforeDefaultNumericConversion() {
        final CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);

        final CsvRoutines routines = new CsvRoutines(settings);
        final List<InventoryRecord> records = routines.parseAll(
                InventoryRecord.class,
                new StringReader("quantity\n 42 \n")
        );

        assertEquals(1, records.size());
        assertEquals(42, records.get(0).quantity);
    }

    public static class InventoryRecord {
        @Trim
        @Parsed(field = "quantity")
        private int quantity;

        public InventoryRecord() {
        }
    }
}
