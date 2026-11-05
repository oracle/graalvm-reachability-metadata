/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the CSV record-iteration subsystem to stream a transaction ledger.
 */
public class CsvLedgerStreamingTest {
    @Test
    @Timeout(60)
    public void streamsTypedLedgerRecordsByHeader() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        CsvParser parser = new CsvParser(settings);
        String ledger = "transaction_id,account,quantity,amount,description\n"
                + "TX-1042,AC-17,3,129.90,Replacement parts\n"
                + "TX-1043,AC-19,1,85.50,Consulting \"follow-up\"\n";

        IterableResult<Record, ParsingContext> records = parser.iterateRecords(new StringReader(ledger));
        List<String> transactionSummaries = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (Record record : records) {
            quantities.add(record.getInt("quantity"));
            amounts.add(record.getBigDecimal("amount"));
            transactionSummaries.add(record.getString("transaction_id")
                    + ":" + record.getString("account")
                    + ":" + record.getInt("quantity")
                    + ":" + record.getBigDecimal("amount")
                    + ":" + record.getString("description"));
        }

        assertEquals(List.of(
                "TX-1042:AC-17:3:129.90:Replacement parts",
                "TX-1043:AC-19:1:85.50:Consulting \"follow-up\""), transactionSummaries);
        assertEquals(List.of(3, 1), quantities);
        assertEquals(List.of(new BigDecimal("129.90"), new BigDecimal("85.50")), amounts);
        assertArrayEquals(new String[]{"transaction_id", "account", "quantity", "amount", "description"},
                records.getContext().headers());
    }
}
