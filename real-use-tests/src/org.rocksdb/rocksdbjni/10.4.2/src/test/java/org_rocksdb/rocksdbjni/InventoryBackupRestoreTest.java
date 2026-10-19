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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupEngineOptions;
import org.rocksdb.BackupInfo;
import org.rocksdb.Env;
import org.rocksdb.Options;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

/**
 * Online backup and restore with `BackupEngine`: taking incremental backups of an open
 * database with application metadata, listing them through `BackupInfo`, restoring the latest
 * or a chosen backup into a fresh directory, and pruning old backups by count or by id.
 *
 * A warehouse keeps its stock levels in RocksDB and takes a labeled backup at the end of every
 * shift. Disaster recovery restores the latest backup onto a replacement host directory, an
 * audit restores the stock as it stood after an earlier shift, and the retention policy keeps
 * only the newest backups.
 */
public class InventoryBackupRestoreTest {

    @TempDir
    Path workDir;

    @BeforeAll
    static void loadNativeLibrary() {
        RocksDB.loadLibrary();
    }

    @Test
    void restoresTheLatestShiftBackupOntoAReplacementDirectory() throws Exception {
        String livePath = workDir.resolve("live").toString();
        String backupPath = workDir.resolve("backups").toString();
        String replacementPath = workDir.resolve("replacement").toString();

        try (Warehouse warehouse = Warehouse.open(livePath);
                Backups backups = Backups.open(backupPath)) {
            warehouse.receive("bolt-m6", 400);
            warehouse.receive("nut-m6", 380);
            backups.endOfShift(warehouse, "shift-1");

            warehouse.ship("bolt-m6", 150);
            warehouse.receive("washer-m6", 1_000);
            backups.endOfShift(warehouse, "shift-2");

            warehouse.ship("nut-m6", 380);

            List<BackupInfo> taken = backups.engine.getBackupInfo();
            assertEquals(2, taken.size());
            assertEquals(List.of("shift-1", "shift-2"),
                    taken.stream().map(BackupInfo::appMetadata).toList());
            assertTrue(taken.get(0).backupId() < taken.get(1).backupId());
            for (BackupInfo info : taken) {
                assertTrue(info.numberFiles() > 0, "every backup lists the files it needs");
                assertTrue(info.size() > 0);
                assertTrue(info.timestamp() > 0);
            }
            assertEquals(0, backups.engine.getCorruptedBackups().length);

            backups.restoreLatest(replacementPath);
        }

        try (Warehouse replacement = Warehouse.open(replacementPath)) {
            assertEquals(Map.of("bolt-m6", 250L, "nut-m6", 380L, "washer-m6", 1_000L),
                    replacement.stock(), "work after the last backup is not in the restore");
        }

        try (Warehouse live = Warehouse.open(livePath)) {
            assertNull(live.db.get(bytes("nut-m6")), "restoring never touches the live database");
            assertEquals(250L, live.level("bolt-m6"));
        }
    }

