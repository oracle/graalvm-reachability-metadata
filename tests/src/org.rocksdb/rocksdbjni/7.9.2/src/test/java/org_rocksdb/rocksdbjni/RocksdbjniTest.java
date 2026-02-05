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
import org.rocksdb.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RocksdbjniTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @Test
    void basicPutGetDeleteAndPersistence(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("basic-db");

        try (Options options = new Options().setCreateIfMissing(true)) {
            // Create/open and perform basic CRUD
            try (RocksDB db = RocksDB.open(options, dbPath.toString())) {
                assertThat(db.get(b("missing"))).isNull();

                db.put(b("k1"), b("v1"));
                assertThat(s(db.get(b("k1")))).isEqualTo("v1");

                db.delete(b("k1"));
                assertThat(db.get(b("k1"))).isNull();
            }

            // Reopen and ensure state persisted (key k1 remains deleted)
            try (RocksDB db = RocksDB.open(options, dbPath.toString())) {
                assertThat(db.get(b("k1"))).isNull();

                db.put(b("k2"), b("v2"));
            }

            // Reopen and verify k2 is present
            try (RocksDB db = RocksDB.open(options, dbPath.toString())) {
                assertThat(s(db.get(b("k2")))).isEqualTo("v2");
            }
        }
    }

    @Test
    void iteratorOrderingAndSeek(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("iter-db");

        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("a"), b("1"));
            db.put(b("aa"), b("2"));
            db.put(b("b"), b("3"));
            db.put(b("c"), b("4"));

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

            assertThat(keys).containsExactly("a", "aa", "b", "c");
            assertThat(values).containsExactly("1", "2", "3", "4");

            // Seek to a specific key
            List<String> seeked = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                it.seek(b("b"));
                while (it.isValid()) {
                    seeked.add(s(it.key()));
                    it.next();
                }
            }
            assertThat(seeked).containsExactly("b", "c");
        }
    }

    @Test
    void snapshotIsolation(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("snapshot-db");

        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            db.put(b("k"), b("v1"));

            Snapshot snapshot = db.getSnapshot();
            try (ReadOptions ro = new ReadOptions().setSnapshot(snapshot)) {
                // Modify after snapshot
                db.put(b("k"), b("v2"));
                db.put(b("k2"), b("x"));

                // Snapshot view should see old value
                assertThat(s(db.get(ro, b("k")))).isEqualTo("v1");
                // Default view should see new value
                assertThat(s(db.get(b("k")))).isEqualTo("v2");
                assertThat(s(db.get(b("k2")))).isEqualTo("x");
            } finally {
                db.releaseSnapshot(snapshot);
            }
        }
    }

    @Test
    void writeBatchAtomicityAndMultiGet(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("batch-db");

        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString());
             WriteOptions wo = new WriteOptions()) {

            db.put(b("a"), b("1"));
            db.put(b("b"), b("1"));
            db.put(b("c"), b("1"));

            try (WriteBatch batch = new WriteBatch()) {
                batch.delete(b("a"));
                batch.put(b("b"), b("2"));
                batch.put(b("d"), b("9"));
                db.write(wo, batch);
            }

            assertThat(db.get(b("a"))).isNull();
            assertThat(s(db.get(b("b")))).isEqualTo("2");
            assertThat(s(db.get(b("c")))).isEqualTo("1");
            assertThat(s(db.get(b("d")))).isEqualTo("9");

            // Validate using multiGetAsList to ensure order is preserved
            List<byte[]> keys = Arrays.asList(b("a"), b("b"), b("c"), b("d"), b("zzz"));
            List<byte[]> values = db.multiGetAsList(keys);
            assertThat(values.get(0)).isNull();
            assertThat(s(values.get(1))).isEqualTo("2");
            assertThat(s(values.get(2))).isEqualTo("1");
            assertThat(s(values.get(3))).isEqualTo("9");
            assertThat(values.get(4)).isNull();
        }
    }

    @Test
    void columnFamiliesIsolationAndPersistence(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("cf-db");

        ColumnFamilyOptions defaultCfOpts = new ColumnFamilyOptions();
        ColumnFamilyOptions cf1Opts = new ColumnFamilyOptions();
        ColumnFamilyOptions cf2Opts = new ColumnFamilyOptions();

        List<ColumnFamilyDescriptor> cfDescs = Arrays.asList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultCfOpts),
                new ColumnFamilyDescriptor(b("cf1"), cf1Opts),
                new ColumnFamilyDescriptor(b("cf2"), cf2Opts)
        );

        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(dbOptions, dbPath.toString(), cfDescs, handles)) {

            // After opening, options objects are no longer needed; close them to free native resources
            defaultCfOpts.close();
            cf1Opts.close();
            cf2Opts.close();

            assertThat(handles).hasSize(3);

            ColumnFamilyHandle defaultHandle = handles.get(0);
            ColumnFamilyHandle cf1Handle = handles.get(1);
            ColumnFamilyHandle cf2Handle = handles.get(2);

            // Put values into different CFs
            db.put(defaultHandle, b("k"), b("v-default"));
            db.put(cf1Handle, b("k"), b("v-cf1"));
            db.put(cf2Handle, b("k2"), b("v-cf2"));

            // Ensure isolation between CFs
            assertThat(s(db.get(defaultHandle, b("k")))).isEqualTo("v-default");
            assertThat(s(db.get(cf1Handle, b("k")))).isEqualTo("v-cf1");
            assertThat(db.get(cf1Handle, b("k2"))).isNull();
            assertThat(db.get(cf2Handle, b("k"))).isNull();
            assertThat(s(db.get(cf2Handle, b("k2")))).isEqualTo("v-cf2");

            // Close and reopen to verify persistence
            for (ColumnFamilyHandle h : handles) {
                h.close();
            }
        }

        // Reopen with the same CF descriptors
        List<ColumnFamilyHandle> reopenedHandles = new ArrayList<>();
        // Need fresh CFOptions when reopening
        ColumnFamilyOptions defaultCfOpts2 = new ColumnFamilyOptions();
        ColumnFamilyOptions cf1Opts2 = new ColumnFamilyOptions();
        ColumnFamilyOptions cf2Opts2 = new ColumnFamilyOptions();
        List<ColumnFamilyDescriptor> reopenDescs = Arrays.asList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultCfOpts2),
                new ColumnFamilyDescriptor(b("cf1"), cf1Opts2),
                new ColumnFamilyDescriptor(b("cf2"), cf2Opts2)
        );

        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(dbOptions, dbPath.toString(), reopenDescs, reopenedHandles)) {

            // Close the CF options as they are no longer needed after opening
            defaultCfOpts2.close();
            cf1Opts2.close();
            cf2Opts2.close();

            ColumnFamilyHandle defaultHandle = reopenedHandles.get(0);
            ColumnFamilyHandle cf1Handle = reopenedHandles.get(1);
            ColumnFamilyHandle cf2Handle = reopenedHandles.get(2);

            assertThat(s(db.get(defaultHandle, b("k")))).isEqualTo("v-default");
            assertThat(s(db.get(cf1Handle, b("k")))).isEqualTo("v-cf1");
            assertThat(s(db.get(cf2Handle, b("k2")))).isEqualTo("v-cf2");

            for (ColumnFamilyHandle h : reopenedHandles) {
                h.close();
            }
        }
    }

    @Test
    void compactRangeKeepsDataCorrect(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("compact-db");

        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString())) {

            // Insert a range of keys
            for (int i = 0; i < 200; i++) {
                db.put(b(String.format("k%03d", i)), b("v" + i));
            }
            // Delete some to create tombstones
            for (int i = 50; i < 100; i++) {
                db.delete(b(String.format("k%03d", i)));
            }

            // Compact the entire key range
            db.compactRange();

            // Verify keys before 50 exist, [50,99] deleted, [100,199] exist
            for (int i = 0; i < 50; i++) {
                assertThat(db.get(b(String.format("k%03d", i)))).isNotNull();
            }
            for (int i = 50; i < 100; i++) {
                assertThat(db.get(b(String.format("k%03d", i)))).isNull();
            }
            for (int i = 100; i < 200; i++) {
                assertThat(db.get(b(String.format("k%03d", i)))).isNotNull();
            }
        }
    }

    @Test
    void checkpointCreatesConsistentSnapshot(@TempDir Path tmp) throws Exception {
        Path srcDbPath = tmp.resolve("src-db");
        Path checkpointPath = tmp.resolve("checkpoint-db");

        // Create source DB and populate some data
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, srcDbPath.toString())) {

            db.put(b("k1"), b("v1"));
            db.put(b("k2"), b("v2"));

            // Create a checkpoint capturing current state
            try (Checkpoint checkpoint = Checkpoint.create(db)) {
                checkpoint.createCheckpoint(checkpointPath.toString());
            }

            // Mutate the source DB after checkpoint
            db.put(b("k3"), b("v3"));
            db.delete(b("k1"));
        }

        // Open the checkpoint as a standalone DB and verify it reflects the state at checkpoint time
        try (Options options = new Options().setCreateIfMissing(false);
             RocksDB cpDb = RocksDB.open(options, checkpointPath.toString())) {

            assertThat(s(cpDb.get(b("k1")))).isEqualTo("v1"); // existed at checkpoint
            assertThat(s(cpDb.get(b("k2")))).isEqualTo("v2"); // existed at checkpoint
            assertThat(cpDb.get(b("k3"))).isNull();           // written after checkpoint
        }
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] b) {
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }
}
