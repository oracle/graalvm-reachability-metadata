/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.FlushOptions;
import org.rocksdb.MergeOperator;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.UInt64AddOperator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * Merge operators: blind read-modify-write updates through `RocksDB.merge` with the built-in
 * `UInt64AddOperator` and `StringAppendOperator`, resolved on reads, in iterators, inside a
 * `WriteBatch`, and across flush, compaction, and reopening the database.
 *
 * A web analytics service counts page views per URL without reading the current count first,
 * and keeps an append-only trail of the referrers that sent each visit. Both stores rely on
 * RocksDB to fold the pending updates into the stored value.
 */
public class PageViewMergeOperatorTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void countsPageViewsWithBlindIncrements() throws Exception {
        String path = workDir.resolve("views").toString();

        try (Store views = Store.open(path, new UInt64AddOperator())) {
            views.increment("/home", 1);
            views.increment("/home", 1);
            views.increment("/pricing", 3);
            views.increment("/home", 5);
            assertEquals(7L, views.count("/home"));
            assertEquals(3L, views.count("/pricing"));
            assertEquals(0L, views.count("/careers"), "an unseen page has no counter");

            views.db.put(bytes("/pricing"), counter(100));
            views.increment("/pricing", 2);
            assertEquals(102L, views.count("/pricing"), "merges apply on top of a put");

            views.db.delete(bytes("/home"));
            views.increment("/home", 4);
            assertEquals(4L, views.count("/home"), "a delete resets the counter");

            try (WriteBatch batch = new WriteBatch();
                    WriteOptions write = new WriteOptions()) {
                batch.merge(bytes("/docs"), counter(10));
                batch.merge(bytes("/docs"), counter(15));
                batch.merge(bytes("/home"), counter(1));
                views.db.write(write, batch);
            }
            assertEquals(Map.of("/docs", 25L, "/home", 5L, "/pricing", 102L), views.counts(),
                    "iterators see the merged values in key order");

            views.flushAndCompact();
            views.increment("/docs", 1);
            assertEquals(26L, views.count("/docs"), "merges stack on a compacted value");
        }

        try (Store reopened = Store.open(path, new UInt64AddOperator())) {
            assertEquals(Map.of("/docs", 26L, "/home", 5L, "/pricing", 102L),
                    reopened.counts(), "pending merge operands survive reopening");
        }
    }

    @Test
    void appendsReferrersToAnOrderedTrail() throws Exception {
        String path = workDir.resolve("referrers").toString();

        try (Store trail = Store.open(path, new StringAppendOperator('|'))) {
            trail.append("/home", "google");
            trail.append("/home", "newsletter");
            trail.append("/pricing", "ads");
            trail.append("/home", "direct");
            assertEquals(List.of("google", "newsletter", "direct"), trail.referrers("/home"));
            assertEquals(List.of("ads"), trail.referrers("/pricing"));
            assertNull(trail.db.get(bytes("/careers")));

            trail.flushAndCompact();
            trail.append("/pricing", "partner");
            assertEquals("ads|partner", text(trail.db.get(bytes("/pricing"))));
        }

        try (Store reopened = Store.open(path, new StringAppendOperator('|'))) {
            reopened.append("/home", "bookmark");
            assertEquals(List.of("google", "newsletter", "direct", "bookmark"),
                    reopened.referrers("/home"), "appends keep arrival order across reopening");
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** Encodes a count the way `UInt64AddOperator` reads it: a little-endian fixed64. */
    private static byte[] counter(long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    private static long decode(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /** A RocksDB database whose values are folded by one merge operator. */
    private static final class Store implements AutoCloseable {

        private final MergeOperator operator;
        private final Options options;
        private final RocksDB db;

        private Store(MergeOperator operator, Options options, RocksDB db) {
            this.operator = operator;
            this.options = options;
            this.db = db;
        }

        static Store open(String path, MergeOperator operator) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true).setMergeOperator(operator);
            return new Store(operator, options, RocksDB.open(options, path));
        }

        void increment(String page, long by) throws RocksDBException {
            db.merge(bytes(page), counter(by));
        }

        long count(String page) throws RocksDBException {
            byte[] value = db.get(bytes(page));
            return value == null ? 0L : decode(value);
        }

        Map<String, Long> counts() {
            Map<String, Long> counts = new TreeMap<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    counts.put(text(it.key()), decode(it.value()));
                }
            }
            return counts;
        }

        void append(String page, String referrer) throws RocksDBException {
            db.merge(bytes(page), bytes(referrer));
        }

        List<String> referrers(String page) throws RocksDBException {
            byte[] value = db.get(bytes(page));
            return value == null ? List.of() : List.of(text(value).split("\\|"));
        }

        void flushAndCompact() throws RocksDBException {
            try (FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flush);
            }
            db.compactRange();
        }

        @Override
        public void close() {
            db.close();
            options.close();
            operator.close();
        }
    }
}
