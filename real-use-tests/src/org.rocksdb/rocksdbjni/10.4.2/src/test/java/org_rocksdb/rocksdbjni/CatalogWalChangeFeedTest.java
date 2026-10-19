/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionLogIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * Write-ahead log tailing for change data capture: reading committed write batches back from
 * the WAL with `getUpdatesSince` and `TransactionLogIterator`, decoding each one with a
 * `WriteBatch.Handler`, and resuming from a saved sequence number after a flush and a restart.
 *
 * A catalog service keeps products and stock levels in two column families and tags every
 * batch with the operator who issued it through `putLogData`. A replicator tails the primary's
 * WAL, keeps an audit trail of who changed what, and replays every change into a follower
 * database. It remembers the next sequence number it needs, so it can pick up where it left off.
 */
public class CatalogWalChangeFeedTest {

    @TempDir
    Path dataDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void replaysCommittedBatchesIntoFollowerInCommitOrder() throws Exception {
        try (Catalog primary = Catalog.open(dataDir.resolve("primary"));
                Catalog follower = Catalog.open(dataDir.resolve("follower"))) {
            Replicator replicator = new Replicator(primary, follower, 1);
            assertEquals(0, replicator.poll(), "nothing is written yet, so there is nothing to tail");

            primary.addProduct("alice", "sku-100", "Desk lamp", 12);
            primary.addProduct("alice", "sku-200", "Office chair", 4);
            primary.adjustStock("bob", "sku-100", 9);

            assertEquals(3, replicator.poll());
            assertEquals(List.of(
                    "alice put products/sku-100",
                    "alice put stock/sku-100",
                    "alice put products/sku-200",
                    "alice put stock/sku-200",
                    "bob put stock/sku-100"), replicator.audit);
            assertEquals(primary.contents(), follower.contents());
            assertEquals("9", follower.stock("sku-100"));
            assertEquals(primary.db.getLatestSequenceNumber() + 1, replicator.nextSequence);

            replicator.audit.clear();
            primary.addProduct("carol", "sku-300", "Monitor arm", 7);
            primary.addProduct("carol", "sku-310", "Monitor stand", 3);
            primary.discontinue("dave", "sku-200");
            primary.clearRange("erin", "sku-300", "sku-400");

            assertEquals(4, replicator.poll());
            assertEquals(List.of(
                    "carol put products/sku-300",
                    "carol put stock/sku-300",
                    "carol put products/sku-310",
                    "carol put stock/sku-310",
                    "dave delete products/sku-200",
                    "dave delete stock/sku-200",
                    "erin deleteRange products/[sku-300,sku-400)",
                    "erin deleteRange stock/[sku-300,sku-400)"), replicator.audit);
            assertEquals(primary.contents(), follower.contents());
            assertEquals(Map.of("sku-100", "Desk lamp"), follower.contents().get("products"));
            assertNull(follower.stock("sku-310"));

            assertEquals(0, replicator.poll(), "a caught-up replicator reads nothing twice");
        }
    }

