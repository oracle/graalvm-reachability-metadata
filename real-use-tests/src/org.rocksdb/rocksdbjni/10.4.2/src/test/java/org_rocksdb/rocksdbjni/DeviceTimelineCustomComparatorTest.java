/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.AbstractComparator;
import org.rocksdb.ComparatorOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;

/**
 * Custom key ordering: a Java `AbstractComparator` installed with `Options.setComparator` that
 * RocksDB calls back for memtable inserts, seeks, iteration bounds, flush, and compaction.
 *
 * A fleet telemetry service stores readings under `device | timestamp` keys and wants each
 * device's readings newest first, so the latest reading is the first key a seek lands on. The
 * comparator orders devices by name and timestamps in descending order, and the database refuses
 * to reopen under a comparator with a different name.
 */
public class DeviceTimelineCustomComparatorTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void iteratesEachDeviceNewestFirst() throws Exception {
        String path = workDir.resolve("timeline").toString();

        try (Timeline timeline = Timeline.open(path)) {
            timeline.record("pump-2", 1_000L, "pressure=4.1");
            timeline.record("pump-1", 3_000L, "pressure=3.9");
            timeline.record("pump-1", 1_000L, "pressure=3.2");
            timeline.record("pump-1", 2_000L, "pressure=3.5");
            timeline.record("pump-10", 5_000L, "pressure=6.0");
            timeline.record("pump-2", 4_000L, "pressure=4.4");

            assertEquals(List.of(
                    "pump-1@3000=pressure=3.9",
                    "pump-1@2000=pressure=3.5",
                    "pump-1@1000=pressure=3.2",
                    "pump-10@5000=pressure=6.0",
                    "pump-2@4000=pressure=4.4",
                    "pump-2@1000=pressure=4.1"), timeline.all());

            assertEquals("pressure=3.9", timeline.latest("pump-1"));
            assertEquals("pressure=4.4", timeline.latest("pump-2"));
            assertNull(timeline.latest("pump-3"), "an unknown device has no reading");

            assertEquals(List.of("pump-1@2000=pressure=3.5", "pump-1@1000=pressure=3.2"),
                    timeline.atOrBefore("pump-1", 2_500L),
                    "seeking to a timestamp lands on the newest reading not after it");
        }
    }

    @Test
    void keepsOrderingAcrossFlushCompactionAndReopen() throws Exception {
        String path = workDir.resolve("durable").toString();

        try (Timeline timeline = Timeline.open(path)) {
            for (long ts = 1; ts <= 50; ts++) {
                timeline.record("valve-a", ts * 10, "open=" + (ts % 2 == 0));
                timeline.record("valve-b", ts * 10, "flow=" + ts);
            }
            timeline.flush();
            timeline.record("valve-a", 505L, "open=late");
            timeline.flush();
            timeline.compact();

            assertEquals("open=late", timeline.latest("valve-a"),
                    "a reading flushed later still sorts by its timestamp");
            assertEquals("flow=50", timeline.latest("valve-b"));
        }

        try (Timeline reopened = Timeline.open(path)) {
            List<String> all = reopened.all();
            assertEquals(101, all.size());
            assertEquals("valve-a@505=open=late", all.get(0));
            assertEquals("valve-a@500=open=true", all.get(1));
            assertEquals("valve-a@10=open=false", all.get(50));
            assertEquals("valve-b@500=flow=50", all.get(51));
            assertEquals("valve-b@10=flow=1", all.get(100));

            assertEquals(List.of("valve-b@30=flow=3", "valve-b@20=flow=2"),
                    reopened.window("valve-b", 30L, 10L),
                    "an upper bound before the oldest reading cuts the scan short");
        }
    }

    @Test
    void scansBackwardsFromTheOldestReading() throws Exception {
        String path = workDir.resolve("backwards").toString();

        try (Timeline timeline = Timeline.open(path)) {
            timeline.record("fan-1", 100L, "rpm=900");
            timeline.record("fan-1", 200L, "rpm=950");
            timeline.record("fan-1", 300L, "rpm=1000");
            timeline.record("fan-2", 100L, "rpm=400");

            assertEquals(List.of("fan-1@100=rpm=900", "fan-1@200=rpm=950", "fan-1@300=rpm=1000"),
                    timeline.oldestFirst("fan-1"));
        }
    }

    @Test
    void refusesToReopenUnderAnotherComparator() throws Exception {
        String path = workDir.resolve("guarded").toString();

        try (Timeline timeline = Timeline.open(path)) {
            timeline.record("sensor-x", 1L, "t=20");
        }

        RocksDBException failure;
        try (Options options = new Options().setCreateIfMissing(false)) {
            failure = assertThrows(RocksDBException.class, () -> RocksDB.open(options, path).close());
        }
        String message = failure.getMessage();
        assertTrue(message.contains(NewestFirstComparator.NAME),
                "the error names the comparator the data was written with: " + message);

        try (Timeline reopened = Timeline.open(path)) {
            assertEquals("t=20", reopened.latest("sensor-x"));
        }
    }

    @Test
    void comparesKeysTheWayTheStoreOrdersThem() {
        try (ComparatorOptions comparatorOptions = new ComparatorOptions();
                NewestFirstComparator comparator = new NewestFirstComparator(comparatorOptions)) {
            assertEquals(NewestFirstComparator.NAME, comparator.name());
            assertTrue(comparator.compare(ByteBuffer.wrap(Timeline.key("a", 9L)),
                    ByteBuffer.wrap(Timeline.key("a", 1L))) < 0);
            assertTrue(comparator.compare(ByteBuffer.wrap(Timeline.key("a", 1L)),
                    ByteBuffer.wrap(Timeline.key("b", 9L))) < 0);
            assertEquals(0, comparator.compare(ByteBuffer.wrap(Timeline.key("a", 5L)),
                    ByteBuffer.wrap(Timeline.key("a", 5L))));
        }
    }

    /** Orders `device | timestamp` keys by device ascending, then timestamp descending. */
    static final class NewestFirstComparator extends AbstractComparator {

        static final String NAME = "fleet.NewestFirstComparator";

        NewestFirstComparator(ComparatorOptions options) {
            super(options);
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public int compare(ByteBuffer a, ByteBuffer b) {
            String deviceA = device(a);
            String deviceB = device(b);
            int byDevice = deviceA.compareTo(deviceB);
            if (byDevice != 0) {
                return byDevice;
            }
            return Long.compare(timestamp(b), timestamp(a));
        }

        private static String device(ByteBuffer key) {
            int start = key.position();
            int end = key.limit() - Long.BYTES - 1;
            byte[] name = new byte[end - start];
            key.duplicate().get(name);
            return new String(name, StandardCharsets.UTF_8);
        }

        private static long timestamp(ByteBuffer key) {
            return key.getLong(key.limit() - Long.BYTES);
        }
    }

    /** A per-device reading store over a database ordered by `NewestFirstComparator`. */
    static final class Timeline implements AutoCloseable {

        private final ComparatorOptions comparatorOptions;
        private final NewestFirstComparator comparator;
        private final Options options;
        private final RocksDB db;

        private Timeline(ComparatorOptions comparatorOptions, NewestFirstComparator comparator,
                Options options, RocksDB db) {
            this.comparatorOptions = comparatorOptions;
            this.comparator = comparator;
            this.options = options;
            this.db = db;
        }

        static Timeline open(String path) throws RocksDBException {
            ComparatorOptions comparatorOptions = new ComparatorOptions();
            NewestFirstComparator comparator = new NewestFirstComparator(comparatorOptions);
            Options options = new Options().setCreateIfMissing(true).setComparator(comparator);
            return new Timeline(comparatorOptions, comparator, options, RocksDB.open(options, path));
        }

        static byte[] key(String device, long timestamp) {
            byte[] name = device.getBytes(StandardCharsets.UTF_8);
            return ByteBuffer.allocate(name.length + 1 + Long.BYTES)
                    .put(name).put((byte) '|').putLong(timestamp).array();
        }

        void record(String device, long timestamp, String reading) throws RocksDBException {
            db.put(key(device, timestamp), reading.getBytes(StandardCharsets.UTF_8));
        }

        String latest(String device) {
            List<String> readings = atOrBefore(device, Long.MAX_VALUE);
            if (readings.isEmpty()) {
                return null;
            }
            String newest = readings.get(0);
            return newest.substring(newest.indexOf('=') + 1);
        }

        List<String> atOrBefore(String device, long timestamp) {
            List<String> readings = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seek(key(device, timestamp)); it.isValid(); it.next()) {
                    if (!device(it.key()).equals(device)) {
                        break;
                    }
                    readings.add(entry(it));
                }
            }
            return readings;
        }

        List<String> window(String device, long newest, long exclusiveOldest) {
            List<String> readings = new ArrayList<>();
            try (Slice upper = new Slice(key(device, exclusiveOldest));
                    ReadOptions read = new ReadOptions().setIterateUpperBound(upper);
                    RocksIterator it = db.newIterator(read)) {
                for (it.seek(key(device, newest)); it.isValid(); it.next()) {
                    readings.add(entry(it));
                }
            }
            return readings;
        }

        List<String> oldestFirst(String device) {
            List<String> readings = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekForPrev(key(device, 0L)); it.isValid(); it.prev()) {
                    if (!device(it.key()).equals(device)) {
                        break;
                    }
                    readings.add(entry(it));
                }
            }
            return readings;
        }

        List<String> all() {
            List<String> readings = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    readings.add(entry(it));
                }
            }
            return readings;
        }

        void flush() throws RocksDBException {
            try (FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
                db.flush(flush);
            }
        }

        void compact() throws RocksDBException {
            db.compactRange();
        }

        private static String entry(RocksIterator it) {
            byte[] key = it.key();
            long timestamp = ByteBuffer.wrap(key, key.length - Long.BYTES, Long.BYTES).getLong();
            return device(key) + "@" + timestamp + "=" + new String(it.value(), StandardCharsets.UTF_8);
        }

        private static String device(byte[] key) {
            return new String(key, 0, key.length - Long.BYTES - 1, StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            db.close();
            options.close();
            comparator.close();
            comparatorOptions.close();
        }
    }
}
