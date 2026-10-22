/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JE sorted-duplicate databases and primary cursors to maintain mailing-list subscriptions.
@Timeout(60)
public class MailingListDuplicatesTest {
    @TempDir
    Path home;

    @Test
    public void deduplicatesSignupsRemovesOneRecipientAndReopensLists() throws DatabaseException {
        try (Subscriptions subscriptions = new Subscriptions(home, true)) {
            // One database key has several recipient values, ordered independently of signup order.
            assertTrue(subscriptions.subscribe("weekly", "zoe@example.test"));
            assertTrue(subscriptions.subscribe("weekly", "ada@example.test"));
            assertTrue(subscriptions.subscribe("weekly", "ben@example.test"));
            assertTrue(subscriptions.subscribe("alerts", "ben@example.test"));
            assertFalse(subscriptions.subscribe("weekly", "ada@example.test"));
            assertEquals(List.of("ada@example.test", "ben@example.test", "zoe@example.test"),
                    subscriptions.recipients("weekly"));
            assertEquals(List.of("ben@example.test"), subscriptions.recipients("alerts"));
            assertEquals(List.of(), subscriptions.recipients("unannounced"));

            // Remove one key/value pair, not the whole list or this recipient's other subscriptions.
            assertTrue(subscriptions.unsubscribe("weekly", "ben@example.test"));
            assertFalse(subscriptions.unsubscribe("weekly", "ben@example.test"));
            assertEquals(List.of("ada@example.test", "zoe@example.test"), subscriptions.recipients("weekly"));
            assertEquals(List.of("ben@example.test"), subscriptions.recipients("alerts"));
            assertTrue(subscriptions.unsubscribe("alerts", "ben@example.test"));
            assertEquals(List.of(), subscriptions.recipients("alerts"));
        }

        try (Subscriptions subscriptions = new Subscriptions(home, false)) {
            assertEquals(List.of("ada@example.test", "zoe@example.test"), subscriptions.recipients("weekly"));
            assertEquals(List.of(), subscriptions.recipients("alerts"));
            assertFalse(subscriptions.subscribe("weekly", "ada@example.test"));
            assertTrue(subscriptions.subscribe("weekly", "ben@example.test"));
            assertEquals(List.of("ada@example.test", "ben@example.test", "zoe@example.test"),
                    subscriptions.recipients("weekly"));
        }
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry result = new DatabaseEntry();
        StringBinding.stringToEntry(value, result);
        return result;
    }

    private static final class Subscriptions implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private Subscriptions(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            databaseConfig.setSortedDuplicates(true);
            try {
                database = environment.openDatabase(null, "mailing-list-recipients", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private boolean subscribe(String list, String email) throws DatabaseException {
            OperationStatus status = database.putNoDupData(null, entry(list), entry(email));
            if (status == OperationStatus.KEYEXIST) {
                return false;
            }
            assertEquals(OperationStatus.SUCCESS, status);
            return true;
        }

        private boolean unsubscribe(String list, String email) throws DatabaseException {
            Cursor cursor = database.openCursor(null, null);
            try {
                OperationStatus status = cursor.getSearchBoth(entry(list), entry(email), LockMode.DEFAULT);
                if (status == OperationStatus.NOTFOUND) {
                    return false;
                }
                assertEquals(OperationStatus.SUCCESS, status);
                assertEquals(OperationStatus.SUCCESS, cursor.delete());
                return true;
            } finally {
                cursor.close();
            }
        }

        private List<String> recipients(String list) throws DatabaseException {
            List<String> recipients = new ArrayList<>();
            Cursor cursor = database.openCursor(null, null);
            try {
                DatabaseEntry key = entry(list);
                DatabaseEntry value = new DatabaseEntry();
                OperationStatus status = cursor.getSearchKey(key, value, LockMode.DEFAULT);
                while (status == OperationStatus.SUCCESS) {
                    assertEquals(list, StringBinding.entryToString(key));
                    recipients.add(StringBinding.entryToString(value));
                    status = cursor.getNextDup(key, value, LockMode.DEFAULT);
                }
                assertEquals(OperationStatus.NOTFOUND, status);
            } finally {
                cursor.close();
            }
            return recipients;
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