    @Test
    void resumesFromSavedSequenceAfterFlushAndRestart() throws Exception {
        Path primaryPath = dataDir.resolve("primary-restart");
        long savedSequence;

        try (Catalog follower = Catalog.open(dataDir.resolve("follower-restart"))) {
            try (Catalog primary = Catalog.open(primaryPath)) {
                primary.addProduct("alice", "sku-1", "Notebook", 50);
                Replicator replicator = new Replicator(primary, follower, 1);
                assertEquals(1, replicator.poll());
                savedSequence = replicator.nextSequence;

                primary.adjustStock("bob", "sku-1", 45);
                primary.addProduct("bob", "sku-2", "Pencil case", 20);
                primary.flush();
            }

            try (Catalog primary = Catalog.open(primaryPath)) {
                primary.adjustStock("carol", "sku-2", 18);

                Replicator resumed = new Replicator(primary, follower, savedSequence);
                assertEquals(3, resumed.poll(),
                        "flushed batches stay readable from the archived WAL");
                assertEquals(List.of(
                        "bob put stock/sku-1",
                        "bob put products/sku-2",
                        "bob put stock/sku-2",
                        "carol put stock/sku-2"), resumed.audit);
                assertEquals(primary.contents(), follower.contents());
                assertEquals("45", follower.stock("sku-1"));
                assertEquals("18", follower.stock("sku-2"));
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** The catalog's storage: a `products` and a `stock` family, with archived WAL files kept. */
    private static final class Catalog implements AutoCloseable {

        private static final List<String> FAMILIES = List.of("products", "stock");

        private final DBOptions dbOptions;
        private final ColumnFamilyOptions familyOptions;
        private final List<ColumnFamilyHandle> handles;
        private final RocksDB db;
        private final Map<String, ColumnFamilyHandle> byName = new HashMap<>();

        private Catalog(DBOptions dbOptions, ColumnFamilyOptions familyOptions,
                List<ColumnFamilyHandle> handles, RocksDB db) throws RocksDBException {
            this.dbOptions = dbOptions;
            this.familyOptions = familyOptions;
            this.handles = handles;
            this.db = db;
            for (ColumnFamilyHandle handle : handles) {
                byName.put(text(handle.getName()), handle);
            }
        }

        static Catalog open(Path path) throws RocksDBException {
            DBOptions dbOptions = new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true)
                    .setWalTtlSeconds(3600);
            ColumnFamilyOptions familyOptions = new ColumnFamilyOptions();
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, familyOptions));
            for (String family : FAMILIES) {
                descriptors.add(new ColumnFamilyDescriptor(bytes(family), familyOptions));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            RocksDB db = RocksDB.open(dbOptions, path.toString(), descriptors, handles);
            return new Catalog(dbOptions, familyOptions, handles, db);
        }

        ColumnFamilyHandle family(String name) {
            ColumnFamilyHandle handle = byName.get(name);
            assertNotNull(handle, "unknown column family " + name);
            return handle;
        }

        void addProduct(String operator, String sku, String name, int stock) throws RocksDBException {
            try (WriteBatch batch = taggedBatch(operator)) {
                batch.put(family("products"), bytes(sku), bytes(name));
                batch.put(family("stock"), bytes(sku), bytes(Integer.toString(stock)));
                commit(batch);
            }
        }

        void adjustStock(String operator, String sku, int stock) throws RocksDBException {
            try (WriteBatch batch = taggedBatch(operator)) {
                batch.put(family("stock"), bytes(sku), bytes(Integer.toString(stock)));
                commit(batch);
            }
        }

        void discontinue(String operator, String sku) throws RocksDBException {
            try (WriteBatch batch = taggedBatch(operator)) {
                batch.delete(family("products"), bytes(sku));
                batch.delete(family("stock"), bytes(sku));
                commit(batch);
            }
        }

        void clearRange(String operator, String fromSku, String toSku) throws RocksDBException {
            try (WriteBatch batch = taggedBatch(operator)) {
                batch.deleteRange(family("products"), bytes(fromSku), bytes(toSku));
                batch.deleteRange(family("stock"), bytes(fromSku), bytes(toSku));
                commit(batch);
            }
        }

        String stock(String sku) throws RocksDBException {
            byte[] value = db.get(family("stock"), bytes(sku));
            return value == null ? null : text(value);
        }

        /** Every key and value of both families, as a reader of the database would see them. */
        Map<String, Map<String, String>> contents() {
            Map<String, Map<String, String>> contents = new TreeMap<>();
            for (String name : FAMILIES) {
                Map<String, String> entries = new TreeMap<>();
                try (RocksIterator iterator = db.newIterator(family(name))) {
                    for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                        entries.put(text(iterator.key()), text(iterator.value()));
                    }
                }
                contents.put(name, entries);
            }
            return contents;
        }

        void apply(WriteBatch batch) throws RocksDBException {
            commit(batch);
        }

        void flush() throws RocksDBException {
            try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flushOptions, handles);
            }
        }

        private static WriteBatch taggedBatch(String operator) throws RocksDBException {
            WriteBatch batch = new WriteBatch();
            batch.putLogData(bytes(operator));
            return batch;
        }

        private void commit(WriteBatch batch) throws RocksDBException {
            try (WriteOptions writeOptions = new WriteOptions()) {
                db.write(writeOptions, batch);
            }
        }

        @Override
        public void close() {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
            db.close();
            familyOptions.close();
            dbOptions.close();
        }
    }

    /** Tails the primary's WAL and replays each committed batch into the follower. */
    private static final class Replicator {

        private final Catalog primary;
        private final Catalog follower;
        private final Map<Integer, String> familyNames = new HashMap<>();
        private final List<String> audit = new ArrayList<>();
        private long nextSequence;

        Replicator(Catalog primary, Catalog follower, long nextSequence) throws RocksDBException {
            this.primary = primary;
            this.follower = follower;
            this.nextSequence = nextSequence;
            for (ColumnFamilyHandle handle : primary.handles) {
                familyNames.put(handle.getID(), text(handle.getName()));
            }
        }

        /** Replays every batch committed since the last poll and returns how many it read. */
        int poll() throws RocksDBException {
            if (nextSequence > primary.db.getLatestSequenceNumber()) {
                return 0;
            }
            int batches = 0;
            try (TransactionLogIterator log = primary.db.getUpdatesSince(nextSequence)) {
                for (; log.isValid(); log.next()) {
                    log.status();
                    TransactionLogIterator.BatchResult result = log.getBatch();
                    try (WriteBatch committed = result.writeBatch();
                            WriteBatch replay = new WriteBatch();
                            ChangeDecoder decoder = new ChangeDecoder(replay)) {
                        committed.iterate(decoder);
                        follower.apply(replay);
                        nextSequence = result.sequenceNumber() + committed.count();
                    }
                    batches++;
                }
                log.status();
            }
            return batches;
        }

        /** Turns one committed batch into audit entries and a batch for the follower. */
        private final class ChangeDecoder extends WriteBatch.Handler {

            private final WriteBatch replay;
            private String operator = "unknown";

            ChangeDecoder(WriteBatch replay) {
                this.replay = replay;
            }

            private ColumnFamilyHandle target(int columnFamilyId) {
                return follower.family(familyNames.get(columnFamilyId));
            }

            private void record(String change, int columnFamilyId, String key) {
                audit.add(operator + " " + change + " " + familyNames.get(columnFamilyId) + "/" + key);
            }

            @Override
            public void logData(byte[] blob) {
                operator = text(blob);
            }

            @Override
            public void put(int columnFamilyId, byte[] key, byte[] value) throws RocksDBException {
                replay.put(target(columnFamilyId), key, value);
                record("put", columnFamilyId, text(key));
            }

            @Override
            public void put(byte[] key, byte[] value) {
                throw new IllegalStateException("the catalog never writes the default family");
            }

            @Override
            public void merge(int columnFamilyId, byte[] key, byte[] value) throws RocksDBException {
                replay.merge(target(columnFamilyId), key, value);
                record("merge", columnFamilyId, text(key));
            }

            @Override
            public void merge(byte[] key, byte[] value) {
                throw new IllegalStateException("the catalog never writes the default family");
            }

            @Override
            public void delete(int columnFamilyId, byte[] key) throws RocksDBException {
                replay.delete(target(columnFamilyId), key);
                record("delete", columnFamilyId, text(key));
            }

            @Override
            public void delete(byte[] key) {
                throw new IllegalStateException("the catalog never writes the default family");
            }

            @Override
            public void singleDelete(int columnFamilyId, byte[] key) throws RocksDBException {
                replay.singleDelete(target(columnFamilyId), key);
                record("singleDelete", columnFamilyId, text(key));
            }

            @Override
            public void singleDelete(byte[] key) {
                throw new IllegalStateException("the catalog never writes the default family");
            }

            @Override
            public void deleteRange(int columnFamilyId, byte[] beginKey, byte[] endKey)
                    throws RocksDBException {
                replay.deleteRange(target(columnFamilyId), beginKey, endKey);
                record("deleteRange", columnFamilyId, "[" + text(beginKey) + "," + text(endKey) + ")");
            }

            @Override
            public void deleteRange(byte[] beginKey, byte[] endKey) {
                throw new IllegalStateException("the catalog never writes the default family");
            }

            @Override
            public void putBlobIndex(int columnFamilyId, byte[] key, byte[] value) {
                throw new IllegalStateException("the catalog does not use blob files");
            }

            // The catalog writes plain batches, so two-phase-commit markers never appear.
            @Override
            public void markBeginPrepare() {
            }

            @Override
            public void markEndPrepare(byte[] xid) {
            }

            @Override
            public void markNoop(boolean emptyBatch) {
            }

            @Override
            public void markRollback(byte[] xid) {
            }

            @Override
            public void markCommit(byte[] xid) {
            }

            @Override
            public void markCommitWithTimestamp(byte[] xid, byte[] timestamp) {
            }
        }
    }
}
