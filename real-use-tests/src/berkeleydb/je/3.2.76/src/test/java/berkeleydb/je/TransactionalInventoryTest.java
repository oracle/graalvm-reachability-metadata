/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE transactions to commit or cancel stock transfers between warehouses.
@Timeout(60)
public class TransactionalInventoryTest {
    @TempDir
    Path home;

    @Test
    public void persistsConfirmedTransfersAndRollsBackCancelledTransfers() throws DatabaseException {
        try (InventoryStore inventory = new InventoryStore(home, true)) {
            inventory.writeStock(null, "north:coffee", 40);
            inventory.writeStock(null, "south:coffee", 10);

            inventory.transfer("north:coffee", "south:coffee", 12, true);
            assertEquals(28, inventory.stock("north:coffee"));
            assertEquals(22, inventory.stock("south:coffee"));

            inventory.transfer("south:coffee", "north:coffee", 7, false);
            assertEquals(28, inventory.stock("north:coffee"));
            assertEquals(22, inventory.stock("south:coffee"));
        }

        // Reopen without permission to create a database: the committed stock must already exist.
        try (InventoryStore inventory = new InventoryStore(home, false)) {
            assertEquals(28, inventory.stock("north:coffee"));
            assertEquals(22, inventory.stock("south:coffee"));
        }
    }

    private static final class InventoryStore implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private InventoryStore(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setTransactional(true);
            environmentConfig.setLockTimeout(10_000_000L);
            environmentConfig.setTxnTimeout(30_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            databaseConfig.setTransactional(true);
            try {
                database = environment.openDatabase(null, "warehouse-stock", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void transfer(String source, String destination, int quantity, boolean confirmed)
                throws DatabaseException {
            Transaction transaction = environment.beginTransaction(null, null);
            try {
                int sourceStock = readStock(transaction, source, LockMode.RMW);
                int destinationStock = readStock(transaction, destination, LockMode.RMW);
                if (sourceStock < quantity) {
                    throw new IllegalArgumentException("Insufficient stock for transfer");
                }
                writeStock(transaction, source, sourceStock - quantity);
                writeStock(transaction, destination, destinationStock + quantity);
                // The same transaction sees both updates before the operator confirms or cancels.
                assertEquals(sourceStock - quantity, readStock(transaction, source, LockMode.DEFAULT));
                assertEquals(destinationStock + quantity, readStock(transaction, destination, LockMode.DEFAULT));
                if (confirmed) {
                    transaction.commitSync();
                    transaction = null;
                }
            } finally {
                if (transaction != null) {
                    transaction.abort();
                }
            }
        }

        private int stock(String location) throws DatabaseException {
            return readStock(null, location, LockMode.DEFAULT);
        }

        private int readStock(Transaction transaction, String location, LockMode lockMode)
                throws DatabaseException {
            DatabaseEntry value = new DatabaseEntry();
            assertEquals(OperationStatus.SUCCESS, database.get(transaction, key(location), value, lockMode));
            return IntegerBinding.entryToInt(value);
        }

        private void writeStock(Transaction transaction, String location, int quantity) throws DatabaseException {
            DatabaseEntry value = new DatabaseEntry();
            IntegerBinding.intToEntry(quantity, value);
            assertEquals(OperationStatus.SUCCESS, database.put(transaction, key(location), value));
        }

        private static DatabaseEntry key(String location) {
            DatabaseEntry key = new DatabaseEntry();
            StringBinding.stringToEntry(location, key);
            return key;
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
