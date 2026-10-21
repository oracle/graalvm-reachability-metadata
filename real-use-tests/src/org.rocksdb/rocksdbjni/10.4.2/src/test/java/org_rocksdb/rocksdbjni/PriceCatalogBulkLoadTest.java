/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.EnvOptions;
import org.rocksdb.IngestExternalFileOptions;
import org.rocksdb.LiveFileMetaData;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.SstFileReader;
import org.rocksdb.SstFileReaderIterator;
import org.rocksdb.SstFileWriter;
import org.rocksdb.TableProperties;

/**
 * Bulk loading with external SST files: building sorted table files offline with
 * `SstFileWriter`, inspecting and checksumming them with `SstFileReader` and its
 * `TableProperties`, and ingesting them into a live database with `ingestExternalFile`.
 *
 * A retailer's pricing service serves SKU prices from RocksDB. Every night a feed job turns the
 * supplier's price list into an SST file, a gatekeeper verifies the file before it is handed
 * over, and the service ingests it in one step instead of replaying millions of puts. A later
 * delta file carries price changes and delisted SKUs as tombstones.
 */
public class PriceCatalogBulkLoadTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void ingestsAVerifiedNightlyFeedAndAppliesADeltaWithDelistings() throws Exception {
        Map<String, String> supplierPrices = new LinkedHashMap<>();
        supplierPrices.put("sku-0300", "12.50 EUR");
        supplierPrices.put("sku-0100", "4.99 EUR");
        supplierPrices.put("sku-0200", "7.25 EUR");
        supplierPrices.put("sku-0400", "99.00 EUR");

        Path fullFeed = workDir.resolve("feed-full.sst");
        long fullSize = FeedJob.writeFull(fullFeed, supplierPrices);
        assertTrue(fullSize > 0);
        assertEquals(fullSize, Files.size(fullFeed), "fileSize reports the finished file");

        FeedInspection full = FeedInspection.of(fullFeed);
        assertEquals(4, full.entries());
        assertEquals(0, full.deletions());
        assertEquals("leveldb.BytewiseComparator", full.comparator());
        assertEquals(List.of("sku-0100", "sku-0200", "sku-0300", "sku-0400"),
                List.copyOf(full.contents().keySet()), "the feed job sorts the supplier list");

        try (PricingService service = PricingService.open(workDir.resolve("prices").toString())) {
            service.ingest(fullFeed);
            assertFalse(Files.exists(fullFeed), "a moved feed file now belongs to the database");
            assertEquals(new TreeMap<>(supplierPrices), service.catalog());
            assertTrue(service.db.getLiveFilesMetaData().stream()
                    .map(LiveFileMetaData::fileName)
                    .anyMatch(name -> name.endsWith(".sst")), "the feed is a live table file");

            SortedMap<String, String> delta = new TreeMap<>();
            delta.put("sku-0200", "6.75 EUR");
            delta.put("sku-0300", null);
            delta.put("sku-0500", "18.00 EUR");
            Path deltaFeed = workDir.resolve("feed-delta.sst");
            FeedJob.writeDelta(deltaFeed, delta);

            FeedInspection deltaInspection = FeedInspection.of(deltaFeed);
            assertEquals(3, deltaInspection.entries());
            assertEquals(1, deltaInspection.deletions(), "a delisting is a tombstone");

            service.ingest(deltaFeed);
            Map<String, String> expected = new TreeMap<>();
            expected.put("sku-0100", "4.99 EUR");
            expected.put("sku-0200", "6.75 EUR");
            expected.put("sku-0400", "99.00 EUR");
            expected.put("sku-0500", "18.00 EUR");
            assertEquals(expected, service.catalog());
            assertNull(service.price("sku-0300"));
        }

