/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.FlushOptions;
import org.rocksdb.HistogramData;
import org.rocksdb.HistogramType;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;
import org.rocksdb.TickerType;

/**
 * Database statistics: `Statistics` ticker counters and `HistogramData` latency and size
 * histograms that RocksDB collects natively and an application samples for its metrics.
 *
 * A session store exports RocksDB's own counters to its monitoring dashboard: how many keys
 * were written and read, how many reads the memtable answered, how well the block cache serves
 * reads once data is flushed, and how much flush and compaction wrote. The dashboard samples
 * deltas with `getAndResetTickerCount`, reads histograms the native side builds, ignores
 * histograms it does not chart, and clears everything with `reset` between reporting windows.
 */
public class SessionStoreStatisticsTest {

    private static final int SESSIONS = 50;

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void countsWritesReadsAndBlockCacheEffectiveness() throws Exception {
        try (Statistics stats = new Statistics();
                Store store = Store.open(workDir.resolve("sessions").toString(), stats)) {
            assertEquals(StatsLevel.EXCEPT_DETAILED_TIMERS, stats.statsLevel());

            long payloadBytes = 0;
            for (int i = 0; i < SESSIONS; i++) {
                store.put(sessionId(i), token(i));
                payloadBytes += bytes(sessionId(i)).length + bytes(token(i)).length;
            }
            assertEquals(SESSIONS, stats.getAndResetTickerCount(TickerType.NUMBER_KEYS_WRITTEN));
            assertEquals(0L, stats.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN),
                    "sampling a ticker resets it for the next window");
            assertTrue(stats.getTickerCount(TickerType.BYTES_WRITTEN) >= payloadBytes,
                    "every key and value byte is accounted for in the written volume");

            for (int i = 0; i < SESSIONS; i++) {
                assertEquals(token(i), store.get(sessionId(i)));
            }
            assertNull(store.get("session-unknown"));
            assertEquals(SESSIONS, stats.getAndResetTickerCount(TickerType.MEMTABLE_HIT),
                    "unflushed sessions are served from the memtable");
            assertEquals(1L, stats.getAndResetTickerCount(TickerType.MEMTABLE_MISS));
            assertEquals(SESSIONS + 1L, stats.getAndResetTickerCount(TickerType.NUMBER_KEYS_READ));
            HistogramData getLatency = stats.getHistogramData(HistogramType.DB_GET);
            assertEquals(SESSIONS + 1L, getLatency.getCount(), "one latency sample per lookup");
            assertTrue(getLatency.getMax() >= getLatency.getMin());
            assertTrue(getLatency.getPercentile99() >= getLatency.getMedian());
            assertEquals(SESSIONS, stats.getHistogramData(HistogramType.DB_WRITE).getCount());

            store.flush();
            assertTrue(stats.getTickerCount(TickerType.FLUSH_WRITE_BYTES) > 0,
                    "the flush wrote an SST file");
            assertEquals(1L, stats.getHistogramData(HistogramType.FLUSH_TIME).getCount());

            for (int i = 0; i < SESSIONS; i++) {
                assertEquals(token(i), store.get(sessionId(i)));
            }
            assertEquals(0L, stats.getAndResetTickerCount(TickerType.MEMTABLE_HIT),
                    "flushed sessions are no longer in the memtable");
            long coldMisses = stats.getAndResetTickerCount(TickerType.BLOCK_CACHE_DATA_MISS);
            long coldHits = stats.getAndResetTickerCount(TickerType.BLOCK_CACHE_DATA_HIT);
            assertTrue(coldMisses >= 1, "the first read loads the data block into the cache");
            assertEquals(SESSIONS, coldMisses + coldHits, "each lookup reads one data block");

            for (int i = 0; i < SESSIONS; i++) {
                assertEquals(token(i), store.get(sessionId(i)));
            }
            assertEquals(0L, stats.getTickerCount(TickerType.BLOCK_CACHE_DATA_MISS),
                    "a warm cache serves every repeated read");
            assertEquals(SESSIONS, stats.getTickerCount(TickerType.BLOCK_CACHE_DATA_HIT));
        }
    }

    @Test
    void reportsCompactionWorkAndClearsBetweenWindows() throws Exception {
        try (Statistics stats = new Statistics();
                Store store = Store.open(workDir.resolve("compaction").toString(), stats)) {
            stats.setStatsLevel(StatsLevel.ALL);
            assertEquals(StatsLevel.ALL, stats.statsLevel());

            for (int round = 0; round < 2; round++) {
                for (int i = 0; i < SESSIONS; i++) {
                    store.put(sessionId(i), token(i + round));
                }
                store.flush();
            }
            assertEquals(2L, stats.getHistogramData(HistogramType.FLUSH_TIME).getCount());
            assertEquals(0L, stats.getTickerCount(TickerType.COMPACT_WRITE_BYTES));

            store.db.compactRange();
            assertTrue(stats.getTickerCount(TickerType.COMPACT_WRITE_BYTES) > 0,
                    "compaction rewrote the overlapping files");
            assertEquals(SESSIONS, stats.getTickerCount(TickerType.COMPACTION_KEY_DROP_NEWER_ENTRY),
                    "every session's first token was superseded by its second");
            assertEquals(1L, stats.getHistogramData(HistogramType.COMPACTION_TIME).getCount());
            assertEquals(token(1), store.get(sessionId(0)));

            String histogram = stats.getHistogramString(HistogramType.COMPACTION_TIME);
            assertTrue(histogram.contains("Count: 1"), histogram);
            String report = stats.toString();
            assertTrue(report.contains("rocksdb.number.keys.written COUNT : " + (2 * SESSIONS)),
                    report);
            assertTrue(report.contains("rocksdb.db.get.micros"), report);

            stats.reset();
            assertEquals(0L, stats.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN));
            assertEquals(0L, stats.getTickerCount(TickerType.COMPACT_WRITE_BYTES));
            assertEquals(0L, stats.getHistogramData(HistogramType.COMPACTION_TIME).getCount());

            store.put("session-late", "late-token");
            assertEquals(1L, stats.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN),
                    "collection continues in the next window");
        }
    }

    @Test
    void sharesOneCollectorAcrossStoresAndSkipsIgnoredHistograms() throws Exception {
        EnumSet<HistogramType> ignored = EnumSet.of(HistogramType.DB_GET);
        try (Statistics stats = new Statistics(ignored);
                Store primary = Store.open(workDir.resolve("primary").toString(), stats);
                Store replica = Store.open(workDir.resolve("replica").toString(), stats)) {
            primary.put("session-a", "token-a");
            primary.put("session-b", "token-b");
            replica.put("session-a", "token-a");
            assertEquals("token-a", primary.get("session-a"));
            assertEquals("token-a", replica.get("session-a"));
            assertArrayEquals(bytes("token-b"), primary.db.get(bytes("session-b")));

            assertEquals(3L, stats.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN),
                    "both stores report into the shared collector");
            assertEquals(3L, stats.getTickerCount(TickerType.NUMBER_KEYS_READ));
            assertEquals(3L, stats.getHistogramData(HistogramType.DB_WRITE).getCount());
            assertEquals(0L, stats.getHistogramData(HistogramType.DB_GET).getCount(),
                    "an ignored histogram collects nothing");
        }
    }

    private static String sessionId(int index) {
        return String.format("session-%04d", index);
    }

    private static String token(int index) {
        return "token-" + index;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** A session database that reports into a caller-owned `Statistics` collector. */
    private static final class Store implements AutoCloseable {

        private final LRUCache cache;
        private final Options options;
        private final RocksDB db;

        private Store(LRUCache cache, Options options, RocksDB db) {
            this.cache = cache;
            this.options = options;
            this.db = db;
        }

        static Store open(String path, Statistics stats) throws RocksDBException {
            LRUCache cache = new LRUCache(8L * 1024 * 1024);
            Options options = new Options()
                    .setCreateIfMissing(true)
                    .setStatistics(stats)
                    .setTableFormatConfig(new BlockBasedTableConfig().setBlockCache(cache));
            return new Store(cache, options, RocksDB.open(options, path));
        }

        void put(String sessionId, String token) throws RocksDBException {
            db.put(bytes(sessionId), bytes(token));
        }

        String get(String sessionId) throws RocksDBException {
            byte[] value = db.get(bytes(sessionId));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        }

        void flush() throws RocksDBException {
            try (FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flush);
            }
        }

        @Override
        public void close() {
            db.close();
            options.close();
            cache.close();
        }
    }
}
