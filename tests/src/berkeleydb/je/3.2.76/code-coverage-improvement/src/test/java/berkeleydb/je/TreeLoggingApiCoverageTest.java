/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINDelta;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.INDeleteInfo;
import com.sleepycat.je.tree.INDupDeleteInfo;
import com.sleepycat.je.txn.Locker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TreeLoggingApiCoverageTest {

    @Test
    void deleteInformationIsLoggedWithItsDatabaseContext(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "records", databaseConfig);
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                    .envGetEnvironmentImpl(environment);
            DatabaseId databaseId = com.sleepycat.je.DbInternal
                    .dbGetDatabaseImpl(database).getId();
            INDeleteInfo deleteInfo = new INDeleteInfo(17L, new byte[] {1, 2}, databaseId);
            assertThat(deleteInfo.getDeletedNodeId()).isEqualTo(17L);
            assertThat(deleteInfo.getDeletedIdKey()).containsExactly(1, 2);
            assertThat(deleteInfo.getDatabaseId()).isEqualTo(databaseId);
            long before = implementation.getFileManager().getLastUsedLsn();
            LogManager logManager = implementation.getLogManager();
            deleteInfo.optionalLog(logManager,
                    com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database));
            assertThat(implementation.getFileManager().getLastUsedLsn()).isGreaterThan(before);

            INDupDeleteInfo duplicateInfo = new INDupDeleteInfo(18L,
                    new byte[] {3}, new byte[] {4}, databaseId);
            assertThat(duplicateInfo.getDeletedNodeId()).isEqualTo(18L);
            assertThat(duplicateInfo.getDeletedMainKey()).containsExactly(3);
            assertThat(duplicateInfo.getDeletedDupKey()).containsExactly(4);
            before = implementation.getFileManager().getLastUsedLsn();
            duplicateInfo.optionalLog(logManager,
                    com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database));
            assertThat(implementation.getFileManager().getLastUsedLsn()).isGreaterThan(before);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void treeObjectsExposeSafeUpdateAndReconstitutionFailures() {
        IN parent = new IN();
        BIN child = new BIN();
        assertThatThrownBy(() -> child.getChildKey(parent)).isInstanceOf(Exception.class);
        BINDelta delta = new BINDelta();
        assertThat(delta.getLastFullLsn()).isEqualTo(-1L);
        assertThatThrownBy(() -> delta.reconstituteBIN(null)).isInstanceOf(Exception.class);
    }

    @Test
    void aTransactionLockerCanDemoteAnUnheldLock(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setTransactional(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Transaction transaction = null;
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            databaseConfig.setTransactional(true);
            database = environment.openDatabase(null, "locks", databaseConfig);
            transaction = environment.beginTransaction(null, null);
            database.put(transaction, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            Locker locker = com.sleepycat.je.DbInternal.getLocker(transaction);
            long databaseLockId = com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database)
                    .getId().getId();
            assertThatThrownBy(() -> locker.demoteLock(databaseLockId))
                    .isInstanceOf(NullPointerException.class);
            assertThat(locker.getId()).isPositive();
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
}
