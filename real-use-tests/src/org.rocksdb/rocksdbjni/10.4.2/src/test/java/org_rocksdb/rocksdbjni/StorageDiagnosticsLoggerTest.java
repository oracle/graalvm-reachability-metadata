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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.CompactRangeOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.Logger;
import org.rocksdb.LoggerType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

/**
 * Info logging routed to a Java `Logger`: RocksDB's diagnostic log lines from database open,
 * flush, and compaction are delivered to an application callback instead of the `LOG` file,
 * filtered by an `InfoLogLevel` that can be raised or lowered while the database runs.
 *
 * A storage service forwards RocksDB diagnostics into its own log pipeline so operators see
 * the engine banner and maintenance activity next to application events, and keeps the volume
 * down by running at `WARN_LEVEL` until an incident calls for verbose output.
 */
public class StorageDiagnosticsLoggerTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void forwardsStartupBannerToApplicationLogInsteadOfLogFile() throws Exception {
        Path dbDir = workDir.resolve("orders");

        try (LogPipeline pipeline = new LogPipeline(InfoLogLevel.INFO_LEVEL);
                Options options = new Options().setCreateIfMissing(true).setLogger(pipeline)) {
            assertEquals(LoggerType.JAVA_IMPLEMENTATION, pipeline.getLoggerType());
            assertEquals(InfoLogLevel.INFO_LEVEL, pipeline.infoLogLevel());

            try (RocksDB db = RocksDB.open(options, dbDir.toString())) {
                db.put(bytes("order-1"), bytes("placed"));
                assertArrayEquals(bytes("placed"), db.get(bytes("order-1")));
            }

            assertTrue(pipeline.anyContains("RocksDB version: 10.4.2"),
                    "the engine banner names the library version");
            assertTrue(pipeline.anyContains("Options.create_if_missing: 1"),
                    "the options dump reflects the application's settings");
            assertTrue(pipeline.anyContains(dbDir.toString()), "a line names the database path");
        }

