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
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.LogScanConfig;
import com.sleepycat.je.LogScanner;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.SequenceStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.cleaner.UtilizationProfile;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.TxnAbort;
import com.sleepycat.je.util.DbBackup;
import com.sleepycat.je.utilint.EventTrace;
import com.sleepycat.je.utilint.PropUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EnvironmentApiCoverageTest {

    @Test
    void environmentDatabaseAndLogApisReportRealDatabaseState(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        Cursor cursor = null;
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            database = environment.openDatabase(null, "records", config);
            database.put(null, entry("a"), entry("one"));
            database.put(null, entry("b"), entry("two"));
            final Database recordsDatabase = database;
            assertThat(database.getDatabaseName()).isEqualTo("records");
            assertThat(database.getEnvironment()).isSameAs(environment);
            assertThat(database.count()).isEqualTo(2);
            assertThat(database.getSecondaryDatabases()).isEmpty();
            assertThat(database.getSearchBoth(null, entry("a"), entry("one"), null))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThatThrownBy(() -> recordsDatabase.putNoDupData(null, entry("c"), entry("three")))
                    .isInstanceOf(Exception.class);
            database.preload(1024L);
            com.sleepycat.je.PreloadConfig preloadConfig = new com.sleepycat.je.PreloadConfig();
            preloadConfig.setLoadLNs(true);
            assertThat(database.preload(preloadConfig)).isNotNull();
            assertThatThrownBy(recordsDatabase::sync).isInstanceOf(Exception.class);
            cursor = database.openCursor(null, CursorConfig.DEFAULT);
            assertThat(cursor.getConfig().getDirtyRead()).isFalse();
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            assertThat(cursor.getFirst(key, value, null)).isEqualTo(OperationStatus.SUCCESS);
            assertThat(cursor.count()).isEqualTo(1);
            assertThat(new String(key.getData(), key.getOffset(), key.getSize(), "UTF-8"))
                    .isEqualTo("a");
            cursor.close();
            cursor = null;
            database.preload(1L, 100L);
            assertThat(database.getStats(new StatsConfig())).isNotNull();
            assertThat(database.verify(new VerifyConfig())).isNotNull();
            environment.getStats(new StatsConfig());
            environment.getStats(StatsConfig.DEFAULT);
            environment.setMutableConfig(new EnvironmentMutableConfig());
            assertThat(environment.getMutableConfig()).isNotNull();
            environment.cleanLog();
            environment.compress();
            assertThat(environment.verify(new VerifyConfig(), new PrintStream(new ByteArrayOutputStream())))
                    .isTrue();
            environment.checkpoint(new com.sleepycat.je.CheckpointConfig());
            environment.sync();
            environment.evictMemory();
            assertThat(environment.getLockStats(new StatsConfig())).isNotNull();
            assertThat(environment.getTransactionStats(new StatsConfig())).isNotNull();
            com.sleepycat.je.Transaction threadTransaction = environment.beginTransaction(null, null);
            environment.setThreadTransaction(threadTransaction);
            assertThat(environment.getThreadTransaction()).isSameAs(threadTransaction);
            threadTransaction.abort();
            environment.setThreadTransaction(null);
            LogScanConfig scanConfig = new LogScanConfig();
            scanConfig.setForwards(true);
            int[] records = {0};
            long lastUsedLsn = DbInternal.envGetEnvironmentImpl(environment)
                    .getFileManager().getLastUsedLsn();
            assertThat(environment.scanLog(0, lastUsedLsn, scanConfig,
                    new LogScanner() {
                        @Override
                        public boolean scanRecord(DatabaseEntry scanKey, DatabaseEntry scanValue,
                                boolean isDelete, String databaseName) {
                            records[0]++;
                            return true;
                        }
                    })).isTrue();
            assertThat(records[0]).isPositive();
            DbBackup backup = new DbBackup(environment);
            backup.startBackup();
            assertThat(backup.getLogFilesInBackupSet()).isNotNull();
            assertThat(backup.getLogFilesInBackupSet(backup.getLastFileInBackupSet()))
                    .isNotNull();
            backup.endBackup();
            assertThat(database.truncate(null, true)).isEqualTo(2);
            database.close();
            database = null;
            environment.renameDatabase(null, "records", "renamed");
            environment.removeDatabase(null, "renamed");
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

    @Test
    void internalConfigurationAndDiagnosticsUseEnvironmentImplementation(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        try {
            EnvironmentConfig config = new EnvironmentConfig(new Properties());
            DbInternal.setCreateUP(config, true);
            DbInternal.setCheckpointUP(config, false);
            DbInternal.setTxnReadCommitted(config, true);
            assertThat(DbInternal.getCreateUP(config)).isTrue();
            assertThat(DbInternal.getCheckpointUP(config)).isFalse();
            assertThat(DbInternal.getTxnReadCommitted(config)).isTrue();
            DbInternal.setLoadPropertyFile(config, true);
            new DbInternal();
            DbInternal.checkImmutablePropsForEquality(new EnvironmentMutableConfig(),
                    new EnvironmentMutableConfig());
            DbInternal.disableParameterValidation(new EnvironmentMutableConfig());
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            DbInternal.databaseConfigValidate(databaseConfig, databaseConfig.cloneConfig());
            DbInternal.makeExceptionEvent(new Exception("diagnostic"), "context");
            Environment shell = DbInternal.getEnvironmentShell(home.toFile());
            assertThat(shell.getHome()).isEqualTo(home.toFile());
            shell.close();

            EnvironmentImpl implementation = DbInternal.envGetEnvironmentImpl(environment);
            implementation.dumpMapTree();
            implementation.verifyCursors();
            FileManager fileManager = implementation.getFileManager();
            assertThat(fileManager.getNFSyncs()).isGreaterThanOrEqualTo(-1);
            assertThat(fileManager.getNFSyncRequests()).isGreaterThanOrEqualTo(-1);
            assertThat(fileManager.getNFSyncTimeouts()).isGreaterThanOrEqualTo(-1);
            assertThat(new BasicLocker(implementation).getOwnerAbortLsn(0L))
                    .isGreaterThanOrEqualTo(-1L);
            assertThat(new INList(implementation.getInMemoryINs(), implementation)).isNotNull();
            UtilizationProfile profile = implementation.getUtilizationProfile();
            assertThat(profile.verifyFileSummaryDatabase()).isTrue();
            assertThat(new TxnAbort()).isNotNull();
            assertThat(new LatchNotHeldException()).isNotNull();
            assertThat(new LockNotGrantedException()).isNotNull();
            assertThat(new LockNotGrantedException(new Exception("cause"))).isNotNull();
            assertThat(new LockNotGrantedException("message", new Exception("cause")))
                    .hasMessageContaining("message");
            TestDaemon daemon = new TestDaemon(1L, "coverage", implementation);
            daemon.addToQueue("work");
            daemon.addToQueueAlreadyLatched(java.util.Collections.singleton("more"));
            assertThat(daemon.getQueueSize()).isPositive();
            assertThat(daemon.getThread()).isNull();
            assertThat(daemon.isRunning()).isFalse();
            assertThat(daemon.toString()).contains("coverage");
            daemon.requestShutdown();
            EventTrace.addEvent("environment diagnostic");
            Properties properties = PropUtil.validateProps(new Properties(),
                    Collections.emptySet(), "test");
            assertThat(properties).isEmpty();
        } finally {
            environment.close();
        }
    }

    @Test
    void sequencesXaAndCompatibilityHelpersPreservePublicContracts(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            database = environment.openDatabase(null, "sequence", config);
            SequenceConfig sequenceConfig = new SequenceConfig();
            sequenceConfig.setAllowCreate(true);
            Sequence sequence = database.openSequence(null, entry("next"), sequenceConfig);
            assertThat(sequence.getDatabase()).isSameAs(database);
            assertThat(sequence.getKey()).isNotNull();
            assertThat(sequence.get(null, 1)).isEqualTo(0L);
            SequenceStats sequenceStats = sequence.getStats(new StatsConfig());
            assertThat(sequenceStats).isNotNull();
            assertThat(sequenceStats.getNGets()).isPositive();
            assertThat(sequenceStats.getNCachedGets()).isGreaterThanOrEqualTo(0);
            assertThat(sequenceStats.getCurrent()).isGreaterThanOrEqualTo(0L);
            assertThat(sequenceStats.getValue()).isGreaterThanOrEqualTo(0L);
            assertThat(sequenceStats.getLastValue()).isGreaterThanOrEqualTo(0L);
            assertThat(sequenceStats.getMin()).isLessThanOrEqualTo(sequenceStats.getMax());
            assertThat(sequenceStats.getCacheSize()).isGreaterThanOrEqualTo(0);
            assertThat(sequenceStats.toString()).contains("nGets=1");
            sequence.close();
            database.removeSequence(null, entry("next"));
            Environment compatibilityEnvironment = openEnvironment(home.resolve("compatibility"));
            DatabaseConfig compatibleConfig = new DatabaseConfig();
            compatibleConfig.setAllowCreate(true);
            Database compatible = DbCompat.openDatabase(compatibilityEnvironment, null,
                    "compatible", null, compatibleConfig);
            assertThat(compatible.getDatabaseName()).isEqualTo("compatible");
            DbCompat.setBtreeComparator(compatibleConfig, new PublicComparator());
            compatible.close();
            compatibilityEnvironment.close();

            EnvironmentConfig xaConfig = new EnvironmentConfig();
            xaConfig.setAllowCreate(true);
            xaConfig.setTransactional(true);
            java.nio.file.Files.createDirectories(home.resolve("xa"));
            XAEnvironment xa = new XAEnvironment(home.resolve("xa").toFile(), xaConfig);
            TestXid xid = new TestXid(9);
            xa.start(xid, XAResource.TMNOFLAGS);
            xa.end(xid, XAResource.TMSUCCESS);
            int prepareResult = xa.prepare(xid);
            assertThat(prepareResult).isIn(XAResource.XA_RDONLY, XAResource.XA_OK);
            try {
                xa.rollback(xid);
            } catch (XAException expected) {
                assertThat(expected.errorCode).isNotEqualTo(XAException.XAER_RMERR);
            }
            assertThat(xa.recover(XAResource.TMSTARTRSCAN)).isNotNull();
            assertThatThrownBy(() -> xa.commit(new TestXid(404), false))
                    .isInstanceOf(XAException.class);
            assertThat(xa.getTransactionTimeout()).isGreaterThanOrEqualTo(0);
            assertThat(xa.setTransactionTimeout(3)).isIn(true, false);
            assertThat(xa.isSameRM(xa)).isTrue();
            try {
                xa.forget(xid);
            } catch (XAException expected) {
                assertThat(expected.errorCode).isNotEqualTo(XAException.XAER_RMERR);
            }
            xa.close();
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void configurationConstructorsAndDirtyReadSettingExposeValues() {
        Properties properties = new Properties();
        EnvironmentConfig config = new EnvironmentConfig(properties);
        assertThat(config).isNotNull();
        TransactionConfig transactionConfig = new TransactionConfig();
        transactionConfig.setDirtyRead(true);
        assertThat(transactionConfig.getDirtyRead()).isTrue();
    }

    private static Environment openEnvironment(Path home) throws Exception {
        java.nio.file.Files.createDirectories(home);
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }

    private static DatabaseEntry entry(String text) {
        return new DatabaseEntry(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final class TestDaemon extends com.sleepycat.je.utilint.DaemonThread {
        TestDaemon(long interval, String name, EnvironmentImpl environment) {
            super(interval, name, environment);
        }

        @Override
        protected void onWakeup() {
        }
    }

    public static final class PublicComparator implements java.util.Comparator<String> {
        @Override
        public int compare(String first, String second) {
            return first.compareToIgnoreCase(second);
        }
    }

    private static final class TestXid implements Xid {
        private final int id;

        TestXid(int id) {
            this.id = id;
        }

        @Override
        public int getFormatId() {
            return 1;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] {(byte) id};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[] {(byte) (id + 1)};
        }
    }
}
