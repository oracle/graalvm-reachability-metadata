/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.processor.ColumnProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the column-processing subsystem to collect dashboard metrics from a sales feed.
 */
public class CsvColumnAggregationTest {
    @Test
    @Timeout(60)
    public void collectsColumnsByHeaderForSalesDashboard() {
        ColumnProcessor columns = new ColumnProcessor();
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(columns);
        CsvParser parser = new CsvParser(settings);
        String salesFeed = "sale_id,region,units,revenue\n"
                + "S-1001,EMEA,3,129.90\n"
                + "S-1002,AMER,5,245.00\n"
                + "S-1003,EMEA,2,80.50\n";

        parser.parse(new StringReader(salesFeed));

        assertEquals(List.of("sale_id", "region", "units", "revenue"),
                List.of(columns.getHeaders()));
        assertEquals(List.of("S-1001", "S-1002", "S-1003"), columns.getColumn("sale_id"));
        assertEquals(List.of("EMEA", "AMER", "EMEA"), columns.getColumn("region"));
        assertEquals(List.of("3", "5", "2"), columns.getColumn("units"));
        assertEquals(Map.of(
                "sale_id", List.of("S-1001", "S-1002", "S-1003"),
                "region", List.of("EMEA", "AMER", "EMEA"),
                "units", List.of("3", "5", "2"),
                "revenue", List.of("129.90", "245.00", "80.50")),
                columns.getColumnValuesAsMapOfNames());
    }
}
