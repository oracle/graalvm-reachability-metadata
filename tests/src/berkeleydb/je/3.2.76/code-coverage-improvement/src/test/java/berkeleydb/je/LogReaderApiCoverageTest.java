/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.INFileReader;
import com.sleepycat.je.log.LNFileReader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.dbi.EnvironmentImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class LogReaderApiCoverageTest {

    @Test
    void lnReaderDecodesLoggedRecordsAndTransactionStatus(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setTransactional(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            databaseConfig.setTransactional(true);
            database = environment.openDatabase(null, "records", databaseConfig);
            Transaction transaction = environment.beginTransaction(null, null);
            assertThat(database.put(transaction, entry(1), entry(2)))
                    .isEqualTo(OperationStatus.SUCCESS);
            transaction.commit();

            EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                    .envGetEnvironmentImpl(environment);
            FileManager fileManager = implementation.getFileManager();
            long lastLsn = fileManager.getLastUsedLsn();
            LNFileReader records = new LNFileReader(implementation, 100000, 0L, true,
                    lastLsn, lastLsn, null);
            records.addTargetType(LogEntryType.LOG_LN_TRANSACTIONAL);
            boolean foundRecord = false;
            while (records.readNextEntry()) {
                if (records.isLN()) {
                    assertThat(records.getLN()).isNotNull();
                    assertThat(records.getDatabaseId()).isNotNull();
                    assertThat(records.getKey()).containsExactly(1);
                    assertThat(records.getDupTreeKey()).containsExactly(2);
                    assertThat(records.getTxnId()).isNotNull();
                    assertThat(records.isPrepare()).isFalse();
                    assertThat(records.isAbort()).isFalse();
                    foundRecord = true;
                    break;
                }
            }
            assertThat(foundRecord).isTrue();

            LNFileReader commits = new LNFileReader(implementation, 100000, 0L, true,
                    lastLsn, lastLsn, null);
            commits.addTargetType(LogEntryType.LOG_TXN_COMMIT);
            boolean foundCommit = false;
            while (commits.readNextEntry()) {
                assertThat(commits.isAbort()).isFalse();
                if (!commits.isPrepare()) {
                    try {
                        assertThat(commits.getTxnCommitId()).isPositive();
                        foundCommit = true;
                        break;
                    } catch (ClassCastException ignored) {
                        // A target entry can be shared by the reader before a commit.
                    }
                }
            }
            assertThat(foundCommit).isTrue();
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void inReaderScansTreeEntriesAndExposesTrackingState(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "records", databaseConfig);
            database.put(null, entry(1), entry(2));
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                    .envGetEnvironmentImpl(environment);
            long lastLsn = implementation.getFileManager().getLastUsedLsn();
            INFileReader reader = new INFileReader(implementation, 100000, 0L, lastLsn,
                    true, false, -1L, new java.util.HashMap<>());
            reader.addTargetType(LogEntryType.LOG_IN);
            reader.addTargetType(LogEntryType.LOG_BIN);
            boolean foundTree = false;
            while (reader.readNextEntry()) {
                if (reader.getIN() != null) {
                    assertThat(reader.getDatabaseId()).isNotNull();
                    assertThat(reader.getMaxNodeId()).isGreaterThanOrEqualTo(0L);
                    assertThat(reader.getMaxDbId()).isGreaterThanOrEqualTo(0);
                    assertThat(reader.getMaxTxnId()).isGreaterThanOrEqualTo(0L);
                    assertThat(reader.getLsnOfIN()).isGreaterThanOrEqualTo(0L);
                    foundTree = true;
                    break;
                }
            }
            assertThat(foundTree).isTrue();
            assertThat(reader.isDeleteInfo()).isFalse();
            assertThat(reader.isDupDeleteInfo()).isFalse();

            com.sleepycat.je.dbi.DatabaseId databaseId = com.sleepycat.je.DbInternal
                    .dbGetDatabaseImpl(database).getId();
            com.sleepycat.je.tree.INDeleteInfo deleteInfo =
                    new com.sleepycat.je.tree.INDeleteInfo(9L, new byte[] {3}, databaseId);
            deleteInfo.optionalLog(implementation.getLogManager(),
                    com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database));
            com.sleepycat.je.tree.INDupDeleteInfo duplicateInfo =
                    new com.sleepycat.je.tree.INDupDeleteInfo(10L, new byte[] {4},
                            new byte[] {5}, databaseId);
            duplicateInfo.optionalLog(implementation.getLogManager(),
                    com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database));
            implementation.getLogManager().flush();
            long infoEndLsn = implementation.getFileManager().getLastUsedLsn();
            INFileReader deleteReader = new INFileReader(implementation, 100000, 0L,
                    infoEndLsn, true, false, -1L, new java.util.HashMap<>());
            deleteReader.addTargetType(LogEntryType.LOG_IN_DELETE_INFO);
            boolean foundDeleteInfo = false;
            while (deleteReader.readNextEntry()) {
                if (deleteReader.isDeleteInfo()) {
                    assertThat(deleteReader.getDeletedNodeId()).isEqualTo(9L);
                    assertThat(deleteReader.getDeletedIdKey()).containsExactly(3);
                    foundDeleteInfo = true;
                    break;
                }
            }
            INFileReader duplicateReader = new INFileReader(implementation, 100000, 0L,
                    infoEndLsn, true, false, -1L, new java.util.HashMap<>());
            duplicateReader.addTargetType(LogEntryType.LOG_IN_DUPDELETE_INFO);
            boolean foundDuplicateInfo = false;
            while (duplicateReader.readNextEntry()) {
                if (duplicateReader.isDupDeleteInfo()) {
                    assertThat(duplicateReader.getDupDeletedNodeId()).isEqualTo(10L);
                    assertThat(duplicateReader.getDupDeletedMainKey()).containsExactly(4);
                    assertThat(duplicateReader.getDupDeletedDupKey()).containsExactly(5);
                    foundDuplicateInfo = true;
                    break;
                }
            }
            assertThat(foundDeleteInfo).isTrue();
            assertThat(foundDuplicateInfo).isFalse();
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static DatabaseEntry entry(int value) {
        return new DatabaseEntry(new byte[] {(byte) value});
    }
}
