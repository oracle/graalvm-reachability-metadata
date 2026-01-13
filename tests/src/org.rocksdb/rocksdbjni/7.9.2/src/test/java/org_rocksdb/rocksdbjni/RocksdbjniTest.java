/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RocksdbjniTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void basicPutGetDelete() throws Exception {
        Path dbPath = newDbDir("basicPutGetDelete");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            byte[] key = b("hello");
            byte[] value = b("world");

            db.put(key, value);
            assertThat(db.get(key)).isEqualTo(value);

            db.delete(key);
            assertThat(db.get(key)).isNull();
        }
    }

    @Test
    void iteratorTraversesInKeyOrder() throws Exception {
        Path dbPath = newDbDir("iteratorTraversesInKeyOrder");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("a"), b("1"));
            db.put(b("c"), b("3"));
            db.put(b("b"), b("2"));

            List<String> keys = new ArrayList<>();
            List<String> values = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                it.seekToFirst();
                while (it.isValid()) {
                    keys.add(s(it.key()));
                    values.add(s(it.value()));
                    it.next();
                }
            }

            assertThat(keys).containsExactly("a", "b", "c");
            assertThat(values).containsExactly("1", "2", "3");
        }
    }

    @Test
    void writeBatchIsAtomic() throws Exception {
        Path dbPath = newDbDir("writeBatchIsAtomic");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            byte[] k1 = b("k1");
            byte[] k2 = b("k2");

            try (WriteOptions writeOptions = new WriteOptions();
                 WriteBatch batch = new WriteBatch()) {
                batch.put(k1, b("v1"));
                batch.put(k2, b("v2"));
                batch.delete(k1);

                db.write(writeOptions, batch);
            }

            assertThat(db.get(k1)).isNull();
            assertThat(db.get(k2)).isEqualTo(b("v2"));
        }
    }

    @Test
    void columnFamiliesMaintainIsolation() throws Exception {
        Path dbPath = newDbDir("columnFamiliesMaintainIsolation");

        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)) {

            ColumnFamilyOptions defaultCfOpts = new ColumnFamilyOptions();
            ColumnFamilyOptions cf1Opts = new ColumnFamilyOptions();
            List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
                    new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultCfOpts),
                    new ColumnFamilyDescriptor(b("cf1"), cf1Opts)
            );

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
            RocksDB db = RocksDB.open(dbOptions, dbPath.toString(), cfDescriptors, cfHandles);

            try (db; defaultCfOpts; cf1Opts) {
                ColumnFamilyHandle defaultHandle = cfHandles.get(0);
                ColumnFamilyHandle cf1Handle = cfHandles.get(1);

                // Put in different CFs
                db.put(defaultHandle, b("key"), b("default"));
                db.put(cf1Handle, b("key"), b("cf1"));

                // Verify isolation
                assertThat(db.get(defaultHandle, b("key"))).isEqualTo(b("default"));
                assertThat(db.get(cf1Handle, b("key"))).isEqualTo(b("cf1"));

                // Same key in different CFs should not collide
                assertThat(db.get(defaultHandle, b("missing"))).isNull();
                assertThat(db.get(cf1Handle, b("missing"))).isNull();

                // Close handles explicitly
                for (ColumnFamilyHandle handle : cfHandles) {
                    handle.close();
                }
            }
        }
    }

    @Test
    void snapshotProvidesPointInTimeView() throws Exception {
        Path dbPath = newDbDir("snapshotProvidesPointInTimeView");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            byte[] key = b("snap-key");
            db.put(key, b("v1"));

            Snapshot snapshot = db.getSnapshot();
            try (ReadOptions ro = new ReadOptions().setSnapshot(snapshot)) {

                // Modify after snapshot
                db.put(key, b("v2"));

                // Snapshot should still see old value
                assertThat(db.get(ro, key)).isEqualTo(b("v1"));

                // Without snapshot we see the latest
                assertThat(db.get(key)).isEqualTo(b("v2"));
            } finally {
                db.releaseSnapshot(snapshot);
            }
        }
    }

    @Test
    void multiGetRetrievesMultipleKeys() throws Exception {
        Path dbPath = newDbDir("multiGetRetrievesMultipleKeys");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("k1"), b("v1"));
            db.put(b("k2"), b("v2"));
            // Intentionally leaving k3 missing
            db.put(b("k4"), b("v4"));

            List<byte[]> keys = Arrays.asList(b("k1"), b("k2"), b("k3"), b("k4"));
            List<byte[]> values = db.multiGetAsList(keys);

            assertThat(values).hasSize(4);
            assertThat(values.get(0)).isEqualTo(b("v1"));
            assertThat(values.get(1)).isEqualTo(b("v2"));
            assertThat(values.get(2)).isNull(); // k3 missing
            assertThat(values.get(3)).isEqualTo(b("v4"));
        }
    }

    @Test
    void deleteRangeRemovesKeysInHalfOpenInterval() throws Exception {
        Path dbPath = newDbDir("deleteRangeRemovesKeysInHalfOpenInterval");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("a"), b("1"));
            db.put(b("b"), b("2"));
            db.put(b("c"), b("3"));
            db.put(b("d"), b("4"));

            // Delete keys in [b, d)
            db.deleteRange(b("b"), b("d"));

            assertThat(db.get(b("a"))).isEqualTo(b("1"));
            assertThat(db.get(b("b"))).isNull();
            assertThat(db.get(b("c"))).isNull();
            assertThat(db.get(b("d"))).isEqualTo(b("4"));
        }
    }

    @Test
    void iteratorRespectsBoundsInReadOptions() throws Exception {
        Path dbPath = newDbDir("iteratorRespectsBoundsInReadOptions");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("a"), b("1"));
            db.put(b("b"), b("2"));
            db.put(b("c"), b("3"));
            db.put(b("d"), b("4"));

            List<String> keys = new ArrayList<>();
            try (Slice lower = new Slice(b("b"));
                 Slice upper = new Slice(b("d"));
                 ReadOptions ro = new ReadOptions()) {
                ro.setIterateLowerBound(lower);
                ro.setIterateUpperBound(upper);

                try (RocksIterator it = db.newIterator(ro)) {
                    // Start at the lower bound
                    it.seek(b("b"));
                    while (it.isValid()) {
                        keys.add(s(it.key()));
                        it.next();
                    }
                }
            }

            // Expect keys in [b, d) => b and c
            assertThat(keys).containsExactly("b", "c");
        }
    }

    // Helpers

    private Path newDbDir(String name) throws IOException {
        Path dir = tempDir.resolve(name + "-" + UUID.randomUUID());
        Files.createDirectories(dir);
        return dir;
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