        try (PricingService reopened = PricingService.open(workDir.resolve("prices").toString())) {
            assertEquals("6.75 EUR", reopened.price("sku-0200"), "ingested data is durable");
            assertEquals(4, reopened.catalog().size());
        }
    }

    @Test
    void ingestedFeedOverridesManualEditsWhileAnOpenSnapshotKeepsTheOldPrices() throws Exception {
        try (PricingService service = PricingService.open(workDir.resolve("prices").toString())) {
            service.setManually("sku-0700", "30.00 EUR");
            service.setManually("sku-0800", "41.00 EUR");

            Snapshot beforeFeed = service.db.getSnapshot();
            try (ReadOptions asOfBeforeFeed = new ReadOptions().setSnapshot(beforeFeed)) {
                Path feed = workDir.resolve("feed-override.sst");
                FeedJob.writeFull(feed, Map.of("sku-0700", "27.50 EUR", "sku-0900", "5.00 EUR"));
                service.ingest(feed);

                assertEquals("27.50 EUR", service.price("sku-0700"),
                        "the feed is newer than the unflushed manual edit");
                assertEquals("41.00 EUR", service.price("sku-0800"));
                assertEquals("5.00 EUR", service.price("sku-0900"));

                assertArrayEquals(bytes("30.00 EUR"),
                        service.db.get(asOfBeforeFeed, bytes("sku-0700")),
                        "a reader holding a snapshot never sees half a feed");
                assertNull(service.db.get(asOfBeforeFeed, bytes("sku-0900")));
            } finally {
                service.db.releaseSnapshot(beforeFeed);
            }
        }
    }

    @Test
    void feedWriterRejectsOutOfOrderKeysAndReaderRejectsAForeignFile() throws Exception {
        Path broken = workDir.resolve("feed-unsorted.sst");
        try (EnvOptions envOptions = new EnvOptions();
                Options options = new Options();
                SstFileWriter writer = new SstFileWriter(envOptions, options)) {
            writer.open(broken.toString());
            writer.put(bytes("sku-0200"), bytes("1.00 EUR"));
            RocksDBException unsorted = assertThrows(RocksDBException.class,
                    () -> writer.put(bytes("sku-0100"), bytes("2.00 EUR")));
            assertTrue(unsorted.getMessage() != null && !unsorted.getMessage().isEmpty());
        }

        Path notATable = workDir.resolve("supplier.csv");
        Files.writeString(notATable, "sku-0100,4.99\nsku-0200,7.25\n");
        assertThrows(RocksDBException.class, () -> FeedInspection.of(notATable));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** The offline feed job that turns supplier price lists into sorted SST files. */
    private static final class FeedJob {

        private FeedJob() {
        }

        static long writeFull(Path file, Map<String, String> prices) throws RocksDBException {
            SortedMap<String, String> sorted = new TreeMap<>(prices);
            return write(file, sorted);
        }

        static long writeDelta(Path file, SortedMap<String, String> changes)
                throws RocksDBException {
            return write(file, changes);
        }

        /** Writes the map in key order; a `null` price delists the SKU. */
        private static long write(Path file, SortedMap<String, String> rows)
                throws RocksDBException {
            try (EnvOptions envOptions = new EnvOptions();
                    Options options = new Options();
                    SstFileWriter writer = new SstFileWriter(envOptions, options)) {
                writer.open(file.toString());
                for (Map.Entry<String, String> row : rows.entrySet()) {
                    if (row.getValue() == null) {
                        writer.delete(bytes(row.getKey()));
                    } else {
                        writer.put(bytes(row.getKey()), bytes(row.getValue()));
                    }
                }
                writer.finish();
                return writer.fileSize();
            }
        }
    }

    /** What the gatekeeper learns from a feed file before it is handed to the service. */
    private record FeedInspection(long entries, long deletions, String comparator,
            SortedMap<String, String> contents) {

        static FeedInspection of(Path file) throws RocksDBException {
            try (Options options = new Options();
                    SstFileReader reader = new SstFileReader(options)) {
                reader.open(file.toString());
                reader.verifyChecksum();
                TableProperties properties = reader.getTableProperties();
                SortedMap<String, String> contents = new TreeMap<>();
                try (ReadOptions readOptions = new ReadOptions();
                        SstFileReaderIterator it = reader.newIterator(readOptions)) {
                    for (it.seekToFirst(); it.isValid(); it.next()) {
                        contents.put(text(it.key()), text(it.value()));
                    }
                }
                return new FeedInspection(properties.getNumEntries(),
                        properties.getNumDeletions(), properties.getComparatorName(), contents);
            }
        }
    }

    /** The online pricing service: one RocksDB instance keyed by SKU. */
    private static final class PricingService implements AutoCloseable {

        private final Options options;
        private final RocksDB db;

        private PricingService(Options options, RocksDB db) {
            this.options = options;
            this.db = db;
        }

        static PricingService open(String path) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true);
            return new PricingService(options, RocksDB.open(options, path));
        }

        void ingest(Path feed) throws RocksDBException {
            try (IngestExternalFileOptions ingest = new IngestExternalFileOptions()
                    .setMoveFiles(true)
                    .setSnapshotConsistency(true)
                    .setAllowGlobalSeqNo(true)
                    .setAllowBlockingFlush(true)) {
                db.ingestExternalFile(List.of(feed.toString()), ingest);
            }
        }

        void setManually(String sku, String price) throws RocksDBException {
            db.put(bytes(sku), bytes(price));
        }

        String price(String sku) throws RocksDBException {
            byte[] value = db.get(bytes(sku));
            return value == null ? null : text(value);
        }

        Map<String, String> catalog() {
            Map<String, String> catalog = new TreeMap<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    catalog.put(text(it.key()), text(it.value()));
                }
            }
            return catalog;
        }

        @Override
        public void close() {
            db.close();
            options.close();
        }
    }
}
