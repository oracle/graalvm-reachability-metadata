/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

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
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code DatabaseImpl} comparator serialization and log reload paths.
 */
public class DatabaseImplTest {

    @Test
    void reloadsClassNameComparatorsFromDatabaseLog(@TempDir Path directory) throws Exception {
        Environment environment = openEnvironment(directory);
        Database database = null;
        try {
            database = environment.openDatabase(null, "comparators", newComparatorDatabaseConfig(true));
            database.put(null, entry("alpha"), entry("first"));
            database.put(null, entry("omega"), entry("last"));
            database.put(null, entry("same"), entry("apple"));
            database.put(null, entry("same"), entry("pear"));

            assertFirstKeyAndDuplicateOrder(database);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }

        environment = openEnvironment(directory);
        database = null;
        try {
            database = environment.openDatabase(null, "comparators", existingComparatorDatabaseConfig());

            DatabaseConfig config = database.getConfig();
            assertThat(config.getBtreeComparator()).isInstanceOf(ReverseBtreeComparator.class);
            assertThat(config.getDuplicateComparator()).isInstanceOf(ReverseDuplicateComparator.class);
            assertThat(config.getBtreeComparatorByClassName()).isTrue();
            assertThat(config.getDuplicateComparatorByClassName()).isTrue();
            assertFirstKeyAndDuplicateOrder(database);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static Environment openEnvironment(Path directory) throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        return new Environment(directory.toFile(), config);
    }

    private static DatabaseConfig newComparatorDatabaseConfig(boolean allowCreate) {
        DatabaseConfig config = existingComparatorDatabaseConfig();
        config.setAllowCreate(allowCreate);
        config.setBtreeComparator(ReverseBtreeComparator.class);
        config.setDuplicateComparator(ReverseDuplicateComparator.class);
        return config;
    }

    private static DatabaseConfig existingComparatorDatabaseConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setSortedDuplicates(true);
        return config;
    }

    private static void assertFirstKeyAndDuplicateOrder(Database database) throws DatabaseException {
        Cursor cursor = database.openCursor(null, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();

            OperationStatus status = cursor.getFirst(key, value, LockMode.DEFAULT);
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            assertThat(string(key)).isEqualTo("same");

            status = cursor.getSearchKey(entry("same"), value, LockMode.DEFAULT);
            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            assertThat(string(value)).isEqualTo("pear");
        } finally {
            cursor.close();
        }
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String string(DatabaseEntry entry) {
        return new String(entry.getData(), entry.getOffset(), entry.getSize(), StandardCharsets.UTF_8);
    }

    /**
     * Public zero-argument comparator class stored by class name in the database log.
     */
    public static final class ReverseBtreeComparator implements Comparator<byte[]>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(byte[] left, byte[] right) {
            return -compareUnsignedBytes(left, right);
        }
    }

    /**
     * Public zero-argument comparator class stored by class name in the database log.
     */
    public static final class ReverseDuplicateComparator implements Comparator<byte[]>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(byte[] left, byte[] right) {
            return -compareUnsignedBytes(left, right);
        }
    }

    private static int compareUnsignedBytes(byte[] left, byte[] right) {
        int limit = Math.min(left.length, right.length);
        for (int i = 0; i < limit; i++) {
            int leftByte = left[i] & 0xff;
            int rightByte = right[i] & 0xff;
            if (leftByte != rightByte) {
                return leftByte - rightByte;
            }
        }
        return left.length - right.length;
    }
}
