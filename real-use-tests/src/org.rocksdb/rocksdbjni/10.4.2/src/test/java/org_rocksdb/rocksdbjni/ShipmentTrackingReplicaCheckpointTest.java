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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Checkpoint;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

/**
 * Checkpoints and read replicas: point-in-time copies of a live database taken with
 * `Checkpoint.createCheckpoint`, opened with `RocksDB.openReadOnly`, and a secondary instance
 * opened with `RocksDB.openAsSecondary` that follows the primary through
 * `tryCatchUpWithPrimary`.
 *
 * A shipment tracking service keeps the latest status of every parcel in a primary database.
 * Nightly it freezes a checkpoint for the reporting team, and a dashboard process serves reads
 * from a secondary instance that it refreshes on demand while the primary keeps writing.
 */
public class ShipmentTrackingReplicaCheckpointTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void checkpointFreezesTheStoreWhileThePrimaryMovesOn() throws Exception {
        Path primaryDir = workDir.resolve("primary");
        Path snapshotDir = workDir.resolve("checkpoint-2026-10-19");

        try (Options options = new Options().setCreateIfMissing(true);
                RocksDB primary = RocksDB.open(options, primaryDir.toString())) {
            Tracker tracker = new Tracker(primary);
            tracker.update("PCL-1001", "picked-up");
            tracker.update("PCL-1002", "in-transit");
            flush(primary);
            tracker.update("PCL-1003", "out-for-delivery");

            try (Checkpoint checkpoint = Checkpoint.create(primary)) {
                checkpoint.createCheckpoint(snapshotDir.toString());
            }
            assertTrue(Files.exists(snapshotDir.resolve("CURRENT")),
                    "a checkpoint is a complete database directory");

            tracker.update("PCL-1001", "delivered");
            tracker.update("PCL-1004", "label-created");
            primary.delete(bytes("PCL-1002"));
            assertEquals(Map.of("PCL-1001", "delivered", "PCL-1003", "out-for-delivery",
                    "PCL-1004", "label-created"), tracker.statuses());
        }

        try (Options options = new Options();
                RocksDB report = RocksDB.openReadOnly(options, snapshotDir.toString())) {
            Tracker frozen = new Tracker(report);
            assertEquals(Map.of("PCL-1001", "picked-up", "PCL-1002", "in-transit",
                    "PCL-1003", "out-for-delivery"), frozen.statuses(),
                    "the checkpoint keeps flushed and unflushed writes as of its creation");
            assertNull(report.get(bytes("PCL-1004")));

            RocksDBException rejected = assertThrows(RocksDBException.class,
                    () -> report.put(bytes("PCL-1005"), bytes("picked-up")));
            assertNotNull(rejected.getMessage(), "a read-only instance refuses writes");
        }

        try (Options options = new Options();
                RocksDB restored = RocksDB.open(options, snapshotDir.toString())) {
            Tracker branch = new Tracker(restored);
            branch.update("PCL-1002", "returned-to-sender");
            assertEquals("returned-to-sender", branch.status("PCL-1002"),
                    "a checkpoint opens as an independent writable database");
        }

        try (Options options = new Options();
                RocksDB primary = RocksDB.openReadOnly(options, primaryDir.toString())) {
            assertNull(primary.get(bytes("PCL-1002")),
                    "writes to the checkpoint never reach the primary");
            assertEquals("delivered", new Tracker(primary).status("PCL-1001"));
        }
    }

    @Test
    void secondaryInstanceCatchesUpOnlyWhenAsked() throws Exception {
        Path primaryDir = workDir.resolve("tracking");
        Path secondaryDir = workDir.resolve("dashboard-secondary");

        try (Options primaryOptions = new Options().setCreateIfMissing(true);
                RocksDB primary = RocksDB.open(primaryOptions, primaryDir.toString())) {
            Tracker tracker = new Tracker(primary);
            tracker.update("PCL-2001", "picked-up");
            tracker.update("PCL-2002", "picked-up");
            flush(primary);

            try (Options secondaryOptions = new Options().setMaxOpenFiles(-1);
                    RocksDB secondary = RocksDB.openAsSecondary(secondaryOptions,
                            primaryDir.toString(), secondaryDir.toString())) {
                Tracker dashboard = new Tracker(secondary);
                assertEquals(Map.of("PCL-2001", "picked-up", "PCL-2002", "picked-up"),
                        dashboard.statuses(), "the secondary starts from the primary's state");

                tracker.update("PCL-2001", "in-transit");
                tracker.update("PCL-2003", "label-created");
                assertEquals("picked-up", dashboard.status("PCL-2001"),
                        "the secondary does not see new writes before it catches up");
                assertNull(dashboard.status("PCL-2003"));

                secondary.tryCatchUpWithPrimary();
                assertEquals(Map.of("PCL-2001", "in-transit", "PCL-2002", "picked-up",
                        "PCL-2003", "label-created"), dashboard.statuses(),
                        "catching up replays the primary's write-ahead log");

                primary.delete(bytes("PCL-2002"));
                tracker.update("PCL-2004", "picked-up");
                flush(primary);
                primary.compactRange();
                secondary.tryCatchUpWithPrimary();
                assertEquals(Map.of("PCL-2001", "in-transit", "PCL-2003", "label-created",
                        "PCL-2004", "picked-up"), dashboard.statuses(),
                        "the secondary follows flushes and compactions");

                assertThrows(RocksDBException.class,
                        () -> secondary.put(bytes("PCL-2005"), bytes("picked-up")),
                        "a secondary instance refuses writes");
                assertNull(dashboard.status("PCL-2005"));
                assertArrayEquals(bytes("picked-up"), primary.get(bytes("PCL-2004")));
                assertFalse(Files.exists(secondaryDir.resolve("CURRENT")),
                        "the secondary keeps only its own logs, not a copy of the data");
            }
        }
    }

    private static void flush(RocksDB db) throws RocksDBException {
        try (FlushOptions flush = new FlushOptions().setWaitForFlush(true)) {
            db.flush(flush);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** Parcel statuses keyed by tracking number in one RocksDB instance. */
    private static final class Tracker {

        private final RocksDB db;

        Tracker(RocksDB db) {
            this.db = db;
        }

        void update(String parcel, String status) throws RocksDBException {
            db.put(bytes(parcel), bytes(status));
        }

        String status(String parcel) throws RocksDBException {
            byte[] value = db.get(bytes(parcel));
            return value == null ? null : text(value);
        }

        Map<String, String> statuses() {
            Map<String, String> statuses = new TreeMap<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    statuses.put(text(it.key()), text(it.value()));
                }
            }
            return statuses;
        }
    }
}
