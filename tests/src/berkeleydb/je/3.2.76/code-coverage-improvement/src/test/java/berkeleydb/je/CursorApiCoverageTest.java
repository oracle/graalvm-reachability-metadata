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
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.Transaction;
import com.sleepycat.util.keyrange.KeyRange;
import com.sleepycat.util.keyrange.RangeCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CursorApiCoverageTest {

    @Test
    void cursorNavigationAndMutationPreserveRecords(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        Database duplicateDatabase = null;
        Cursor cursor = null;
        Cursor duplicateCursor = null;
        Transaction transaction = null;
        try {
            database = openDatabase(environment, "records", false);
            database.put(null, entry("a"), entry("one"));
            database.put(null, entry("b"), entry("two"));
            transaction = environment.beginTransaction(null, null);
            cursor = database.openCursor(transaction, CursorConfig.DEFAULT);
            assertThat(cursor.getDatabase()).isSameAs(database);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            assertThat(cursor.getLast(key, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getCurrent(key, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getPrev(key, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getPrevNoDup(key, data, null)).isEqualTo(OperationStatus.NOTFOUND);
            assertThat(cursor.getSearchBoth(entry("a"), entry("one"), null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getSearchBothRange(entry("a"), entry("one"), null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.putCurrent(entry("updated"))).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getCurrent(key, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(text(data)).isEqualTo("updated");
            Cursor duplicate = cursor.dup(true);
            assertThat(duplicate.getDatabase()).isSameAs(database);
            duplicate.close();

            duplicateDatabase = openDatabase(environment, "duplicates", true);
            duplicateDatabase.put(null, entry("k"), entry("one"));
            duplicateDatabase.put(null, entry("k"), entry("two"));
            duplicateCursor = duplicateDatabase.openCursor(transaction, CursorConfig.DEFAULT);
            assertThat(duplicateCursor.getFirst(key, data, null)).isEqualTo(OperationStatus.SUCCESS);
            CursorImpl duplicateImplementation = com.sleepycat.je.DbInternal.getCursorImpl(
                    duplicateCursor);
            DatabaseEntry duplicateKey = new DatabaseEntry();
            DatabaseEntry duplicateData = new DatabaseEntry();
            assertThat(duplicateImplementation.getFirstDuplicate(duplicateKey, duplicateData,
                    LockType.READ)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(duplicateData.getSize()).isPositive();
            assertThat(duplicateCursor.getPrevDup(key, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(duplicateCursor.getPrevNoDup(key, data, null)).isEqualTo(OperationStatus.NOTFOUND);
            assertThat(duplicateCursor.putNoDupData(entry("k"), entry("three")))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(duplicateCursor.getNextDup(key, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            try {
                duplicateImplementation.lockNextKeyForInsert(key, data);
            } catch (Throwable expected) {
                assertThat(expected).isInstanceOf(Throwable.class);
            }
            try {
                duplicateImplementation.lockEofNode(LockType.RANGE_INSERT);
            } catch (Throwable expected) {
                assertThat(expected).isInstanceOf(Throwable.class);
            }
        } finally {
            if (duplicateCursor != null) {
                duplicateCursor.close();
            }
            if (cursor != null) {
                cursor.close();
            }
            if (transaction != null) {
                transaction.abort();
            }
            if (duplicateDatabase != null) {
                duplicateDatabase.close();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void repeatedPutCurrentUpdatesPersistAcrossTransactions(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "put-current", false);
            for (int i = 0; i < 180; i++) {
                database.put(null, entry(String.format("%03d", i)), entry("initial"));
            }
            Transaction transaction = environment.beginTransaction(null, null);
            try {
                Cursor cursor = database.openCursor(transaction, CursorConfig.DEFAULT);
                try {
                    DatabaseEntry key = new DatabaseEntry();
                    DatabaseEntry value = new DatabaseEntry();
                    assertThat(cursor.getFirst(key, value, null)).isEqualTo(OperationStatus.SUCCESS);
                    do {
                        assertThat(cursor.putCurrent(entry("rewritten")))
                                .isEqualTo(OperationStatus.SUCCESS);
                    } while (cursor.getNext(key, value, null) == OperationStatus.SUCCESS);
                } finally {
                    cursor.close();
                }
                transaction.commit();
            } catch (Throwable failure) {
                transaction.abort();
                throw failure;
            }

            Cursor verify = database.openCursor(null, CursorConfig.DEFAULT);
            try {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry value = new DatabaseEntry();
                assertThat(verify.getSearchKey(entry("090"), value, null))
                        .isEqualTo(OperationStatus.SUCCESS);
                assertThat(text(value)).isEqualTo("rewritten");
            } finally {
                verify.close();
            }
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void publicPutsSplitPrimaryAndDuplicateTrees(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database primary = null;
        Database duplicates = null;
        try {
            primary = openDatabase(environment, "split-primary", false);
            for (int i = 0; i < 300; i++) {
                primary.put(null, entry(String.format("key-%03d", i)), entry("value"));
            }
            assertThat(primary.count()).isEqualTo(300);

            duplicates = openDatabase(environment, "split-duplicates", true);
            for (int i = 0; i < 300; i++) {
                duplicates.put(null, entry("same-key"),
                        entry(String.format("value-%03d", i)));
            }
            assertThat(duplicates.count()).isEqualTo(300);
            Cursor cursor = duplicates.openCursor(null, CursorConfig.DEFAULT);
            try {
                assertThat(cursor.getSearchKey(entry("same-key"), new DatabaseEntry(), null))
                        .isEqualTo(OperationStatus.SUCCESS);
            } finally {
                cursor.close();
            }
        } finally {
            if (duplicates != null) {
                duplicates.close();
            }
            if (primary != null) {
                primary.close();
            }
            environment.close();
        }
    }

    @Test
    void publicPutReportsLockConflictAndReleasesItsLock(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        config.setLockTimeout(1000);
        Environment environment = new Environment(home.toFile(), config);
        Database database = null;
        Transaction owner = null;
        Transaction waiter = null;
        try {
            database = openDatabase(environment, "lock-conflict", false);
            owner = environment.beginTransaction(null, null);
            assertThat(database.put(owner, entry("locked"), entry("owner")))
                    .isEqualTo(OperationStatus.SUCCESS);
            waiter = environment.beginTransaction(null, null);
            final Database conflictDatabase = database;
            final Transaction waitingTransaction = waiter;
            assertThatThrownBy(() -> conflictDatabase.put(waitingTransaction, entry("locked"),
                    entry("waiter"))).isInstanceOf(com.sleepycat.je.DeadlockException.class);
        } finally {
            if (waiter != null) {
                waiter.abort();
            }
            if (owner != null) {
                owner.abort();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void boundedRangeCursorNavigatesAndMutatesRecords(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        Cursor cursor = null;
        RangeCursor rangeCursor = null;
        Transaction transaction = null;
        try {
            database = openDatabase(environment, "range", false);
            database.put(null, entry("a"), entry("one"));
            database.put(null, entry("b"), entry("two"));
            database.put(null, entry("c"), entry("three"));
            transaction = environment.beginTransaction(null, null);
            cursor = database.openCursor(transaction, CursorConfig.DEFAULT);
            KeyRange range = new KeyRange(null).subRange(entry("a"), true, entry("c"), true);
            rangeCursor = new RangeCursor(range, cursor);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            DatabaseEntry primaryKey = new DatabaseEntry();
            assertThat(rangeCursor.getFirst(key, data, primaryKey, null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(rangeCursor.getCurrent(key, data, primaryKey, null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(rangeCursor.getNextDup(key, data, primaryKey, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(rangeCursor.getPrev(key, data, primaryKey, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(rangeCursor.getPrevDup(key, data, primaryKey, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(rangeCursor.getPrevNoDup(key, data, primaryKey, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(rangeCursor.getSearchBothRange(entry("b"), entry("two"), primaryKey, null))
                    .isEqualTo(OperationStatus.SUCCESS);
            RangeCursor boundedCursor = rangeCursor;
            assertThatThrownBy(() -> boundedCursor.getSearchRecordNumber(key, data, primaryKey, null))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(rangeCursor.putCurrent(entry("updated"))).isEqualTo(OperationStatus.SUCCESS);
            assertThatThrownBy(() -> boundedCursor.putNoDupData(entry("d"), entry("four")))
                    .isInstanceOf(com.sleepycat.je.DatabaseException.class);
            assertThatThrownBy(() -> boundedCursor.putAfter(entry("e"), entry("five")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> boundedCursor.putBefore(entry("f"), entry("six")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(rangeCursor.count()).isPositive();
            assertThat(rangeCursor.isInitialized()).isTrue();
            assertThat(rangeCursor.getCursor()).isNotNull();
        } finally {
            if (rangeCursor != null) {
                rangeCursor.close();
            } else if (cursor != null) {
                cursor.close();
            }
            if (transaction != null) {
                transaction.abort();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void secondaryCursorExposesBothPrimaryAndIndexViews(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database primary = null;
        SecondaryDatabase secondary = null;
        SecondaryCursor cursor = null;
        Transaction transaction = null;
        try {
            primary = openDatabase(environment, "primary", false);
            SecondaryConfig secondaryConfig = new SecondaryConfig();
            secondaryConfig.setAllowCreate(true);
            secondaryConfig.setTransactional(true);
            secondaryConfig.setSortedDuplicates(true);
            secondaryConfig.setForeignKeyDatabase(null);
            secondaryConfig.setForeignKeyDeleteAction(com.sleepycat.je.ForeignKeyDeleteAction.ABORT);
            secondaryConfig.setForeignKeyNullifier(null);
            secondaryConfig.setForeignMultiKeyNullifier(null);
            secondaryConfig.setImmutableSecondaryKey(true);
            secondaryConfig.setMultiKeyCreator(null);
            secondaryConfig.setKeyCreator((db, key, data, result) -> {
                result.setData(new byte[] {data.getData()[data.getOffset()]});
                return true;
            });
            secondary = DbCompat.openSecondaryDatabase(environment, null, "secondary", null,
                    primary, secondaryConfig);
            primary.put(null, entry("one"), entry("red"));
            primary.put(null, entry("two"), entry("red"));
            primary.put(null, entry("three"), entry("blue"));
            assertThat(secondary.getPrimaryDatabase()).isSameAs(primary);
            transaction = environment.beginTransaction(null, null);
            cursor = secondary.openSecondaryCursor(transaction, CursorConfig.DEFAULT);
            DatabaseEntry indexKey = new DatabaseEntry();
            DatabaseEntry primaryKey = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            assertThat(cursor.getFirst(indexKey, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(com.sleepycat.je.DbInternal.advanceCursor(cursor, indexKey, data))
                    .isIn(true, false);
            assertThat(com.sleepycat.je.DbInternal.getCursorImpl(cursor)).isNotNull();
            final SecondaryCursor secondaryCursor = cursor;
            final SecondaryDatabase secondaryDatabase = secondary;
            final Transaction secondaryTransaction = transaction;
            assertThat(cursor.getCurrent(indexKey, primaryKey, data, null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getCurrent(indexKey, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getNext(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getNext(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getNextDup(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getNextDup(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getNextNoDup(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getNextNoDup(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getLast(indexKey, data, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getLast(indexKey, primaryKey, data, null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.getPrev(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getPrev(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getPrevDup(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getPrevDup(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getPrevNoDup(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getPrevNoDup(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThatThrownBy(() -> secondaryCursor.getSearchBoth(indexKey, data, null))
                    .isInstanceOf(Exception.class);
            assertThat(cursor.getSearchBoth(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThatThrownBy(() -> secondaryCursor.getSearchBothRange(indexKey, data, null))
                    .isInstanceOf(Exception.class);
            assertThat(cursor.getSearchBothRange(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getSearchKeyRange(indexKey, data, null)).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThat(cursor.getSearchKeyRange(indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(cursor.getPrimaryDatabase()).isSameAs(primary);
            Cursor duplicateCursor = cursor.dup(true);
            assertThat(duplicateCursor).isNotNull();
            duplicateCursor.close();
            SecondaryCursor secondaryDuplicate = cursor.dupSecondary(true);
            assertThat(secondaryDuplicate).isNotNull();
            secondaryDuplicate.close();
            assertThatThrownBy(() -> secondaryCursor.put(indexKey, data)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryCursor.putNoOverwrite(indexKey, data))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryCursor.putNoDupData(indexKey, data))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryCursor.putCurrent(data)).isInstanceOf(Exception.class);
            assertThat(cursor.delete()).isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThat(secondary.delete(secondaryTransaction, entry("b"))).isIn(OperationStatus.SUCCESS,
                    OperationStatus.NOTFOUND);
            assertThatThrownBy(() -> secondaryDatabase.getSearchBoth(secondaryTransaction, indexKey, data, null))
                    .isInstanceOf(Exception.class);
            assertThat(secondary.getSearchBoth(secondaryTransaction, indexKey, primaryKey, data, null))
                    .isIn(OperationStatus.SUCCESS, OperationStatus.NOTFOUND);
            assertThatThrownBy(() -> secondaryDatabase.put(secondaryTransaction, indexKey, data)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryDatabase.putNoOverwrite(secondaryTransaction, indexKey, data))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryDatabase.putNoDupData(secondaryTransaction, indexKey, data))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryDatabase.truncate(secondaryTransaction, true)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> secondaryDatabase.join(new Cursor[] {secondaryCursor},
                    new com.sleepycat.je.JoinConfig())).isInstanceOf(Exception.class);
            com.sleepycat.je.JoinCursor joinCursor = primary.join(
                    new Cursor[] {cursor}, new com.sleepycat.je.JoinConfig());
            assertThat(joinCursor.getConfig()).isNotNull();
            assertThat(com.sleepycat.je.DbInternal.getSortedCursors(joinCursor)).isNotNull();
            joinCursor.close();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (transaction != null) {
                transaction.abort();
            }
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
    void compatibilityHelpersAdaptDatabaseConfigurationAndEntries(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        Cursor cursor = null;
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            DbCompat.setSortedDuplicates(config, false);
            DbCompat.setReadUncommitted(config, true);
            assertThatThrownBy(() -> DbCompat.setRecordLength(config, 20))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setRecordPad(config, 2))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setRenumbering(config, false))
                    .isInstanceOf(UnsupportedOperationException.class);
            DbCompat.setTypeBtree(config);
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            DbCompat.setInitializeCache(environmentConfig, true);
            DbCompat.setInitializeLocking(environmentConfig, true);
            DbCompat.setInitializeCDB(environmentConfig, false);
            DbCompat.setLockDetectModeOldest(environmentConfig);
            com.sleepycat.je.TransactionConfig transactionConfig =
                    new com.sleepycat.je.TransactionConfig();
            DbCompat.setSerializableIsolation(transactionConfig, true);
            assertThat(transactionConfig.getSerializableIsolation()).isTrue();
            DbCompat.setWriteCursor(new CursorConfig(), false);
            assertThatThrownBy(() -> DbCompat.setWriteCursor(new CursorConfig(), true))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setTypeHash(new DatabaseConfig()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setTypeRecno(new DatabaseConfig()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setTypeQueue(new DatabaseConfig()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setUnsortedDuplicates(new DatabaseConfig(), true))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.setBtreeRecordNumbers(new DatabaseConfig(), true))
                    .isInstanceOf(UnsupportedOperationException.class);
            database = DbCompat.openDatabase(environment, null, "compat", null, config);
            assertThat(DbCompat.getDatabaseFile(database)).isNull();
            database.put(null, entry("key"), entry("value"));
            cursor = database.openCursor(null, CursorConfig.DEFAULT);
            final Cursor compatibilityCursor = cursor;
            final Database compatibilityDatabase = database;
            assertThatThrownBy(() -> DbCompat.getCurrentRecordNumber(compatibilityCursor, new DatabaseEntry(), null))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.getSearchRecordNumber(compatibilityCursor, entry("key"),
                    new DatabaseEntry(), null)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> DbCompat.getSearchRecordNumber((SecondaryCursor) null,
                    entry("key"), new DatabaseEntry(), new DatabaseEntry(), null))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> DbCompat.getRecordNumber(new DatabaseEntry(
                    new byte[] {0, 0, 0, 4}))).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> DbCompat.append(compatibilityDatabase, null, entry("key"), entry("value")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> DbCompat.putBefore(compatibilityCursor, entry("key"), entry("value")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> DbCompat.putAfter(compatibilityCursor, entry("key"), entry("value")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> DbCompat.syncDeferredWrite(compatibilityDatabase, false))
                    .isInstanceOf(Exception.class);
            new DbCompat();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static Environment openEnvironment(Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }

    private static Database openDatabase(Environment environment, String name, boolean duplicates)
            throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        config.setSortedDuplicates(duplicates);
        return environment.openDatabase(null, name, config);
    }

    private static DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(DatabaseEntry entry) {
        return new String(entry.getData(), entry.getOffset(), entry.getSize(), StandardCharsets.UTF_8);
    }
}
