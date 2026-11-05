/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.validators.RowMustHaveSameNumberOfColumnsAsFirstRowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises OpenCSV's row-validation pipeline for rejecting malformed supplier records.
public class CsvSupplierFeedValidationTest {
    @Test
    @Timeout(120)
    public void acceptsConsistentRowsAndRejectsTruncatedRecord() throws Exception {
        String incoming = "sku,quantity,status\n"
                + "A-17,12,READY\n"
                + "B-04,7,READY\n"
                + "C-09,5\n";
        List<String[]> acceptedRows = new ArrayList<>();

        CsvValidationException rejected = assertThrows(CsvValidationException.class, () -> {
            try (CSVReader reader = new CSVReaderBuilder(new StringReader(incoming))
                    .withSkipLines(1)
                    .withRowValidator(new RowMustHaveSameNumberOfColumnsAsFirstRowValidator())
                    .build()) {
                String[] row;
                while ((row = reader.readNext()) != null) {
                    acceptedRows.add(row);
                }
            }
        });

        assertEquals(2, acceptedRows.size());
        assertArrayEquals(new String[]{"A-17", "12", "READY"}, acceptedRows.get(0));
        assertArrayEquals(new String[]{"B-04", "7", "READY"}, acceptedRows.get(1));
        assertTrue(rejected.getMessage().contains("expected to have 3 elements"));
    }
}
