/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_leveldb.leveldb_api;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBComparator;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBFactory;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Logger;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.Range;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Leveldb_apiTest {
    private static final DBComparator BYTEWISE_COMPARATOR = new BytewiseComparator();

    @Test
    void optionsExposeLevelDbDefaultsAndFluentSetters() {
        Options defaults = new Options();
        assertThat(defaults.createIfMissing()).isTrue();
        assertThat(defaults.errorIfExists()).isFalse();
        assertThat(defaults.writeBufferSize()).isEqualTo(4 * 1024 * 1024);
        assertThat(defaults.maxOpenFiles()).isEqualTo(1000);
        assertThat(defaults.blockRestartInterval()).isEqualTo(16);
        assertThat(defaults.blockSize()).isEqualTo(4096);
        assertThat(defaults.compressionType()).isEqualTo(CompressionType.SNAPPY);
        assertThat(defaults.verifyChecksums()).isTrue();
        assertThat(defaults.paranoidChecks()).isFalse();
        assertThat(defaults.cacheSize()).isZero();
        assertThat(defaults.comparator()).isNull();
        assertThat(defaults.logger()).isNull();

        List<String> messages = new ArrayList<>();
        Logger logger = messages::add;
        Options configured = new Options();
        assertThat(configured.createIfMissing(false)).isSameAs(configured);
        assertThat(configured.errorIfExists(true)).isSameAs(configured);
        assertThat(configured.writeBufferSize(32 * 1024)).isSameAs(configured);
        assertThat(configured.maxOpenFiles(42)).isSameAs(configured);
        assertThat(configured.blockRestartInterval(8)).isSameAs(configured);
        assertThat(configured.blockSize(2048)).isSameAs(configured);
        assertThat(configured.compressionType(CompressionType.NONE)).isSameAs(configured);
        assertThat(configured.verifyChecksums(false)).isSameAs(configured);
        assertThat(configured.cacheSize(128L * 1024L)).isSameAs(configured);
        assertThat(configured.comparator(BYTEWISE_COMPARATOR)).isSameAs(configured);
        assertThat(configured.logger(logger)).isSameAs(configured);
        assertThat(configured.paranoidChecks(true)).isSameAs(configured);

        configured.logger().log("configured");
        assertThat(configured.createIfMissing()).isFalse();
        assertThat(configured.errorIfExists()).isTrue();
        assertThat(configured.writeBufferSize()).isEqualTo(32 * 1024);
        assertThat(configured.maxOpenFiles()).isEqualTo(42);
        assertThat(configured.blockRestartInterval()).isEqualTo(8);
        assertThat(configured.blockSize()).isEqualTo(2048);
        assertThat(configured.compressionType()).isEqualTo(CompressionType.NONE);
        assertThat(configured.verifyChecksums()).isFalse();
        assertThat(configured.cacheSize()).isEqualTo(128L * 1024L);
        assertThat(configured.comparator()).isSameAs(BYTEWISE_COMPARATOR);
        assertThat(configured.logger()).isSameAs(logger);
        assertThat(configured.paranoidChecks()).isTrue();
        assertThat(messages).containsExactly("configured");
    }

    @Test
    void compressionTypesRoundTripPersistentIds() {
        assertThat(CompressionType.NONE.persistentId()).isZero();
        assertThat(CompressionType.SNAPPY.persistentId()).isEqualTo(1);
        assertThat(CompressionType.getCompressionTypeByPersistentId(CompressionType.NONE.persistentId()))
                .isEqualTo(CompressionType.NONE);
        assertThat(CompressionType.getCompressionTypeByPersistentId(CompressionType.SNAPPY.persistentId()))
                .isEqualTo(CompressionType.SNAPPY);
        assertThat(CompressionType.valueOf("SNAPPY")).isEqualTo(CompressionType.SNAPPY);
        assertThat(CompressionType.values()).containsExactly(CompressionType.NONE, CompressionType.SNAPPY);
        assertThatThrownBy(() -> CompressionType.getCompressionTypeByPersistentId(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown persistentId 99");
    }

    @Test
    void rangeValidatesBoundsAndExposesTheConfiguredKeys() {
        byte[] start = bytes("alpha");
        byte[] limit = bytes("omega");
        Range range = new Range(start, limit);

        assertThat(range.start()).isSameAs(start).containsExactly(bytes("alpha"));
        assertThat(range.limit()).isSameAs(limit).containsExactly(bytes("omega"));
        start[0] = 'A';
        limit[0] = 'O';
        assertThat(range.start()).containsExactly(bytes("Alpha"));
        assertThat(range.limit()).containsExactly(bytes("Omega"));

        assertThatThrownBy(() -> new Range(null, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start");
        assertThatThrownBy(() -> new Range(start, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void readAndWriteOptionsKeepSnapshotAndCachePreferences() throws IOException {
        Snapshot snapshot = new InMemorySnapshot(Map.of(new ByteArrayKey(bytes("key")), bytes("value")));
        ReadOptions readDefaults = new ReadOptions();
        assertThat(readDefaults.snapshot()).isNull();
        assertThat(readDefaults.fillCache()).isTrue();
        assertThat(readDefaults.verifyChecksums()).isFalse();

        assertThat(readDefaults.snapshot(snapshot)).isSameAs(readDefaults);
        assertThat(readDefaults.fillCache(false)).isSameAs(readDefaults);
        assertThat(readDefaults.verifyChecksums(true)).isSameAs(readDefaults);
        assertThat(readDefaults.snapshot()).isSameAs(snapshot);
        assertThat(readDefaults.fillCache()).isFalse();
        assertThat(readDefaults.verifyChecksums()).isTrue();

        WriteOptions writeOptions = new WriteOptions();
        assertThat(writeOptions.sync()).isFalse();
        assertThat(writeOptions.snapshot()).isFalse();
        assertThat(writeOptions.sync(true)).isSameAs(writeOptions);
        assertThat(writeOptions.snapshot(true)).isSameAs(writeOptions);
        assertThat(writeOptions.sync()).isTrue();
        assertThat(writeOptions.snapshot()).isTrue();

        snapshot.close();
        assertThat(((InMemorySnapshot) snapshot).isClosed()).isTrue();
    }

    @Test
    void dbFactoryDbBatchIteratorAndSnapshotContractsAreUsable(@TempDir Path directory) throws Exception {
        InMemoryDBFactory factory = new InMemoryDBFactory();
        Options options = new Options().comparator(BYTEWISE_COMPARATOR).createIfMissing(true);

        try (DB db = factory.open(directory.toFile(), options)) {
            db.put(bytes("alpha"), bytes("one"));
            Snapshot firstSnapshot = db.put(bytes("bravo"), bytes("two"), new WriteOptions().snapshot(true));
            db.put(bytes("charlie"), bytes("three"));

            assertThat(db.get(bytes("alpha"))).containsExactly(bytes("one"));
            assertThat(db.get(bytes("charlie"), new ReadOptions().snapshot(firstSnapshot))).isNull();

            try (WriteBatch batch = db.createWriteBatch()) {
                assertThat(batch.put(bytes("delta"), bytes("four"))).isSameAs(batch);
                assertThat(batch.delete(bytes("alpha"))).isSameAs(batch);
                assertThat(db.write(batch, new WriteOptions().snapshot(true))).isNotNull();
            }
            assertThat(db.get(bytes("alpha"))).isNull();
            assertThat(db.get(bytes("delta"))).containsExactly(bytes("four"));

            assertThat(db.getApproximateSizes(new Range(bytes("bravo"), bytes("echo"))))
                    .containsExactly(3L);
            assertThat(db.getProperty("leveldb.num-entries")).isEqualTo("3");

            try (DBIterator iterator = db.iterator(new ReadOptions().fillCache(false))) {
                iterator.seek(bytes("charlie"));
                assertEntry(iterator.peekNext(), "charlie", "three");
                assertEntry(iterator.next(), "charlie", "three");
                assertEntry(iterator.peekNext(), "delta", "four");

                iterator.seekToLast();
                assertThat(iterator.hasPrev()).isTrue();
                assertEntry(iterator.peekPrev(), "delta", "four");
                assertEntry(iterator.prev(), "delta", "four");

                iterator.seekToFirst();
                assertEntry(iterator.next(), "bravo", "two");
            }

            Snapshot deleteSnapshot = db.delete(bytes("delta"), new WriteOptions().snapshot(true));
            assertThat(db.get(bytes("delta"))).isNull();
            assertThat(db.get(bytes("delta"), new ReadOptions().snapshot(deleteSnapshot))).isNull();

            db.compactRange(bytes("bravo"), bytes("delta"));
            db.suspendCompactions();
            db.resumeCompactions();
        }

        assertThat(factory.openedDirectory).isEqualTo(directory.toFile());
        factory.repair(directory.toFile(), options);
        factory.destroy(directory.toFile(), options);
        assertThat(factory.repairedDirectories).containsExactly(directory.toFile());
        assertThat(factory.destroyedDirectories).containsExactly(directory.toFile());
    }

    @Test
    void dbIterableProvidesEntriesForEnhancedForLoops() throws Exception {
        try (DB db = new InMemoryDB(BYTEWISE_COMPARATOR)) {
            db.put(bytes("one"), bytes("first"));
            db.put(bytes("two"), bytes("second"));
            db.put(bytes("three"), bytes("third"));

            List<String> entries = new ArrayList<>();
            for (Map.Entry<byte[], byte[]> entry : db) {
                entries.add(string(entry.getKey()) + "=" + string(entry.getValue()));
            }

            assertThat(entries).containsExactly("one=first", "three=third", "two=second");
        }
    }

    @Test
    void approximateSizesCanEvaluateMultipleRangesInOneCall() throws Exception {
        try (DB db = new InMemoryDB(BYTEWISE_COMPARATOR)) {
            db.put(bytes("alpha"), bytes("one"));
            db.put(bytes("bravo"), bytes("two"));
            db.put(bytes("charlie"), bytes("three"));
            db.put(bytes("delta"), bytes("four"));
            db.put(bytes("echo"), bytes("five"));

            long[] sizes = db.getApproximateSizes(
                    new Range(bytes("alpha"), bytes("charlie")),
                    new Range(bytes("charlie"), bytes("foxtrot")),
                    new Range(bytes("beta"), bytes("delta")));

            assertThat(sizes).containsExactly(2L, 3L, 2L);
        }
    }

    @Test
    void dbExceptionConstructorsPreserveMessagesAndCauses() {
        IllegalStateException cause = new IllegalStateException("disk full");

        DBException empty = new DBException();
        assertThat(empty.getMessage()).isNull();
        assertThat(empty.getCause()).isNull();
        DBException withMessage = new DBException("failed");
        assertThat(withMessage.getMessage()).isEqualTo("failed");
        assertThat(withMessage.getCause()).isNull();
        DBException withCause = new DBException(cause);
        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(withCause.getMessage()).contains("disk full");
        DBException withMessageAndCause = new DBException("failed", cause);
        assertThat(withMessageAndCause.getMessage()).isEqualTo("failed");
        assertThat(withMessageAndCause.getCause()).isSameAs(cause);
    }

    @Test
    void customComparatorCanShortenKeysAndSortLevelDbByteArrays() {
        assertThat(BYTEWISE_COMPARATOR.name()).isEqualTo("test.BytewiseComparator");
        assertThat(BYTEWISE_COMPARATOR.compare(bytes("abc"), bytes("abd"))).isNegative();
        assertThat(BYTEWISE_COMPARATOR.compare(bytes("abc"), bytes("abc"))).isZero();
        assertThat(BYTEWISE_COMPARATOR.compare(bytes("abd"), bytes("abc"))).isPositive();
        assertThat(BYTEWISE_COMPARATOR.findShortestSeparator(bytes("alpha"), bytes("alphabet")))
                .containsExactly(bytes("alpha"));
        assertThat(BYTEWISE_COMPARATOR.findShortSuccessor(bytes("alpha"))).containsExactly(bytes("alpha"));
    }

    private static void assertEntry(Map.Entry<byte[], byte[]> entry, String key, String value) {
        assertThat(entry.getKey()).containsExactly(bytes(key));
        assertThat(entry.getValue()).containsExactly(bytes(value));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static final class BytewiseComparator implements DBComparator {
        @Override
        public String name() {
            return "test.BytewiseComparator";
        }

        @Override
        public byte[] findShortestSeparator(byte[] start, byte[] limit) {
            return Arrays.copyOf(start, start.length);
        }

        @Override
        public byte[] findShortSuccessor(byte[] key) {
            return Arrays.copyOf(key, key.length);
        }

        @Override
        public int compare(byte[] left, byte[] right) {
            int max = Math.min(left.length, right.length);
            for (int index = 0; index < max; index++) {
                int compared = Byte.compareUnsigned(left[index], right[index]);
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(left.length, right.length);
        }
    }

    private static final class InMemoryDBFactory implements DBFactory {
        private final List<File> destroyedDirectories = new ArrayList<>();
        private final List<File> repairedDirectories = new ArrayList<>();
        private File openedDirectory;

        @Override
        public DB open(File path, Options options) {
            openedDirectory = path;
            return new InMemoryDB(options.comparator() == null ? BYTEWISE_COMPARATOR : options.comparator());
        }

        @Override
        public void destroy(File path, Options options) {
            destroyedDirectories.add(path);
        }

        @Override
        public void repair(File path, Options options) {
            repairedDirectories.add(path);
        }
    }

    private static final class InMemoryDB implements DB {
        private final NavigableMap<ByteArrayKey, byte[]> records;
        private boolean compactionsSuspended;

        private InMemoryDB(DBComparator comparator) {
            records = new TreeMap<>((left, right) -> comparator.compare(left.bytes, right.bytes));
        }

        @Override
        public byte[] get(byte[] key) {
            return get(key, new ReadOptions());
        }

        @Override
        public byte[] get(byte[] key, ReadOptions options) {
            NavigableMap<ByteArrayKey, byte[]> source = snapshotData(options.snapshot());
            byte[] value = source.get(new ByteArrayKey(key));
            return value == null ? null : Arrays.copyOf(value, value.length);
        }

        @Override
        public DBIterator iterator() {
            return iterator(new ReadOptions());
        }

        @Override
        public DBIterator iterator(ReadOptions options) {
            return new InMemoryIterator(snapshotData(options.snapshot()));
        }

        @Override
        public void put(byte[] key, byte[] value) {
            put(key, value, new WriteOptions());
        }

        @Override
        public void delete(byte[] key) {
            delete(key, new WriteOptions());
        }

        @Override
        public void write(WriteBatch updates) {
            write(updates, new WriteOptions());
        }

        @Override
        public WriteBatch createWriteBatch() {
            return new InMemoryWriteBatch();
        }

        @Override
        public Snapshot put(byte[] key, byte[] value, WriteOptions options) {
            records.put(new ByteArrayKey(key), Arrays.copyOf(value, value.length));
            return snapshotIfRequested(options);
        }

        @Override
        public Snapshot delete(byte[] key, WriteOptions options) {
            records.remove(new ByteArrayKey(key));
            return snapshotIfRequested(options);
        }

        @Override
        public Snapshot write(WriteBatch updates, WriteOptions options) {
            InMemoryWriteBatch batch = (InMemoryWriteBatch) updates;
            batch.applyTo(records);
            return snapshotIfRequested(options);
        }

        @Override
        public Snapshot getSnapshot() {
            return new InMemorySnapshot(records);
        }

        @Override
        public long[] getApproximateSizes(Range... ranges) {
            long[] sizes = new long[ranges.length];
            for (int index = 0; index < ranges.length; index++) {
                ByteArrayKey start = new ByteArrayKey(ranges[index].start());
                ByteArrayKey limit = new ByteArrayKey(ranges[index].limit());
                sizes[index] = records.subMap(start, true, limit, false).size();
            }
            return sizes;
        }

        @Override
        public String getProperty(String name) {
            if ("leveldb.num-entries".equals(name)) {
                return String.valueOf(records.size());
            }
            return null;
        }

        @Override
        public void suspendCompactions() throws InterruptedException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while suspending compactions");
            }
            compactionsSuspended = true;
        }

        @Override
        public void resumeCompactions() {
            compactionsSuspended = false;
        }

        @Override
        public void compactRange(byte[] begin, byte[] end) {
            assertThat(compactionsSuspended).isFalse();
            assertThat(new BytewiseComparator().compare(begin, end)).isLessThanOrEqualTo(0);
        }

        @Override
        public void close() {
            records.clear();
        }

        private NavigableMap<ByteArrayKey, byte[]> snapshotData(Snapshot snapshot) {
            if (snapshot == null) {
                return copyOf(records);
            }
            return copyOf(((InMemorySnapshot) snapshot).records);
        }

        private Snapshot snapshotIfRequested(WriteOptions options) {
            if (options.snapshot()) {
                return getSnapshot();
            }
            return null;
        }
    }

    private static final class InMemoryWriteBatch implements WriteBatch {
        private final List<BatchOperation> operations = new ArrayList<>();
        private boolean closed;

        @Override
        public WriteBatch put(byte[] key, byte[] value) {
            assertOpen();
            operations.add(new BatchOperation(new ByteArrayKey(key), Arrays.copyOf(value, value.length)));
            return this;
        }

        @Override
        public WriteBatch delete(byte[] key) {
            assertOpen();
            operations.add(new BatchOperation(new ByteArrayKey(key), null));
            return this;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void applyTo(NavigableMap<ByteArrayKey, byte[]> target) {
            assertOpen();
            for (BatchOperation operation : operations) {
                if (operation.value == null) {
                    target.remove(operation.key);
                } else {
                    target.put(operation.key, Arrays.copyOf(operation.value, operation.value.length));
                }
            }
        }

        private void assertOpen() {
            if (closed) {
                throw new DBException("WriteBatch is closed");
            }
        }
    }

    private static final class InMemoryIterator implements DBIterator {
        private final List<Map.Entry<byte[], byte[]>> entries;
        private int position;
        private boolean closed;

        private InMemoryIterator(NavigableMap<ByteArrayKey, byte[]> records) {
            entries = records.entrySet().stream()
                    .<Map.Entry<byte[], byte[]>>map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey().copyBytes(), Arrays.copyOf(entry.getValue(), entry.getValue().length)))
                    .toList();
        }

        @Override
        public void seek(byte[] key) {
            assertOpen();
            ByteArrayKey target = new ByteArrayKey(key);
            position = 0;
            while (position < entries.size() && BYTEWISE_COMPARATOR.compare(entries.get(position).getKey(), target.bytes) < 0) {
                position++;
            }
        }

        @Override
        public void seekToFirst() {
            assertOpen();
            position = 0;
        }

        @Override
        public Map.Entry<byte[], byte[]> peekNext() {
            assertOpen();
            return entries.get(position);
        }

        @Override
        public boolean hasPrev() {
            assertOpen();
            return position > 0;
        }

        @Override
        public Map.Entry<byte[], byte[]> prev() {
            assertOpen();
            position--;
            return entries.get(position);
        }

        @Override
        public Map.Entry<byte[], byte[]> peekPrev() {
            assertOpen();
            return entries.get(position - 1);
        }

        @Override
        public void seekToLast() {
            assertOpen();
            position = entries.size();
        }

        @Override
        public boolean hasNext() {
            assertOpen();
            return position < entries.size();
        }

        @Override
        public Map.Entry<byte[], byte[]> next() {
            assertOpen();
            Map.Entry<byte[], byte[]> next = entries.get(position);
            position++;
            return next;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void assertOpen() {
            if (closed) {
                throw new DBException("Iterator is closed");
            }
        }
    }

    private static final class InMemorySnapshot implements Snapshot {
        private final NavigableMap<ByteArrayKey, byte[]> records;
        private boolean closed;

        private InMemorySnapshot(Map<ByteArrayKey, byte[]> records) {
            this.records = copyOf(records);
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static NavigableMap<ByteArrayKey, byte[]> copyOf(Map<ByteArrayKey, byte[]> source) {
        NavigableMap<ByteArrayKey, byte[]> copy = new TreeMap<>((left, right) -> BYTEWISE_COMPARATOR.compare(left.bytes, right.bytes));
        source.forEach((key, value) -> copy.put(new ByteArrayKey(key.bytes), Arrays.copyOf(value, value.length)));
        return copy;
    }

    private static final class BatchOperation {
        private final ByteArrayKey key;
        private final byte[] value;

        private BatchOperation(ByteArrayKey key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class ByteArrayKey {
        private final byte[] bytes;

        private ByteArrayKey(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        private byte[] copyBytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ByteArrayKey)) {
                return false;
            }
            ByteArrayKey that = (ByteArrayKey) other;
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(bytes));
        }
    }
}
