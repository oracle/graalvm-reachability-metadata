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
import com.sleepycat.je.JoinCursor;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link JoinCursor} through the public secondary-index join API.
 */
public class JoinCursorTest {

    @Test
    void joinsSecondaryCursorDuplicateSets(@TempDir Path directory) throws Exception {
        Environment environment = openEnvironment(directory);
        Database primary = null;
        SecondaryDatabase byColor = null;
        SecondaryDatabase byBodyStyle = null;
        try {
            primary = environment.openDatabase(null, "vehicles", primaryConfig());
            byColor = openSecondary(environment, primary, "byColor", 0);
            byBodyStyle = openSecondary(environment, primary, "byBodyStyle", 1);

            put(primary, "car-1", "red|sedan|alpha");
            put(primary, "car-2", "blue|sedan|bravo");
            put(primary, "car-3", "red|suv|charlie");
            put(primary, "car-4", "red|sedan|delta");

            SecondaryCursor colorCursor = byColor.openSecondaryCursor(null, null);
            SecondaryCursor bodyStyleCursor = byBodyStyle.openSecondaryCursor(null, null);
            try {
                assertThat(positionOnSecondaryKey(colorCursor, "red")).isEqualTo(OperationStatus.SUCCESS);
                assertThat(positionOnSecondaryKey(bodyStyleCursor, "sedan")).isEqualTo(OperationStatus.SUCCESS);

                JoinCursor joinCursor = primary.join(new Cursor[] { colorCursor, bodyStyleCursor }, null);
                try {
                    assertThat(joinCursor.getDatabase()).isSameAs(primary);
                    assertThat(readJoinedKeys(joinCursor)).containsExactlyInAnyOrder("car-1", "car-4");
                } finally {
                    joinCursor.close();
                }
            } finally {
                bodyStyleCursor.close();
                colorCursor.close();
            }
        } finally {
            if (byBodyStyle != null) {
                byBodyStyle.close();
            }
            if (byColor != null) {
                byColor.close();
            }
            if (primary != null) {
                primary.close();
            }
            environment.close();
        }
    }

    private static Environment openEnvironment(Path directory) throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        return new Environment(directory.toFile(), config);
    }

    private static DatabaseConfig primaryConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        return config;
    }

    private static SecondaryDatabase openSecondary(Environment environment,
                                                   Database primary,
                                                   String name,
                                                   int fieldIndex) throws DatabaseException {
        SecondaryConfig config = new SecondaryConfig();
        config.setAllowCreate(true);
        config.setSortedDuplicates(true);
        config.setKeyCreator(new PipeDelimitedFieldKeyCreator(fieldIndex));
        return environment.openSecondaryDatabase(null, name, primary, config);
    }

    private static void put(Database database, String key, String value) throws DatabaseException {
        OperationStatus status = database.put(null, entry(key), entry(value));
        assertThat(status).isEqualTo(OperationStatus.SUCCESS);
    }

    private static OperationStatus positionOnSecondaryKey(SecondaryCursor cursor, String key) throws DatabaseException {
        return cursor.getSearchKey(entry(key), new DatabaseEntry(), LockMode.DEFAULT);
    }

    private static List<String> readJoinedKeys(JoinCursor joinCursor) throws DatabaseException {
        List<String> keys = new ArrayList<>();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = joinCursor.getNext(key, data, LockMode.DEFAULT);
        while (status == OperationStatus.SUCCESS) {
            keys.add(string(key));
            status = joinCursor.getNext(key, data, LockMode.DEFAULT);
        }
        assertThat(status).isEqualTo(OperationStatus.NOTFOUND);
        return keys;
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String string(DatabaseEntry entry) {
        return new String(entry.getData(), entry.getOffset(), entry.getSize(), StandardCharsets.UTF_8);
    }

    private static final class PipeDelimitedFieldKeyCreator implements SecondaryKeyCreator {
        private final int fieldIndex;

        private PipeDelimitedFieldKeyCreator(int fieldIndex) {
            this.fieldIndex = fieldIndex;
        }

        @Override
        public boolean createSecondaryKey(SecondaryDatabase secondary,
                                          DatabaseEntry primaryKey,
                                          DatabaseEntry data,
                                          DatabaseEntry result) {
            String[] fields = string(data).split("\\|");
            if (fieldIndex >= fields.length || fields[fieldIndex].isEmpty()) {
                return false;
            }
            result.setData(fields[fieldIndex].getBytes(StandardCharsets.UTF_8));
            return true;
        }
    }
}
