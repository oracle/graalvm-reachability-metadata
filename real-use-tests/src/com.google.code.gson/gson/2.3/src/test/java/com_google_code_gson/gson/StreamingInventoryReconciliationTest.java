/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Gson's streaming JSON subsystem to reconcile warehouse movements and export stock balances.
public class StreamingInventoryReconciliationTest {

    @Test
    @Timeout(60)
    public void importsMovementsAndExportsSortedStockBalances() throws IOException {
        String movements = """
                [
                  {"sku":"book", "delta":-3,
                   "audit":{"operator":"packing", "tags":["sale",null,{"checked":true}]}},
                  {"delta":8, "sku":"pen", "comment":"delivery"},
                  {"sku":"book", "delta":-2},
                  {"sku":"gift \\"box\\"", "delta":6, "optional":null}
                ]
                """;
        StringWriter export = new StringWriter();

        reconcile(new StringReader(movements), export, Map.of("book", 10, "pen", 4));

        assertEquals("""
                [{"sku":"book","quantity":5},{"sku":"gift \\"box\\"","quantity":6},{"sku":"pen","quantity":12}]""",
                export.toString());
    }

    private static void reconcile(Reader source, Writer destination, Map<String, Integer> openingStock)
            throws IOException {
        Map<String, Integer> stock = new TreeMap<>(openingStock);
        try (JsonReader reader = new JsonReader(source)) {
            reader.beginArray();
            while (reader.hasNext()) {
                String sku = null;
                Integer delta = null;
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "sku" -> sku = reader.nextString();
                        case "delta" -> delta = reader.nextInt();
                        default -> reader.skipValue();
                    }
                }
                reader.endObject();
                if (sku == null || delta == null) {
                    throw new IllegalArgumentException("A stock movement requires sku and delta");
                }
                stock.merge(sku, delta, Integer::sum);
            }
            reader.endArray();
            assertEquals(JsonToken.END_DOCUMENT, reader.peek());
        }

        try (JsonWriter writer = new JsonWriter(destination)) {
            writer.beginArray();
            for (Map.Entry<String, Integer> item : stock.entrySet()) {
                writer.beginObject();
                writer.name("sku").value(item.getKey());
                writer.name("quantity").value(item.getValue());
                writer.endObject();
            }
            writer.endArray();
        }
    }
}
