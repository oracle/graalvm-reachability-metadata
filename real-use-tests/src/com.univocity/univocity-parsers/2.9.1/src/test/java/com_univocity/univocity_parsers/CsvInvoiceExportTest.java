/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the CSV writing subsystem to export an invoice report.
 */
public class CsvInvoiceExportTest {
    @Test
    @Timeout(60)
    public void exportsQuotedInvoiceRows() {
        StringWriter output = new StringWriter();
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setQuoteAllFields(true);
        CsvWriter writer = new CsvWriter(output, settings);

        writer.writeHeaders("invoice_id", "customer", "description", "amount");
        writer.writeRow("INV-1042", "Ada Lovelace", "Consulting, March", "120.00");
        writer.writeRow("INV-1043", "Grace Hopper", "Compiler " + '"' + "workshop", "85.50");
        writer.close();

        assertEquals(
                "\"invoice_id\",\"customer\",\"description\",\"amount\"\n"
                        + "\"INV-1042\",\"Ada Lovelace\",\"Consulting, March\",\"120.00\"\n"
                        + "\"INV-1043\",\"Grace Hopper\",\"Compiler \"\"workshop\",\"85.50\"\n",
                output.toString());
    }
}
