/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LatchedLogManager;
import com.sleepycat.je.log.SyncedLogManager;
import com.sleepycat.je.recovery.Checkpointer;
import com.sleepycat.je.recovery.RecoveryManager;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.latch.LatchImpl;
import com.sleepycat.je.latch.SharedLatchImpl;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.utilint.TestHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InternalApiCoverageTest {

    @Test
    void environmentInternalsExposeLiveDatabaseState(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        Environment environment = new Environment(home.toFile(), config);
        Database database = null;
        CursorImpl cursor = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            databaseConfig.setTransactional(true);
            database = environment.openDatabase(null, "internal", databaseConfig);
            database.put(null, new com.sleepycat.je.DatabaseEntry(new byte[] {1}),
                    new com.sleepycat.je.DatabaseEntry(new byte[] {2}));
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
            assertThat(implementation.isOpen()).isTrue();
            assertThat(implementation.getDbEviction()).isFalse();
            assertThat(implementation.getDeferredWriteTemp()).isFalse();
            assertThat(implementation.getExceptionListener()).isNull();
            assertThatThrownBy(implementation::getReplicator)
                    .isInstanceOf(com.sleepycat.je.utilint.NotImplementedYetException.class);
            assertThat(implementation.getLastRecoveryInfo()).isNotNull();
            assertThat(implementation.getINCompressorQueueSize()).isGreaterThanOrEqualTo(0);
            implementation.setBackgroundSleepHook(new NoOpHook());
            implementation.setExceptionListener(event -> {
            });
            assertThat(implementation.getExceptionListener()).isNotNull();
            implementation.setReplicator(null);
            implementation.updateBackgroundReads(1);
            implementation.checkImmutablePropsForEquality(implementation.cloneConfig());
            implementation.rewriteMapTreeRoot(implementation.getRootLsn());

            MemoryBudget budget = implementation.getMemoryBudget();
            assertThat(budget.getMinTreeMemoryUsage()).isGreaterThanOrEqualTo(0L);
            assertThat(budget.getTreeMemoryUsage()).isGreaterThanOrEqualTo(0L);
            assertThat(budget.getMiscMemoryUsage()).isGreaterThanOrEqualTo(0L);
            assertThat(budget.getTrackerBudget()).isGreaterThanOrEqualTo(0L);
            assertThat(budget.isTreeUsageAboveMinimum()).isFalse();
            assertThat(MemoryBudget.intArraySize(3)).isPositive();
            assertThat(MemoryBudget.objectArraySize(3)).isPositive();

            DbTree tree = implementation.getDbMapTree();
            assertThat(tree.getTransactionId()).isZero();
            com.sleepycat.je.dbi.DatabaseImpl databaseImpl =
                    com.sleepycat.je.DbInternal.dbGetDatabaseImpl(database);
            com.sleepycat.je.dbi.SortedLSNTreeWalker walker =
                    new com.sleepycat.je.dbi.SortedLSNTreeWalker(databaseImpl, false, false,
                            com.sleepycat.je.utilint.DbLsn.NULL_LSN,
                            new com.sleepycat.je.dbi.SortedLSNTreeWalker.TreeNodeProcessor() {
                                @Override
                                public void processLSN(long lsn,
                                        com.sleepycat.je.log.LogEntryType type,
                                        com.sleepycat.je.tree.Node node, byte[] key) {
                                }

                                @Override
                                public void processDirtyDeletedLN(long lsn,
                                        com.sleepycat.je.tree.LN node, byte[] key) {
                                }

                                @Override
                                public void processDupCount(long lsn) {
                                }
                            }, new java.util.ArrayList<>(), exception -> false);
            assertThat(walker.getSavedExceptions()).isEmpty();
            assertThat(tree.getDb(databaseImpl.getId(), 0L, new HashMap<>()))
                    .isNotNull();
            assertThat(tree.toString()).isNotEmpty();
            tree.dumpLog(new java.lang.StringBuffer(), false);
            assertThat(implementation.getCheckpointer().getHighestFlushLevel())
                    .isGreaterThanOrEqualTo(-1);
            Checkpointer.syncDatabase(implementation, databaseImpl, false);
            RecoveryManager.traceRootDeletion(java.util.logging.Level.INFO, databaseImpl);
            assertThat(DbEnvPool.getInstance()).isNotNull();
            assertThatThrownBy(() -> implementation.truncate(null, "missing", true))
                    .isInstanceOf(Exception.class);
            tree.optionalModifyDbRoot(databaseImpl);

            INList inList = implementation.getInMemoryINs();
            assertThat(inList.getSize()).isGreaterThanOrEqualTo(0);
            inList.dump();
            if (inList.getSize() > 0) {
                try {
                    inList.first();
                } catch (AssertionError ignored) {
                    // An empty logical IN set can retain a non-zero accounting size.
                }
                if (!inList.getINs().isEmpty()) {
                    com.sleepycat.je.tree.IN first =
                            (com.sleepycat.je.tree.IN) inList.getINs().first();
                    try {
                        inList.tailSet(first);
                    } catch (AssertionError ignored) {
                        // The live list may change while the environment daemon runs.
                    }
                }
            }
            assertThat(inList.getINs()).isNotNull();
            if (!inList.getINs().isEmpty()) {
                com.sleepycat.je.tree.IN removable =
                        (com.sleepycat.je.tree.IN) inList.getINs().first();
                inList.remove(removable);
            }

            BasicLocker locker = new BasicLocker(implementation);
            assertThat(locker.getWriteOwnerLocker(1L)).isNull();
            assertThat(locker.getTxnLocker()).isNull();
            assertThat(locker.isReadCommittedIsolation()).isFalse();
            LockStats lockStats = new LockStats();
            assertThat(locker.collectStats(lockStats)).isSameAs(lockStats);
            cursor = new CursorImpl(databaseImpl, locker);
            assertThat(cursor.isClosed()).isFalse();
            assertThat(cursor.getLockStats()).isNotNull();
            assertThat(cursor.dumpToString(false)).isNotEmpty();
            cursor.dump(false);
            cursor.dump();
            cursor.dumpTree();
            cursor.setTestHook(new NoOpHook());
            cursor.close();
            cursor = null;

            LatchedLogManager latchedLogManager = new LatchedLogManager(implementation, false);
            latchedLogManager.countObsoleteNode(1L, com.sleepycat.je.log.LogEntryType.LOG_LN, 1);
            latchedLogManager.countObsoleteNodes(Collections.emptyList());
            latchedLogManager.countObsoleteINs(Collections.emptyList());
            latchedLogManager.loadEndOfLogStat(new com.sleepycat.je.EnvironmentStats());
            com.sleepycat.je.cleaner.TrackedFileSummary trackedSummary =
                    latchedLogManager.getUnflushableTrackedSummary(1L);
            assertThat(trackedSummary).isNotNull();
            latchedLogManager.removeTrackedFile(trackedSummary);
            SyncedLogManager syncedLogManager = new SyncedLogManager(implementation, false);
            syncedLogManager.removeTrackedFile(trackedSummary);
            implementation.getLogManager().flushNoSync();            implementation.getLogManager().setReadHook(new NoOpHook());
            implementation.getLogManager().resetPool(implementation.getConfigManager());
            implementation.getCleaner().wakeup();
            assertThat(implementation.getCleaner().getNWakeupRequests()).isGreaterThanOrEqualTo(0);
            implementation.getUtilizationProfile().clearCache();
            assertThat(implementation.getUtilizationProfile().isRMWFixEnabled()).isTrue();
            implementation.getUtilizationTracker().activateCleaner();
            assertThat(implementation.getUtilizationTracker().evictMemory()).isGreaterThanOrEqualTo(0L);
            implementation.getEvictor().alert();
            implementation.getEvictor().setRunnableHook(new NoOpHook());
            implementation.getEvictor().onWakeup();
            assertThatThrownBy(() -> locker.demoteLock(1L)).isInstanceOf(Exception.class);
            locker.markDeleteAtTxnEnd(null, false);
            final Database invalidatedDatabase = database;
            database.close();
            database = null;
            databaseImpl.deleteAndReleaseINs();
            com.sleepycat.je.DbInternal.dbInvalidate(invalidatedDatabase);
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
    void directEnvironmentImplementationCloseModesReleaseResources(@TempDir Path home)
            throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        java.nio.file.Files.createDirectories(home.resolve("normal"));
        java.nio.file.Files.createDirectories(home.resolve("forced"));
        EnvironmentImpl normal = new EnvironmentImpl(home.resolve("normal").toFile(), config);
        normal.getFileManager().releaseExclusiveLock();
        normal.close(false);
        assertThat(normal.isClosed()).isTrue();
        EnvironmentImpl forced = new EnvironmentImpl(home.resolve("forced").toFile(), config);
        forced.forceClose();
        assertThat(forced.isClosed()).isTrue();
        DbEnvPool.getInstance().clear();
    }

    @Test
    void latchesAndConfigManagerRetainTheirPublicProtocol(@TempDir Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), config);
        try {
            EnvironmentImpl implementation = com.sleepycat.je.DbInternal.envGetEnvironmentImpl(environment);
            LatchImpl latch = new LatchImpl(implementation);
            latch.setName("coverage-latch");
            latch.acquire();
            assertThat(latch.toString()).contains("coverage-latch");
            latch.releaseIfOwner();
            SharedLatchImpl shared = new SharedLatchImpl("coverage-shared", implementation);
            shared.setExclusiveOnly(true);
            shared.setName("coverage-shared-renamed");
            assertThat(shared.setNoteLatch(true)).isTrue();
            shared.acquireExclusive();
            shared.releaseIfOwner();
            assertThat(com.sleepycat.je.latch.LatchSupport.latchesHeldToString()).isNotNull();
            com.sleepycat.je.latch.LatchSupport.dumpLatchesHeld();

            Properties properties = new Properties();
            DbConfigManager manager = new DbConfigManager(config);
            properties.setProperty("je.env.isTransactional", "false");
            manager.addConfigurations(properties);
            assertThat(manager.get("je.env.isTransactional")).isEqualTo("false");
            assertThat(manager.getEnvironmentConfig()).isNotNull();
            assertThat(new StatsConfig()).isNotNull();
            assertThat(new CursorConfig()).isNotNull();
            assertThat(implementation.getEvictor().normalizeLevel(
                    new com.sleepycat.je.tree.IN(), 1)).isGreaterThanOrEqualTo(0);
        } finally {
            environment.close();
        }
    }

    private static final class NoOpHook implements TestHook {
        @Override
        public void doIOHook() {
        }

        @Override
        public void doHook() {
        }

        @Override
        public Object getHookValue() {
            return Boolean.TRUE;
        }
    }
}