    @Test
    void restoresAnEarlierShiftAndPrunesBackupsByRetentionPolicy() throws Exception {
        String livePath = workDir.resolve("live").toString();
        String backupPath = workDir.resolve("backups").toString();

        try (Warehouse warehouse = Warehouse.open(livePath);
                Backups backups = Backups.open(backupPath)) {
            warehouse.receive("gear-12t", 60);
            backups.endOfShift(warehouse, "monday");
            warehouse.ship("gear-12t", 20);
            backups.endOfShift(warehouse, "tuesday");
            warehouse.receive("chain-116", 30);
            backups.endOfShift(warehouse, "wednesday");
            warehouse.ship("gear-12t", 40);
            backups.endOfShift(warehouse, "thursday");

            int tuesday = backups.idOf("tuesday");
            String auditPath = workDir.resolve("audit-tuesday").toString();
            backups.restore(tuesday, auditPath);
            try (Warehouse audit = Warehouse.open(auditPath)) {
                assertEquals(Map.of("gear-12t", 40L), audit.stock());
            }

            backups.engine.purgeOldBackups(2);
            assertEquals(List.of("wednesday", "thursday"), backups.labels());

            backups.engine.deleteBackup(backups.idOf("wednesday"));
            backups.engine.garbageCollect();
            assertEquals(List.of("thursday"), backups.labels());

            RocksDBException purged = assertThrows(RocksDBException.class,
                    () -> backups.restore(tuesday, workDir.resolve("gone").toString()));
            assertTrue(purged.getMessage() != null && !purged.getMessage().isEmpty());
        }

        try (Backups reopened = Backups.open(backupPath)) {
            assertEquals(List.of("thursday"), reopened.labels(),
                    "the backup catalog survives reopening the engine");
            String restoredPath = workDir.resolve("thursday").toString();
            reopened.restoreLatest(restoredPath);
            try (Warehouse restored = Warehouse.open(restoredPath)) {
                assertEquals(Map.of("chain-116", 30L), restored.stock());
                assertNull(restored.db.get(bytes("gear-12t")), "a sold-out item is deleted");
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** The application's stock store: item code to on-hand quantity as a decimal string. */
    private static final class Warehouse implements AutoCloseable {

        private final Options options;
        private final RocksDB db;

        private Warehouse(Options options, RocksDB db) {
            this.options = options;
            this.db = db;
        }

        static Warehouse open(String path) throws RocksDBException {
            Options options = new Options().setCreateIfMissing(true);
            return new Warehouse(options, RocksDB.open(options, path));
        }

        long level(String item) throws RocksDBException {
            byte[] value = db.get(bytes(item));
            return value == null ? 0L : Long.parseLong(text(value));
        }

        void receive(String item, long quantity) throws RocksDBException {
            db.put(bytes(item), bytes(Long.toString(level(item) + quantity)));
        }

        void ship(String item, long quantity) throws RocksDBException {
            long remaining = level(item) - quantity;
            if (remaining < 0) {
                throw new IllegalStateException("not enough " + item);
            }
            if (remaining == 0) {
                db.delete(bytes(item));
            } else {
                db.put(bytes(item), bytes(Long.toString(remaining)));
            }
        }

        Map<String, Long> stock() {
            Map<String, Long> stock = new TreeMap<>();
            try (RocksIterator it = db.newIterator()) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    stock.put(text(it.key()), Long.parseLong(text(it.value())));
                }
            }
            return stock;
        }

        @Override
        public void close() {
            db.close();
            options.close();
        }
    }

    /** The shift-end backup job: one `BackupEngine` over a dedicated backup directory. */
    private static final class Backups implements AutoCloseable {

        private final BackupEngineOptions options;
        private final BackupEngine engine;

        private Backups(BackupEngineOptions options, BackupEngine engine) {
            this.options = options;
            this.engine = engine;
        }

        static Backups open(String path) throws RocksDBException, IOException {
            Files.createDirectories(Path.of(path));
            BackupEngineOptions options = new BackupEngineOptions(path).setSync(true);
            return new Backups(options, BackupEngine.open(Env.getDefault(), options));
        }

        void endOfShift(Warehouse warehouse, String label) throws RocksDBException {
            engine.createNewBackupWithMetadata(warehouse.db, label, true);
        }

        List<String> labels() {
            return engine.getBackupInfo().stream().map(BackupInfo::appMetadata).toList();
        }

        int idOf(String label) {
            return engine.getBackupInfo().stream()
                    .filter(info -> label.equals(info.appMetadata()))
                    .mapToInt(BackupInfo::backupId)
                    .findFirst()
                    .orElseThrow();
        }

        void restoreLatest(String path) throws RocksDBException {
            try (RestoreOptions restore = new RestoreOptions(false)) {
                engine.restoreDbFromLatestBackup(path, path, restore);
            }
        }

        void restore(int backupId, String path) throws RocksDBException {
            try (RestoreOptions restore = new RestoreOptions(false)) {
                engine.restoreDbFromBackup(backupId, path, path, restore);
            }
        }

        @Override
        public void close() {
            engine.close();
            options.close();
        }
    }
}
