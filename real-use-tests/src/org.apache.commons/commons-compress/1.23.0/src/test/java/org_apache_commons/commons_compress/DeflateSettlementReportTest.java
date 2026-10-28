/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the DEFLATE compression subsystem by exchanging a zlib-wrapped merchant settlement report.
 */
public class DeflateSettlementReportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void importsPaymentsAndRefundsIntoCurrencySeparatedMerchantBalances() throws IOException {
        String report = """
                transaction,merchant,currency,amount
                TX-101,Caf\u00e9,EUR,125.50
                TX-102,Caf\u00e9,EUR,-25.50
                TX-103,Bookshop,EUR,42.25
                TX-104,Bookshop,USD,20.00
                TX-105,Bookshop,USD,-2.75
                """;
        Path download = temporaryDirectory.resolve("daily-settlement.csv.zz");
        DeflateParameters parameters = new DeflateParameters();
        // The exchange format requires the zlib wrapper, rather than a raw DEFLATE bitstream.
        parameters.setWithZlibHeader(true);
        parameters.setCompressionLevel(Deflater.BEST_COMPRESSION);
        try (DeflateCompressorOutputStream output = new DeflateCompressorOutputStream(
                Files.newOutputStream(download), parameters)) {
            output.write(report.getBytes(StandardCharsets.UTF_8));
        }

        // A partner using the standard zlib decoder can consume the exported report unchanged.
        try (InflaterInputStream partner = new InflaterInputStream(Files.newInputStream(download))) {
            assertEquals(report, new String(partner.readAllBytes(), StandardCharsets.UTF_8));
        }

        Map<String, BigDecimal> merchantBalances = new HashMap<>();
        Map<String, BigDecimal> currencyTotals = new HashMap<>();
        Set<String> importedTransactions = new HashSet<>();
        try (DeflateCompressorInputStream decompressed = new DeflateCompressorInputStream(
                Files.newInputStream(download), parameters);
                BufferedReader reader = new BufferedReader(new InputStreamReader(decompressed, StandardCharsets.UTF_8))) {
            assertEquals("transaction,merchant,currency,amount", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", -1);
                if (columns.length != 4 || !importedTransactions.add(columns[0])) {
                    throw new IOException("Invalid or duplicate settlement: " + line);
                }
                BigDecimal amount = new BigDecimal(columns[3]);
                merchantBalances.merge(columns[2] + "/" + columns[1], amount, BigDecimal::add);
                currencyTotals.merge(columns[2], amount, BigDecimal::add);
            }
            assertEquals(-1, decompressed.read());
        }

        assertEquals(Set.of("TX-101", "TX-102", "TX-103", "TX-104", "TX-105"), importedTransactions);
        assertEquals(Map.of(
                "EUR/Caf\u00e9", new BigDecimal("100.00"),
                "EUR/Bookshop", new BigDecimal("42.25"),
                "USD/Bookshop", new BigDecimal("17.25")), merchantBalances);
        assertEquals(Map.of("EUR", new BigDecimal("142.25"), "USD", new BigDecimal("17.25")), currencyTotals);
    }
}
