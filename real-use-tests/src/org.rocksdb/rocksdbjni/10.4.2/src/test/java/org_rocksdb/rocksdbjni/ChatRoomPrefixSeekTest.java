/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.FlushOptions;
import org.rocksdb.Holder;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;

/**
 * Prefix seek with bloom filters: a fixed-length prefix extractor installed with
 * `Options.useFixedLengthPrefixExtractor`, prefix and whole-key bloom filters in block-based
 * tables and in the memtable, iterators bounded by `ReadOptions.setPrefixSameAsStart`, full
 * scans with `setTotalOrderSeek`, and `keyMayExist` lookups answered from the filter.
 *
 * A chat service stores every message under `<room id>/<sequence>`, where the room id has a
 * fixed width. Loading one room's history is a prefix seek, and the bloom filters let RocksDB
 * skip table files that hold no message of the requested room.
 */
public class ChatRoomPrefixSeekTest {

    /** Width of `room-xyz/`, the part of every key that names the room. */
    private static final int ROOM_PREFIX_LENGTH = 9;

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void loadsOneRoomHistoryWithPrefixSeek() throws Exception {
        try (ChatStore chat = ChatStore.open(workDir.resolve("chat").toString())) {
            chat.post("room-a01", 1, "hello");
            chat.post("room-a01", 2, "anyone here?");
            chat.post("room-a02", 1, "standup at 10");
            chat.post("room-a01", 3, "yes, me");
            chat.post("room-a02", 2, "moved to 11");
            chat.post("room-b07", 1, "release is out");
            chat.flush();

            assertEquals(List.of("hello", "anyone here?", "yes, me"), chat.history("room-a01"));
            assertEquals(List.of("standup at 10", "moved to 11"), chat.history("room-a02"));
            assertEquals(List.of("release is out"), chat.history("room-b07"));

            long filteredBefore = chat.seeksFiltered();
            assertEquals(List.of(), chat.history("room-z99"), "an empty room has no messages");
            assertTrue(chat.seeksFiltered() > filteredBefore,
                    "the prefix bloom filter rules the table out for an unknown room");

            assertEquals(List.of("anyone here?", "yes, me"), chat.historyFrom("room-a01", 2),
                    "a seek into the middle of a room stays inside that room");

            assertFalse(chat.mayExist("room-a01", 9),
                    "the whole-key filter answers for a message that was never posted");
            assertTrue(chat.mayExist("room-a02", 2));

            chat.post("room-a01", 4, "lunch?");
            chat.post("room-c03", 1, "new room");
            assertEquals(List.of("hello", "anyone here?", "yes, me", "lunch?"),
                    chat.history("room-a01"), "memtable and table file merge within a prefix");
            assertEquals(List.of("new room"), chat.history("room-c03"));

            assertEquals(List.of("room-a01/000001", "room-a01/000002", "room-a01/000003",
                    "room-a01/000004", "room-a02/000001", "room-a02/000002",
                    "room-b07/000001", "room-c03/000001"), chat.allKeys(),
                    "a total-order seek crosses every room in key order");
        }
    }

    @Test
    void keepsReadsCorrectAfterChangingThePrefixExtractor() throws Exception {
        String path = workDir.resolve("migrated").toString();
        try (ChatStore chat = ChatStore.open(path)) {
            chat.post("room-a01", 1, "before migration");
            chat.post("room-b02", 1, "other room");
            chat.flush();
        }

        Statistics statistics = new Statistics();
        Options options = new Options()
                .setCreateIfMissing(true)
                .useCappedPrefixExtractor(4)
                .setStatistics(statistics);
        try (ChatStore chat = new ChatStore(options, statistics, RocksDB.open(options, path))) {
            chat.post("room-a01", 2, "after migration");
            assertEquals(List.of("room-a01/000001", "room-a01/000002", "room-b02/000001"),
                    chat.allKeys(), "old table files stay readable under the new extractor");
            assertEquals("before migration", chat.message("room-a01", 1));
            assertEquals("after migration", chat.message("room-a01", 2));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static String key(String room, int sequence) {
        return String.format("%s/%06d", room, sequence);
    }

    /** A message store whose keys share a fixed-width room prefix. */
    private static final class ChatStore implements AutoCloseable {

        private final Options options;
        private final Statistics statistics;
        private final RocksDB db;

        private ChatStore(Options options, Statistics statistics, RocksDB db) {
            this.options = options;
            this.statistics = statistics;
            this.db = db;
        }

        static ChatStore open(String path) throws RocksDBException {
            Statistics statistics = new Statistics();
            BlockBasedTableConfig table = new BlockBasedTableConfig()
                    .setFilterPolicy(new BloomFilter(10))
                    .setWholeKeyFiltering(true);
            Options options = new Options()
                    .setCreateIfMissing(true)
                    .useFixedLengthPrefixExtractor(ROOM_PREFIX_LENGTH)
                    .setMemtablePrefixBloomSizeRatio(0.1)
                    .setMemtableWholeKeyFiltering(true)
                    .setTableFormatConfig(table)
                    .setStatistics(statistics);
            return new ChatStore(options, statistics, RocksDB.open(options, path));
        }

        void post(String room, int sequence, String message) throws RocksDBException {
            db.put(bytes(key(room, sequence)), bytes(message));
        }

        String message(String room, int sequence) throws RocksDBException {
            return text(db.get(bytes(key(room, sequence))));
        }

        List<String> history(String room) {
            return historyFrom(room, 0);
        }

        List<String> historyFrom(String room, int sequence) {
            List<String> messages = new ArrayList<>();
            try (ReadOptions read = new ReadOptions().setPrefixSameAsStart(true);
                    RocksIterator it = db.newIterator(read)) {
                for (it.seek(bytes(key(room, sequence))); it.isValid(); it.next()) {
                    messages.add(text(it.value()));
                }
            }
            return messages;
        }

        List<String> allKeys() {
            List<String> keys = new ArrayList<>();
            try (ReadOptions read = new ReadOptions().setTotalOrderSeek(true);
                    RocksIterator it = db.newIterator(read)) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    keys.add(text(it.key()));
                }
            }
            return keys;
        }

        boolean mayExist(String room, int sequence) {
            return db.keyMayExist(bytes(key(room, sequence)), new Holder<>());
        }

        /** Seeks that a prefix filter answered without reading a data block, on any level. */
        long seeksFiltered() {
            return statistics.getTickerCount(TickerType.NON_LAST_LEVEL_SEEK_FILTERED)
                    + statistics.getTickerCount(TickerType.LAST_LEVEL_SEEK_FILTERED);
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
            statistics.close();
        }
    }
}
