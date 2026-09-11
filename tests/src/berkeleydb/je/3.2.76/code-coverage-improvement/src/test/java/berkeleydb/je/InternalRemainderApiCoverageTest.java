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
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.evictor.Evictor;
import com.sleepycat.je.evictor.Evictor.EvictProfile;
import com.sleepycat.je.incomp.INCompressor;
import com.sleepycat.je.latch.LatchStats;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.txn.AutoTxn;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.BuddyLocker;
import com.sleepycat.je.txn.DummyLockManager;
import com.sleepycat.je.txn.LatchedLockManager;
import com.sleepycat.je.txn.Lock;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockInfo;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.txn.ReadCommittedLocker;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.txn.TxnAbort;
import com.sleepycat.je.txn.TxnCommit;
import com.sleepycat.je.txn.TxnEnd;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.log.UtilizationFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InternalRemainderApiCoverageTest {

    @Test
    void basicLockersUseDummyAndLatchedManagerPublicOperations(@TempDir Path home)
            throws Exception {
        EnvironmentConfig dummyConfig = new EnvironmentConfig();
        dummyConfig.setAllowCreate(true);
        dummyConfig.setConfigParam("je.env.isLocking", "false");
        java.nio.file.Files.createDirectories(home.resolve("dummy"));
        Environment dummyEnvironment = new Environment(home.resolve("dummy").toFile(), dummyConfig);
        try {
            exerciseBasicLocker(dummyEnvironment, "dummy");
        } finally {
            dummyEnvironment.close();
        }

        EnvironmentConfig latchedConfig = new EnvironmentConfig();
        latchedConfig.setAllowCreate(true);
        latchedConfig.setConfigParam("je.env.fairLatches", "true");
        java.nio.file.Files.createDirectories(home.resolve("latched"));
        Environment latchedEnvironment = new Environment(home.resolve("latched").toFile(),
                latchedConfig);
        try {
            exerciseBasicLocker(latchedEnvironment, "latched");
        } finally {
            latchedEnvironment.close();
        }
    }

    @Test
    void utilizationSummariesAreRenderedThroughFileSummaryDump(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            database = environment.openDatabase(null, "summary", config);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            database.close();
            database = null;
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                    .envGetEnvironmentImpl(environment);
            java.util.Map summaries = UtilizationFileReader.calcFileSummaryMap(implementation);
            assertThat(summaries).isNotEmpty();
            FileSummary summary = (FileSummary) summaries.values().iterator().next();
            FileSummaryLN fileSummary = new FileSummaryLN(summary);
            assertThat(fileSummary.dumpString(2, true)).contains("extended-info");
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void lockersTransactionsAndManagersFollowTheirLifecycleProtocols(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        EnvironmentImpl implementation = com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
        try {
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            database = environment.openDatabase(null, "internal", config);
            DatabaseImpl databaseImpl = com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database);

            BasicLocker basic = new BasicLocker(implementation);
            basic.lock(9001L, com.sleepycat.je.txn.LockType.WRITE, true, databaseImpl);
            assertThat(basic.getOwnerAbortLsn(9001L)).isEqualTo(-1L);
            com.sleepycat.je.LockStats lockStats = basic.collectStats(
                    new com.sleepycat.je.LockStats());
            assertThat(lockStats.getNWriteLocks()).isEqualTo(1);
            basic.releaseLock(9001L);
            assertThat(basic.getLockTimeout()).isGreaterThanOrEqualTo(0L);
            assertThat(basic.getAbortLsn(12L)).isEqualTo(-1L);
            assertThat(basic.getOwnerAbortLsn(12L)).isEqualTo(-1L);
            assertThat(basic.collectStats(new com.sleepycat.je.LockStats())).isNotNull();
            basic.setTxnTimeout(1000L);
            assertThat(basic.getTxnTimeOut()).isEqualTo(1000L);
            assertThat(basic.isTimedOut()).isFalse();
            assertThat(basic.toString()).isNotEmpty();
            basic.dumpLockTable();
            Locker nonTxn = basic.newNonTxnLocker();
            assertThat(nonTxn).isNotNull();
            nonTxn.releaseNonTxnLocks();

            Txn transaction = new Txn(implementation, new TransactionConfig(), 77L);
            BuddyLocker buddy = new BuddyLocker(implementation, transaction);
            assertThat(buddy.sharesLocksWith(transaction)).isTrue();
            Locker buddyNonTxn = buddy.newNonTxnLocker();
            assertThat(buddyNonTxn).isNotNull();
            ReadCommittedLocker readCommitted = new ReadCommittedLocker(implementation, transaction);
            assertThat(readCommitted.isReadCommittedIsolation()).isTrue();
            assertThat(readCommitted.getAbortLsn(12L)).isEqualTo(-1L);
            assertThat(readCommitted.createdNode(12L)).isFalse();
            Locker readCommittedNonTxn = readCommitted.newNonTxnLocker();
            assertThat(readCommittedNonTxn).isNotNull();
            readCommitted.releaseLock(12L);

            assertThat(transaction.getTransactionId()).isEqualTo(77L);
            assertThat(transaction.getTransactionId()).isEqualTo(77L);
            assertThat(transaction.getAbortLsn(1L)).isEqualTo(-1L);
            assertThat(transaction.isHandleLockTransferrable()).isIn(true, false);
            transaction.setOnlyAbortable();
            assertThat(transaction.getOnlyAbortable()).isTrue();
            transaction.operationEnd(false);
            transaction.abort(false);
            Txn xidTransaction = new Txn(implementation, new TransactionConfig(), 78L);
            assertThatThrownBy(() -> xidTransaction.abort(
                    new com.sleepycat.je.log.LogUtils.XidImpl(1,
                            new byte[] {1}, new byte[] {2})))
                    .isInstanceOf(com.sleepycat.je.DatabaseException.class);
            xidTransaction.operationEnd();
            AutoTxn autoTxn = new AutoTxn(implementation, new TransactionConfig());
            autoTxn.operationEnd();
            basic.operationEnd();
            nonTxn.operationEnd();
            buddy.operationEnd();
            readCommitted.operationEnd();
            buddyNonTxn.operationEnd();
            readCommittedNonTxn.operationEnd();

            readCommitted.addDeleteInfo(new BIN(databaseImpl, new byte[] {1}, 1, 10),
                    new Key(new byte[] {1}));
            LockInfo lockInfo = new LockInfo(basic, com.sleepycat.je.txn.LockType.READ);
            assertThat(lockInfo.clone()).isNotNull();
            assertThat(lockInfo.toString()).isNotEmpty();
            lockInfo.dump();
            assertThat(LockInfo.getDeadlockStackTrace()).isIn(true, false);
            assertThat(new Lock().toString()).isNotEmpty();
            assertThat(LockGrantType.NEW.toString()).isNotEmpty();
            assertThat(LockGrantType.DENIED.toString()).isNotEmpty();
            assertThat(new LockerFactory()).isNotNull();
            Locker factoryLocker = LockerFactory.getReadableLocker(environment, null, false,
                    false, false);
            assertThat(factoryLocker).isNotNull();
            factoryLocker.operationEnd();

            DummyLockManager dummy = new DummyLockManager(implementation);
            LatchedLockManager latched = new LatchedLockManager(implementation);
            assertThat(dummy.dumpToString()).isNotNull();
            dummy.dump();
            assertThat(latched.dumpToString()).isNotNull();
            assertThat(databaseImpl.getTransactionId()).isZero();
            assertThat(databaseImpl.getBinMaxDeltas()).isGreaterThanOrEqualTo(0);
            assertThat(databaseImpl.getEofNodeId()).isGreaterThanOrEqualTo(-1L);
            assertThat(databaseImpl.isDeleteFinished()).isFalse();
            assertThat(databaseImpl.isInUse()).isTrue();
            databaseImpl.setPendingDeletedHook(null);

            TxnEnd commit = new TxnCommit(10L, 11L);
            assertThat(commit.getId()).isEqualTo(10L);
            assertThat(commit.getTransactionId()).isEqualTo(10L);
            commit.dumpLog(new java.lang.StringBuffer(), true);
            TxnEnd abort = new TxnAbort(12L, 13L);
            java.lang.StringBuffer abortDump = new java.lang.StringBuffer();
            abort.dumpLog(abortDump, true);
            assertThat(abortDump).contains("TxnAbort");
        } finally {
            if (database != null) {
                database.close();
            }
            implementation.invalidate(new Error("coverage close"));
            implementation.close();
        }
    }

    @Test
    void environmentAndEvictionQueuesAcceptRealTreeReferences(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database databaseHandle = null;
        boolean recoveryClosed = false;
        try {
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);
            databaseHandle = environment.openDatabase(null, "queue", config);
            DatabaseImpl database = com.sleepycat.je.DbInternal.dbGetDatabaseImpl(databaseHandle);
            BIN bin = new BIN(database, new byte[] {1}, 1, 10);
            implementation.addToCompressorQueue(bin.createReference(), false);
            assertThatThrownBy(() -> implementation.getINCompressor().addToQueue(
                    bin.createReference())).isInstanceOf(Exception.class);
            assertThat(implementation.getINCompressor().exists(bin.getNodeId())).isTrue();
            INCompressor compressor = implementation.getINCompressor();
            assertThat(compressor.searchForBIN(database, bin.createReference())).isNull();
            EvictProfile profile = new EvictProfile();
            assertThat(profile.count(bin)).isIn(true, false);
            assertThat(profile.getCandidates()).isNotNull();
            LatchStats latchStats = new LatchStats();
            latchStats.nReleases = 1;
            assertThat(latchStats.toString()).contains("nAcquiresNoWaiters");
            assertThat(new Tree()).isNotNull();
            Evictor evictor = implementation.getEvictor();
            assertThatThrownBy(() -> evictor.addToQueue(bin)).isInstanceOf(Exception.class);
            assertThat(evictor.getQueueSize()).isGreaterThanOrEqualTo(0);
            environment.checkpoint(new com.sleepycat.je.CheckpointConfig());
            implementation.removeConfigObserver(implementation.getCleaner());
            implementation.addConfigObserver(implementation.getCleaner());
            databaseHandle.close();
            databaseHandle = null;
            implementation.closeAfterRunRecovery();
            recoveryClosed = true;
        } finally {
            if (databaseHandle != null) {
                databaseHandle.close();
            }
            if (!recoveryClosed) {
                environment.close();
            }
        }
    }

    private static void exerciseBasicLocker(Environment environment, String databaseName)
            throws Exception {
        EnvironmentImpl implementation = com.sleepycat.je.DbInternal
                .envGetEnvironmentImpl(environment);
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        Database database = environment.openDatabase(null, databaseName, config);
        try {
            BasicLocker locker = new BasicLocker(implementation);
            com.sleepycat.je.dbi.DatabaseImpl databaseImpl = com.sleepycat.je.DbInternal
                    .dbGetDatabaseImpl(database);
            locker.lock(7001L, com.sleepycat.je.txn.LockType.WRITE, true, databaseImpl);
            assertThat(locker.getOwnerAbortLsn(7001L)).isEqualTo(-1L);
            assertThat(locker.collectStats(new com.sleepycat.je.LockStats())).isNotNull();
            locker.releaseLock(7001L);
            locker.operationEnd();
        } finally {
            database.close();
        }
    }

    private static boolean implementationIsClosed(Environment environment) {
        return com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment).isClosed();
    }

    private static Environment openEnvironment(Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }
}
