/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE transactional database naming operations to atomically publish a staged routing directory.
@Timeout(60)
public class RoutingDirectoryPublicationTest {
    @TempDir
    Path home;

    @Test
    public void cancelsThenPublishesAWholeDirectoryReplacementAndReopensIt() throws DatabaseException {
        Map<String, String> original = Map.of(
                "/help", "support-v1", "/shop", "store-v1", "/legacy", "legacy-service");
        Map<String, String> replacement = Map.of(
                "/help", "support-v2", "/shop", "store-v2", "/account", "account-service");

        try (RoutingDirectory directory = new RoutingDirectory(home, true)) {
            directory.stage("routes", original);
            directory.stage("routes-next", replacement);
            directory.assertRoutes("routes", original);
            directory.assertRoutes("routes-next", replacement);

            // Cancelling the deployment restores both database names and the removed old data.
            directory.publish(false);
            assertEquals(Set.of("routes", "routes-next"), directory.databaseNames());
            directory.assertRoutes("routes", original);
            directory.assertRoutes("routes-next", replacement);
        }

        try (RoutingDirectory directory = new RoutingDirectory(home, false)) {
            directory.assertRoutes("routes", original);
            directory.assertRoutes("routes-next", replacement);
            directory.publish(true);
            assertEquals(Set.of("routes"), directory.databaseNames());
            directory.assertRoutes("routes", replacement);
        }

        // Clients open the same stable name after restart and see only the new generation.
        try (RoutingDirectory directory = new RoutingDirectory(home, false)) {
            assertEquals(Set.of("routes"), directory.databaseNames());
            directory.assertRoutes("routes", replacement);
        }
    }

    private static DatabaseEntry entry(String text) {
        DatabaseEntry result = new DatabaseEntry();
        StringBinding.stringToEntry(text, result);
        return result;
    }

    private static final class RoutingDirectory implements AutoCloseable {
        private final Environment environment;

        private RoutingDirectory(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig config = new EnvironmentConfig();
            config.setAllowCreate(create);
            config.setTransactional(true);
            config.setLockTimeout(10_000_000L);
            config.setTxnTimeout(30_000_000L);
            environment = new Environment(home.toFile(), config);
        }

        private Database open(String name, boolean create) throws DatabaseException {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(create);
            config.setExclusiveCreate(create);
            config.setTransactional(true);
            return environment.openDatabase(null, name, config);
        }

        private void stage(String name, Map<String, String> routes) throws DatabaseException {
            Database database = open(name, true);
            try {
                for (Map.Entry<String, String> route : routes.entrySet()) {
                    assertEquals(OperationStatus.SUCCESS, database.putNoOverwrite(
                            null, entry(route.getKey()), entry(route.getValue())));
                }
            } finally {
                database.close();
            }
        }

        private void assertRoutes(String name, Map<String, String> expected) throws DatabaseException {
            Database database = open(name, false);
            try {
                assertEquals((long) expected.size(), database.count());
                for (Map.Entry<String, String> route : expected.entrySet()) {
                    DatabaseEntry destination = new DatabaseEntry();
                    assertEquals(OperationStatus.SUCCESS, database.get(
                            null, entry(route.getKey()), destination, LockMode.DEFAULT));
                    assertEquals(route.getValue(), StringBinding.entryToString(destination));
                }
            } finally {
                database.close();
            }
        }

        private Set<String> databaseNames() throws DatabaseException {
            return Set.copyOf(environment.getDatabaseNames());
        }

        private void publish(boolean confirmed) throws DatabaseException {
            // All handles are closed before renaming or removing their databases.
            Transaction transaction = environment.beginTransaction(null, null);
            try {
                environment.renameDatabase(transaction, "routes", "routes-retired");
                environment.renameDatabase(transaction, "routes-next", "routes");
                environment.removeDatabase(transaction, "routes-retired");
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

        @Override
        public void close() throws DatabaseException {
            environment.close();
        }
    }
}
