/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.util.DbBackup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JE hot backups to restore an order database while the live application keeps accepting writes.
@Timeout(60)
public class OrderBackupRestoreTest {
    @TempDir
    Path home;

    @Test
    public void restoresBackupBoundaryWithoutIncludingLaterOrderChanges() throws DatabaseException, IOException {
        Path liveHome = Files.createDirectory(home.resolve("live"));
        Path restoredHome = Files.createDirectory(home.resolve("restored"));

        try (OrderStore live = new OrderStore(liveHome, true)) {
            live.put("order-100", "paid");
            live.put("order-101", "awaiting-payment");
            CheckpointConfig checkpoint = new CheckpointConfig();
            checkpoint.setForce(true);
            live.environment.checkpoint(checkpoint);

            DbBackup backup = new DbBackup(live.environment);
            backup.startBackup();
            try {
                // The backup freezes a log-file boundary, not the running application.
                live.put("order-100", "shipped");
                live.put("order-102", "paid");

                String[] logFiles = backup.getLogFilesInBackupSet();
                assertTrue(logFiles.length > 0);
                for (String logFile : logFiles) {
                    Files.copy(liveHome.resolve(logFile), restoredHome.resolve(logFile));
                }
            } finally {
                // Release the backup's protection against log cleaning even if copying fails.
                backup.endBackup();
            }

            live.assertStatus("order-100", "shipped");
            live.assertStatus("order-101", "awaiting-payment");
            live.assertStatus("order-102", "paid");
        }

        // Only the backup's log files are available: opening must recover the saved database.
        try (OrderStore restored = new OrderStore(restoredHome, false)) {
            restored.assertStatus("order-100", "paid");
            restored.assertStatus("order-101", "awaiting-payment");
            assertEquals(OperationStatus.NOTFOUND, restored.database.get(
                    null, entry("order-102"), new DatabaseEntry(), LockMode.DEFAULT));

            // A restored database is usable for normal application work, not just inspection.
            restored.put("order-103", "paid");
        }
        try (OrderStore restarted = new OrderStore(restoredHome, false)) {
            restarted.assertStatus("order-100", "paid");
            restarted.assertStatus("order-103", "paid");
        }
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(value, entry);
        return entry;
    }

    private static final class OrderStore implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private OrderStore(Path directory, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setTransactional(true);
            environmentConfig.setLockTimeout(10_000_000L);
            environmentConfig.setTxnTimeout(30_000_000L);
            environment = new Environment(directory.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            databaseConfig.setTransactional(true);
            try {
                database = environment.openDatabase(null, "orders", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void put(String orderId, String status) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, database.put(null, entry(orderId), entry(status)));
        }

        private void assertStatus(String orderId, String expected) throws DatabaseException {
            DatabaseEntry value = new DatabaseEntry();
            assertEquals(OperationStatus.SUCCESS, database.get(null, entry(orderId), value, LockMode.DEFAULT));
            assertEquals(expected, StringBinding.entryToString(value));
        }

        @Override
        public void close() throws DatabaseException {
            try {
                database.close();
            } finally {
                environment.close();
            }
        }
    }
}
