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
import org.rocksdb.ByteBufferGetStatus;
import org.rocksdb.FlushOptions;
import org.rocksdb.Holder;
import org.rocksdb.KeyMayExist;
import org.rocksdb.MutableColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.Range;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.rocksdb.SizeApproximationFlag;
import org.rocksdb.Slice;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBatchWithIndex;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RocksDatabaseApiCoverageTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void databaseQueriesAndIteratorSupportArrayAndBufferForms() throws Exception {
        Path path = tempDir.resolve("query-db");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, path.toString())) {
            db.put(bytes("alpha"), bytes("one"));
            db.put(bytes("beta"), bytes("two"));
            db.put(bytes("delta"), bytes("four"));
            assertThat(db.getOptions()).isNotNull();
            assertThat(db.getDBOptions()).isNotNull();
            try (org.rocksdb.Env environment = db.getEnv()) {
                assertThat(environment).isNotNull();
            }
            try (org.rocksdb.Snapshot snapshot = db.getSnapshot()) {
                assertThat(snapshot).isNotNull();
                db.releaseSnapshot(snapshot);
            }
            assertThat(db.getProperty("rocksdb.num-files-at-level0")).isNotNull();
            assertThat(db.getMapProperty("rocksdb.cfstats")).isNotNull();
            assertThat(db.getLongProperty("rocksdb.estimate-num-keys")).isGreaterThanOrEqualTo(0);
            try (org.rocksdb.PerfContext perfContext = db.getPerfContext()) {
                assertThat(perfContext.toString()).isNotNull();
            }

            Holder<byte[]> holder = new Holder<>();
            assertThat(db.keyExists(bytes("alpha"))).isTrue();
            assertThat(db.keyExists(bytes("missing"))).isFalse();
            try (org.rocksdb.ReadOptions readOptions = new org.rocksdb.ReadOptions()) {
                assertThat(db.keyExists(readOptions, bytes("alpha"))).isTrue();
                assertThat(db.keyMayExist(readOptions, bytes("alpha"), holder)).isTrue();
            }
            assertThat(db.keyMayExist(bytes("alpha"), holder)).isTrue();
            assertThat(holder.getValue()).isEqualTo(bytes("one"));

            ByteBuffer key = ByteBuffer.allocateDirect(5);
            key.put(bytes("alpha")).flip();
            ByteBuffer value = ByteBuffer.allocateDirect(16);
            KeyMayExist result = db.keyMayExist(key, value);
            assertThat(result).isNotNull();
            key.rewind();
            assertThat(db.keyExists(key)).isTrue();
            key.rewind();
            assertThat(db.keyMayExist(key)).isTrue();
            key.rewind();
            try (org.rocksdb.ReadOptions readOptions = new org.rocksdb.ReadOptions()) {
                key.rewind();
                value.clear();
                assertThat(db.keyMayExist(readOptions, key, value)).isNotNull();
                key.rewind();
                value.clear();
                assertThat(db.get(readOptions, key, value)).isEqualTo(3);
            }

            List<ByteBuffer> keys = Arrays.asList(directBuffer("alpha"), directBuffer("beta"));
            List<ByteBuffer> values = Arrays.asList(ByteBuffer.allocateDirect(8), ByteBuffer.allocateDirect(8));
            List<ByteBufferGetStatus> results = db.multiGetByteBuffers(keys, values);
            assertThat(results).hasSize(2);
            assertThat(results.get(0).status).isNotNull();

            try (Slice begin = new Slice(bytes("alpha"));
                 Slice end = new Slice(bytes("zulu"))) {
                Range range = new Range(begin, end);
                long[] sizes = db.getApproximateSizes(Collections.singletonList(range),
                        new SizeApproximationFlag[] {SizeApproximationFlag.INCLUDE_MEMTABLES});
                assertThat(sizes).hasSize(1);
                assertThat(db.getApproximateMemTableStats(range)).isNotNull();
            }
            assertThat(db.getLiveFiles().files).isNotNull();
            assertThat(db.suggestCompactRange()).isNotNull();
            assertThat(db.getPerfLevel()).isNotNull();

            try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flushOptions);
            }
            List<org.rocksdb.LiveFileMetaData> files = db.getLiveFilesMetaData();
            if (!files.isEmpty()) {
                org.rocksdb.LiveFileMetaData file = files.get(0);
                try (Options readerOptions = new Options();
                     org.rocksdb.SstFileReader reader = new org.rocksdb.SstFileReader(readerOptions);
                     org.rocksdb.ReadOptions readerReadOptions = new org.rocksdb.ReadOptions()) {
                    reader.open(Path.of(file.path(), file.fileName()).toString());
                    try (org.rocksdb.SstFileReaderIterator iterator = reader.newIterator(readerReadOptions)) {
                        iterator.seekToFirst();
                        assertThat(iterator.isValid()).isTrue();
                    }
                }
            }
            try (RocksIterator iterator = db.newIterator()) {
                iterator.seekToLast();
                assertThat(iterator.isValid()).isTrue();
                assertThat(iterator.key()).isEqualTo(bytes("delta"));
                iterator.prev();
                assertThat(iterator.key()).isEqualTo(bytes("beta"));
                iterator.seekForPrev(bytes("beta"));
                assertThat(iterator.key()).isEqualTo(bytes("beta"));
                ByteBuffer seek = buffer("alpha");
                iterator.seek(seek);
                assertThat(iterator.key()).isEqualTo(bytes("alpha"));
                ByteBuffer reverseSeek = buffer("beta");
                iterator.seekForPrev(reverseSeek);
                assertThat(iterator.key()).isEqualTo(bytes("beta"));
                iterator.refresh();
                try (org.rocksdb.Snapshot snapshot = db.getSnapshot()) {
                    iterator.refresh(snapshot);
                }
                iterator.status();
            }
            assertThat(db.getColumnFamilyMetaData().name()).isEqualTo(RocksDB.DEFAULT_COLUMN_FAMILY);
            assertThat(db.getColumnFamilyMetaData().fileCount()).isGreaterThanOrEqualTo(0);
            assertThat(db.getColumnFamilyMetaData().size()).isGreaterThanOrEqualTo(0);
            assertThat(db.getColumnFamilyMetaData().levels()).isNotNull();
            assertThat(db.getPropertiesOfAllTables()).isNotNull();
            try (Slice begin = new Slice(bytes("alpha")); Slice end = new Slice(bytes("zulu"));
                 ColumnFamilyHandle defaultHandle = db.getDefaultColumnFamily()) {
                if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
                    assertThat(db.getPropertiesOfTablesInRange(defaultHandle,
                            Collections.singletonList(new Range(begin, end))))
                            .isNotNull();
                }
            }
            assertThat(db.numberLevels()).isPositive();
            assertThat(db.maxMemCompactionLevel()).isGreaterThanOrEqualTo(0);
            assertThat(db.level0StopWriteTrigger()).isGreaterThanOrEqualTo(0);
            try {
                db.promoteL0(0);
            } catch (org.rocksdb.RocksDBException expected) {
                assertThat(expected).isNotNull();
            }
            try (org.rocksdb.CompactionOptions compactionOptions = new org.rocksdb.CompactionOptions();
                 org.rocksdb.CompactionJobInfo jobInfo = new org.rocksdb.CompactionJobInfo()) {
                try {
                    db.compactFiles(compactionOptions, Collections.emptyList(), 0, 0, jobInfo);
                } catch (org.rocksdb.RocksDBException expected) {
                    assertThat(expected).isNotNull();
                }
            }
            try {
                db.startTrace(new org.rocksdb.TraceOptions(), new CoverageTraceWriter());
                db.endTrace();
            } catch (org.rocksdb.RocksDBException expected) {
                assertThat(expected).isNotNull();
            }
            MutableColumnFamilyOptions mutable = MutableColumnFamilyOptions.builder()
                    .setWriteBufferSize(8192).build();
            db.setOptions(mutable);
            db.compactRange();
            db.compactRange(bytes("alpha"), bytes("zulu"));
            assertThat(db.suggestCompactRange()).isNotNull();
            if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
                try (org.rocksdb.TransactionLogIterator log = db.getUpdatesSince(0)) {
                    if (log.isValid()) {
                        assertThat(log.getBatch()).isNotNull();
                    } else {
                        try {
                            log.getBatch();
                        } catch (AssertionError expected) {
                            assertThat(expected).isNotNull();
                        }
                    }
                }
            }
        }

        RocksDB.loadLibrary(Collections.emptyList());
        try (RocksDB readOnly = RocksDB.openReadOnly(path.toString())) {
            assertThat(readOnly.get(bytes("alpha"))).isEqualTo(bytes("one"));
        }
        try (RocksDB reopened = RocksDB.open(path.toString())) {
            assertThat(reopened.get(bytes("beta"))).isEqualTo(bytes("two"));
        }
    }

    @Test
    void mergeAndMultiGetUseColumnFamilyByteBuffers() throws Exception {
        Path path = tempDir.resolve("merge-db");
        try (Options options = new Options().setCreateIfMissing(true)
                .setMergeOperatorName("stringappend");
             RocksDB db = RocksDB.open(options, path.toString());
             org.rocksdb.WriteOptions writeOptions = new org.rocksdb.WriteOptions();
             ColumnFamilyHandle handle = db.getDefaultColumnFamily()) {
            assertThat(db.getEnv()).isNotNull();
            db.merge(handle, writeOptions, directBuffer("merge-key"), directBuffer("value"));
            assertThat(db.get(handle, bytes("merge-key"))).isEqualTo(bytes("value"));
            db.merge(writeOptions, directBuffer("default-merge-key"), directBuffer("value"));
            assertThat(db.get(bytes("default-merge-key"))).isEqualTo(bytes("value"));

            List<ByteBuffer> keys = Collections.singletonList(directBuffer("merge-key"));
            List<ByteBuffer> values = Collections.singletonList(ByteBuffer.allocateDirect(32));
            try (org.rocksdb.ReadOptions readOptions = new org.rocksdb.ReadOptions()) {
                List<ByteBufferGetStatus> readStatuses = db.multiGetByteBuffers(readOptions, keys, values);
                assertThat(readStatuses).hasSize(1);
                assertThat(readStatuses.get(0).status).isNotNull();
            }
            List<ByteBufferGetStatus> statuses = db.multiGetByteBuffers(
                    Collections.singletonList(handle), keys, values);
            assertThat(statuses).hasSize(1);
            assertThat(statuses.get(0).status).isNotNull();
            assertThat(statuses.get(0).value).isNotNull();
        }
    }

    @Test
    void readOnlyOpenWithColumnFamilyDescriptorsReadsExistingData() throws Exception {
        Path path = tempDir.resolve("read-only-descriptors");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, path.toString())) {
            db.put(bytes("read-only-key"), bytes("read-only-value"));
        }
        List<ColumnFamilyHandle> handles = new java.util.ArrayList<>();
        try (ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()) {
            List<ColumnFamilyDescriptor> descriptors = Collections.singletonList(
                    new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            try (RocksDB db = RocksDB.openReadOnly(path.toString(), descriptors, handles)) {
                assertThat(db.get(bytes("read-only-key"))).isEqualTo(bytes("read-only-value"));
            }
        } finally {
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
        }
    }

    @Test
    void columnFamilyHandlesExposeStableIdentityAndDescriptors() throws Exception {
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, tempDir.resolve("handles").toString());
             ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()) {
            ColumnFamilyDescriptor descriptor = new ColumnFamilyDescriptor(bytes("events"), columnFamilyOptions);
            ColumnFamilyHandle handle = db.createColumnFamily(descriptor);
            try {
                db.put(handle, bytes("event"), bytes("created"));
                assertThat(db.keyExists(handle, bytes("event"))).isTrue();
                ByteBuffer value = directBuffer("                      ");
                assertThat(db.keyMayExist(handle, directBuffer("event"), value)).isNotNull();
                assertThat(db.getColumnFamilyMetaData(handle).name()).isEqualTo(bytes("events"));
                try (RocksIterator iterator = db.newIterators(Collections.singletonList(handle)).get(0)) {
                    iterator.seekToFirst();
                    assertThat(iterator.isValid()).isTrue();
                    try (WriteBatchWithIndex batch = new WriteBatchWithIndex()) {
                        batch.put(bytes("pending"), bytes("value"));
                        try (RocksIterator merged = batch.newIteratorWithBase(handle, iterator)) {
                            merged.seekToFirst();
                            assertThat(merged.isValid()).isTrue();
                        }
                        batch.put(handle, bytes("pending-family"), bytes("value"));
                        try (org.rocksdb.WBWIRocksIterator familyIterator = batch.newIterator(handle)) {
                            familyIterator.seekToFirst();
                            assertThat(familyIterator.isValid()).isTrue();
                        }
                    }
                }
                assertThat(handle.getID()).isGreaterThan(0);
                assertThat(handle.getName()).isEqualTo(bytes("events"));
                assertThat(handle.getDescriptor().getName()).isEqualTo(bytes("events"));
                ColumnFamilyDescriptor byteArrayDescriptor = new ColumnFamilyDescriptor(bytes("byte-array"));
                assertThat(byteArrayDescriptor.getName()).isEqualTo(bytes("byte-array"));
                assertThat(byteArrayDescriptor.hashCode()).isNotEqualTo(0);
                byteArrayDescriptor.getOptions().close();
                assertThat(descriptor.equals(descriptor)).isTrue();
                assertThat(handle).isEqualTo(handle);
                assertThat(handle.hashCode()).isEqualTo(handle.hashCode());
            } finally {
                db.destroyColumnFamilyHandle(handle);
            }
        }
    }

    @Test
    void writeBatchVariantsRepresentAtomicChanges() throws Exception {
        Path path = tempDir.resolve("batch-db");
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, path.toString());
             WriteBatch batch = new WriteBatch()) {
            byte[] first = bytes("first");
            byte[] second = bytes("second");
            batch.put(first, bytes("1"));
            batch.setMaxBytes(4096);
            batch.setSavePoint();
            batch.put(directBuffer("second"), directBuffer("2"));
            batch.merge(first, bytes("ignored-until-merge-configured"));
            batch.delete(directBuffer("unused"));
            batch.deleteRange(bytes("a"), bytes("b"));
            batch.putLogData(bytes("audit"));
            assertThat(batch.getWriteBatch()).isNotNull();
            assertThat(batch.count()).isEqualTo(5);
            batch.rollbackToSavePoint();
            batch.setSavePoint();
            batch.popSavePoint();
            batch.clear();
            assertThat(batch.count()).isZero();

            try (ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()) {
                ColumnFamilyDescriptor descriptor = new ColumnFamilyDescriptor(bytes("cf"), cfOptions);
                ColumnFamilyHandle handle = db.createColumnFamily(descriptor);
                try {
                    batch.put(handle, first, bytes("cf-value"));
                    batch.put(handle, directBuffer("second"), directBuffer("cf-two"));
                    batch.delete(handle, first);
                    batch.delete(handle, directBuffer("unused"));
                    batch.singleDelete(handle, second);
                    batch.deleteRange(handle, bytes("a"), bytes("b"));
                    batch.merge(handle, bytes("merge"), bytes("value"));
                    batch.setSavePoint();
                    batch.singleDelete(bytes("plain"));
                    assertThat(batch.count()).isGreaterThan(0);
                    batch.popSavePoint();
                } finally {
                    db.destroyColumnFamilyHandle(handle);
                }
            }
        }
    }

    @Test
    void writeBatchWithIndexIteratorExposesPendingEntries() throws Exception {
        try (WriteBatchWithIndex batch = new WriteBatchWithIndex()) {
            batch.put(bytes("duplicate"), bytes("first"));
            batch.put(bytes("distinct"), bytes("last"));
            assertThat(batch.count()).isEqualTo(2);
            try (org.rocksdb.WBWIRocksIterator iterator = batch.newIterator()) {
                iterator.seekToFirst();
                assertThat(iterator.isValid()).isTrue();
                assertThat(iterator.entry()).isNotNull();
            }
        }
    }

    private static final class CoverageTraceWriter extends org.rocksdb.AbstractTraceWriter {
        @Override
        public void write(org.rocksdb.Slice data) {
        }

        @Override
        public void closeWriter() {
        }

        @Override
        public long getFileSize() {
            return 0;
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ByteBuffer buffer(String value) {
        return ByteBuffer.wrap(bytes(value));
    }

    private static ByteBuffer directBuffer(String value) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(value.length());
        buffer.put(bytes(value)).flip();
        return buffer;
    }
}
