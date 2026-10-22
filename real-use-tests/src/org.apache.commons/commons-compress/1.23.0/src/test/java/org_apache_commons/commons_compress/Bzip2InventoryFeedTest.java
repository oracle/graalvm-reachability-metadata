/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the BZIP2 compression subsystem by exporting and importing a warehouse inventory feed.
 */
public class Bzip2InventoryFeedTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void importsCompressedStockMovementsIntoWarehouseBalances() throws IOException, CompressorException {
        String header = "sku,warehouse,quantity,description";
        String deliveries = """
                COFFEE-01,north,2,Caf\u00e9 blend
                FILTER-02,south,3,Paper filters
                COFFEE-01,north,-1,Caf\u00e9 blend
                """;
        int deliveryBatches = 2000;
        Path feed = temporaryDirectory.resolve("stock-movements.csv.bz2");

        // A small BZIP2 block keeps a streaming export's memory use bounded.
        // The feed is large enough to span multiple compression blocks.
        try (BZip2CompressorOutputStream compressed = new BZip2CompressorOutputStream(Files.newOutputStream(feed), 1);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(compressed, StandardCharsets.UTF_8))) {
            writer.write(header);
            writer.write('\n');
            for (int batch = 0; batch < deliveryBatches; batch++) {
                writer.write(deliveries);
            }
        }

        Map<String, Integer> balances = new HashMap<>();
        Map<String, String> descriptions = new HashMap<>();
        int importedMovements = 0;
        // Feed files arrive without a trusted extension, so the importer detects the format.
        try (BufferedInputStream source = new BufferedInputStream(Files.newInputStream(feed));
                CompressorInputStream decompressed = new CompressorStreamFactory().createCompressorInputStream(source);
                BufferedReader reader = new BufferedReader(new InputStreamReader(decompressed, StandardCharsets.UTF_8))) {
            assertEquals(header, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", 4);
                if (columns.length != 4) {
                    throw new IOException("Invalid inventory row: " + line);
                }
                String sku = columns[0];
                String warehouse = columns[1];
                int quantity = Integer.parseInt(columns[2]);
                balances.merge(warehouse + "/" + sku, quantity, Integer::sum);
                descriptions.put(sku, columns[3]);
                importedMovements++;
            }
            assertEquals(-1, decompressed.read());
        }

        assertEquals(6000, importedMovements);
        assertEquals(Map.of("north/COFFEE-01", 2000, "south/FILTER-02", 6000), balances);
        assertEquals(Map.of("COFFEE-01", "Caf\u00e9 blend", "FILTER-02", "Paper filters"), descriptions);
    }
}
