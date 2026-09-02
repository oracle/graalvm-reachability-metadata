/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.compat.DbCompat;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JoinConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.LogScanConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.util.keyrange.KeyRange;
import com.sleepycat.util.keyrange.RangeCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DatabaseApiCoverageTest {

    @Test
    void secondaryDatabaseAndCursorExposeReadOnlySecondarySemantics(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home, true);
        Database primary = null;
        SecondaryDatabase secondary = null;
        try {
            primary = openDatabase(environment, "primary", false, true);
            SecondaryConfig secondaryConfig = new SecondaryConfig();
            secondaryConfig.setAllowCreate(true);
            secondaryConfig.setTransactional(true);
            secondaryConfig.setKeyCreator(new ValueKeyCreator());
            secondary = environment.openSecondaryDatabase(null, "by-value", primary,
                    secondaryConfig);
            assertThat(primary.put(null, entry("id"), entry("value")))
                    .isEqualTo(OperationStatus.SUCCESS);
            final SecondaryDatabase secondaryDatabase = secondary;

            DatabaseEntry secondaryKey = entry("value");
            DatabaseEntry primaryKey = entry("id");
            assertThatThrownBy(() -> secondaryDatabase.getSearchBoth(null, secondaryKey, primaryKey,
                    LockMode.DEFAULT)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> secondaryDatabase.put(null, secondaryKey, primaryKey))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> secondaryDatabase.putNoOverwrite(null, secondaryKey, primaryKey))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> secondaryDatabase.putNoDupData(null, secondaryKey, primaryKey))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> secondaryDatabase.join(new Cursor[0], new JoinConfig()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> secondaryDatabase.truncate(null, false))
                    .isInstanceOf(UnsupportedOperationException.class);

            SecondaryCursor cursor = secondary.openSecondaryCursor(null, null);
            try {
                assertThatThrownBy(() -> cursor.getSearchBoth(secondaryKey, primaryKey,
                        LockMode.DEFAULT)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> cursor.getSearchBothRange(secondaryKey, primaryKey,
                        LockMode.DEFAULT)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> cursor.put(secondaryKey, primaryKey))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> cursor.putCurrent(primaryKey))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> cursor.putNoDupData(secondaryKey, primaryKey))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> cursor.putNoOverwrite(secondaryKey, primaryKey))
                        .isInstanceOf(UnsupportedOperationException.class);
            } finally {
                cursor.close();
            }
        } finally {
            if (secondary != null) {
                secondary.close();
            }
            if (primary != null) {
                primary.close();
            }
            environment.close();
        }
    }

    @Test
    void databaseStatisticsWalkTheTreeAfterRecordsAreWritten(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home, false);
        Database database = null;
        try {
            database = openDatabase(environment, "statistics", false, false);
            database.put(null, entry("one"), entry("value"));
            database.put(null, entry("two"), entry("value"));
            assertThat(database.getStats(new com.sleepycat.je.StatsConfig())).isNotNull();
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void rangeCursorMaintainsBoundsWhenInsertingDuplicateValues(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home, false);
        Database database = null;
        RangeCursor range = null;
        try {
            database = openDatabase(environment, "duplicates", true, false);
            database.put(null, entry("key"), entry("one"));
            Cursor cursor = database.openCursor(null, CursorConfig.DEFAULT);
            try {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                assertThat(cursor.getFirst(key, data, LockMode.DEFAULT))
                        .isEqualTo(OperationStatus.SUCCESS);
                range = new RangeCursor(new KeyRange(null), cursor);
                final RangeCursor rangeCursor = range;
                DatabaseEntry recordNumber = new DatabaseEntry(new byte[] {0, 0, 0, 1});
                assertThatThrownBy(() -> rangeCursor.getSearchRecordNumber(recordNumber,
                        new DatabaseEntry(), new DatabaseEntry(), LockMode.DEFAULT))
                        .isInstanceOf(UnsupportedOperationException.class);
                DatabaseEntry insertedKey = entry("key");
                DatabaseEntry insertedData = entry("two");
                assertThatThrownBy(() -> rangeCursor.putAfter(insertedKey, insertedData))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> rangeCursor.putBefore(entry("key"), entry("zero")))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThat(rangeCursor.putNoDupData(entry("key"), entry("two")))
                        .isEqualTo(OperationStatus.SUCCESS);
            } finally {
                if (range != null) {
                    range.close();
                    range = null;
                } else {
                    cursor.close();
                }
            }
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void compatibilityHelpersSyncDeferredWritesAndCloneScanConfiguration(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home, false);
        Database database = null;
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            config.setDeferredWrite(true);
            database = environment.openDatabase(null, "deferred", config);
            assertThat(database.put(null, entry("key"), entry("data")))
                    .isEqualTo(OperationStatus.SUCCESS);
            DbCompat.syncDeferredWrite(database, true);
            assertThat(database.get(null, entry("key"), new DatabaseEntry(), LockMode.DEFAULT))
                    .isEqualTo(OperationStatus.SUCCESS);

            LogScanConfig scanConfig = new LogScanConfig();
            scanConfig.setForwards(false);
            assertThatThrownBy(scanConfig::cloneConfig)
                    .isInstanceOf(ClassCastException.class);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void environmentImplementationTruncatesAnOpenedDatabase(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home, true);
        Database database = null;
        Transaction transaction = null;
        try {
            database = openDatabase(environment, "truncate-me", false, true);
            database.put(null, entry("one"), entry("value"));
            database.put(null, entry("two"), entry("value"));
            database.close();
            database = null;
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                    .envGetEnvironmentImpl(environment);
            transaction = environment.beginTransaction(null, null);
            long removed = implementation.truncate(
                    com.sleepycat.je.DbInternal.getLocker(transaction), "truncate-me", false);
            assertThat(removed).isZero();
            transaction.commit();
            transaction = null;
            DatabaseConfig reopenedConfig = new DatabaseConfig();
            reopenedConfig.setTransactional(true);
            database = environment.openDatabase(null, "truncate-me", reopenedConfig);
            assertThat(database.count()).isZero();
        } finally {
            if (transaction != null) {
                transaction.abort();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static Environment openEnvironment(Path home, boolean transactional) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(transactional);
        return new Environment(home.toFile(), config);
    }

    private static Database openDatabase(Environment environment, String name,
            boolean duplicates, boolean transactional) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(transactional);
        config.setSortedDuplicates(duplicates);
        return environment.openDatabase(null, name, config);
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class ValueKeyCreator implements SecondaryKeyCreator {
        @Override
        public boolean createSecondaryKey(SecondaryDatabase secondary, DatabaseEntry key,
                DatabaseEntry data, DatabaseEntry result) {
            result.setData(data.getData(), data.getOffset(), data.getSize());
            return true;
        }
    }
}