        try (Stream<Path> files = Files.list(dbDir)) {
            List<String> names = files.map(file -> file.getFileName().toString()).toList();
            assertTrue(names.contains("CURRENT"), "the database itself was written");
            assertFalse(names.contains("LOG"), "diagnostics went to the Java logger, not a LOG file");
        }
    }

    @Test
    void quietLevelSuppressesRoutineMaintenanceLines() throws Exception {
        try (LogPipeline pipeline = new LogPipeline(InfoLogLevel.WARN_LEVEL);
                Options options = new Options().setCreateIfMissing(true).setLogger(pipeline);
                RocksDB db = RocksDB.open(options, workDir.resolve("quiet").toString());
                FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
            writeOrders(db, 0, 200);
            db.flush(flush);
            writeOrders(db, 200, 400);
            db.flush(flush);
            db.compactRange();

            assertEquals(400, countOrders(db));
            assertTrue(pipeline.linesBelow(InfoLogLevel.WARN_LEVEL).isEmpty(),
                    "no DEBUG or INFO line reaches the pipeline at WARN_LEVEL: "
                            + pipeline.linesBelow(InfoLogLevel.WARN_LEVEL));
            assertFalse(pipeline.anyContainsIgnoreCase("flush"), "flushes stay out of a quiet log");
        }
    }

    @Test
    void raisingVerbosityAtRuntimeSurfacesFlushAndCompaction() throws Exception {
        try (LogPipeline pipeline = new LogPipeline(InfoLogLevel.WARN_LEVEL);
                Options options = new Options().setCreateIfMissing(true).setLogger(pipeline);
                RocksDB db = RocksDB.open(options, workDir.resolve("incident").toString());
                FlushOptions flush = new FlushOptions().setWaitForFlush(true);
                CompactRangeOptions compaction = new CompactRangeOptions()
                        .setBottommostLevelCompaction(
                                CompactRangeOptions.BottommostLevelCompaction.kForce)) {
            writeOrders(db, 0, 100);
            db.flush(flush);
            assertFalse(pipeline.anyContainsIgnoreCase("flush"), "quiet until the incident starts");

            pipeline.setInfoLogLevel(InfoLogLevel.INFO_LEVEL);
            assertEquals(InfoLogLevel.INFO_LEVEL, pipeline.infoLogLevel());

            writeOrders(db, 100, 300);
            db.flush(flush);
            assertTrue(pipeline.anyContainsIgnoreCase("flush"),
                    "a flush after raising verbosity is reported");

            db.compactRange(db.getDefaultColumnFamily(), null, null, compaction);
            assertTrue(pipeline.anyContainsIgnoreCase("compact"), "the manual compaction is reported");
            assertFalse(pipeline.linesAt(InfoLogLevel.INFO_LEVEL).isEmpty());
            assertEquals(300, countOrders(db));

            pipeline.setInfoLogLevel(InfoLogLevel.ERROR_LEVEL);
            int beforeQuiet = pipeline.linesBelow(InfoLogLevel.ERROR_LEVEL).size();
            writeOrders(db, 300, 400);
            db.flush(flush);
            assertEquals(beforeQuiet, pipeline.linesBelow(InfoLogLevel.ERROR_LEVEL).size(),
                    "lowering verbosity again stops routine lines");
            assertEquals(400, countOrders(db));
        }
    }

    @Test
    void oneApplicationLoggerServesSeveralDatabases() throws Exception {
        Path ordersDir = workDir.resolve("orders-shard");
        Path customersDir = workDir.resolve("customers-shard");

        try (LogPipeline pipeline = new LogPipeline(InfoLogLevel.INFO_LEVEL);
                Options options = new Options().setCreateIfMissing(true).setLogger(pipeline)) {
            try (RocksDB orders = RocksDB.open(options, ordersDir.toString());
                    RocksDB customers = RocksDB.open(options, customersDir.toString())) {
                orders.put(bytes("order-9"), bytes("shipped"));
                customers.put(bytes("customer-4"), bytes("gold"));
                assertArrayEquals(bytes("shipped"), orders.get(bytes("order-9")));
                assertArrayEquals(bytes("gold"), customers.get(bytes("customer-4")));
            }

            assertTrue(pipeline.anyContains(ordersDir.toString()), "the orders shard logged its open");
            assertTrue(pipeline.anyContains(customersDir.toString()),
                    "the customers shard logged its open");
        }
    }

    private static void writeOrders(RocksDB db, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            db.put(bytes("order-%05d".formatted(i)), bytes("status-" + (i % 3)));
        }
    }

    private static int countOrders(RocksDB db) {
        int count = 0;
        try (var iterator = db.newIterator()) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                count++;
            }
        }
        return count;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /// One diagnostic line as the application pipeline records it.
    record LogLine(InfoLogLevel level, String message) {
    }

    /// The application's log sink: RocksDB calls `log` from its own threads, so lines are
    /// appended under a lock and read back as snapshots.
    static final class LogPipeline extends Logger {

        private final List<LogLine> lines = new ArrayList<>();

        LogPipeline(InfoLogLevel level) {
            super(level);
        }

        @Override
        protected void log(InfoLogLevel level, String message) {
            synchronized (lines) {
                lines.add(new LogLine(level, message));
            }
        }

        List<LogLine> snapshot() {
            synchronized (lines) {
                return List.copyOf(lines);
            }
        }

        boolean anyContains(String fragment) {
            return snapshot().stream().anyMatch(line -> line.message().contains(fragment));
        }

        boolean anyContainsIgnoreCase(String fragment) {
            String needle = fragment.toLowerCase(Locale.ROOT);
            return snapshot().stream()
                    .anyMatch(line -> line.message().toLowerCase(Locale.ROOT).contains(needle));
        }

        List<LogLine> linesAt(InfoLogLevel level) {
            return snapshot().stream().filter(line -> line.level() == level).toList();
        }

        List<LogLine> linesBelow(InfoLogLevel threshold) {
            return snapshot().stream()
                    .filter(line -> line.level().ordinal() < threshold.ordinal())
                    .toList();
        }
    }
}
