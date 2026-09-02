/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ForeignKeyDeleteAction;
import com.sleepycat.je.JoinConfig;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.LogScanConfig;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.VerifyConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PublicStateApiCoverageTest {

    @Test
    void statisticsObjectsExposeTheValuesTheyStore() {
        EnvironmentStats environmentStats = new EnvironmentStats();
        environmentStats.setAdminBytes(1L);
        environmentStats.setBufferBytes(2L);
        environmentStats.setCacheDataBytes(3L);
        environmentStats.setLockBytes(5L);
        environmentStats.setRequiredEvictBytes(6L);
        environmentStats.setNNotResident(7L);
        environmentStats.setNCacheMiss(8L);
        environmentStats.setEndOfLog(9L);
        environmentStats.setLastCheckpointEnd(10L);
        environmentStats.setLastCheckpointId(11L);
        environmentStats.setLastCheckpointStart(12L);
        environmentStats.setNFSyncs(13L);
        environmentStats.setNFSyncRequests(14L);
        environmentStats.setNFSyncTimeouts(15L);
        environmentStats.setNBINsStripped(16L);
        environmentStats.setNNodesExplicitlyEvicted(17L);
        environmentStats.setNNodesScanned(18L);
        environmentStats.setNNodesSelected(19L);
        environmentStats.setNRepeatFaultReads(20L);
        environmentStats.setNRepeatIteratorReads(21L);
        environmentStats.setNTempBufferWrites(22L);
        environmentStats.setTotalLogSize(23L);
        environmentStats.setCleanerBacklog(24);
        environmentStats.setCursorsBins(25);
        environmentStats.setDbClosedBins(26);
        environmentStats.setInCompQueueSize(27);
        environmentStats.setNCheckpoints(28);
        environmentStats.setNCleanerDeletions(29);
        environmentStats.setNCleanerEntriesRead(30);
        environmentStats.setNCleanerRuns(31);
        environmentStats.setNClusterLNsProcessed(32);
        environmentStats.setNDeltaINFlush(33);
        environmentStats.setNEvictPasses(34);
        environmentStats.setNFullBINFlush(35);
        environmentStats.setNFullINFlush(36);
        environmentStats.setNINsCleaned(37);
        environmentStats.setNINsDead(38);
        environmentStats.setNINsMigrated(39);
        environmentStats.setNINsObsolete(40);
        environmentStats.setNLNQueueHits(41);
        environmentStats.setNLNsCleaned(42);
        environmentStats.setNLNsDead(43);
        environmentStats.setNLNsLocked(44);
        environmentStats.setNLNsMarked(45);
        environmentStats.setNLNsMigrated(46);
        environmentStats.setNLNsObsolete(47);
        environmentStats.setNLogBuffers(48);
        environmentStats.setNMarkedLNsProcessed(49);
        environmentStats.setNPendingLNsLocked(50);
        environmentStats.setNPendingLNsProcessed(51);
        environmentStats.setNToBeCleanedLNsProcessed(52);
        environmentStats.setNonEmptyBins(53);
        environmentStats.setProcessedBins(54);
        environmentStats.setSplitBins(55);
        assertThat(environmentStats.getAdminBytes()).isEqualTo(1L);
        assertThat(environmentStats.getBufferBytes()).isEqualTo(2L);
        assertThat(environmentStats.getCacheDataBytes()).isEqualTo(3L);
        assertThat(environmentStats.getCacheTotalBytes()).isEqualTo(5L);
        assertThat(environmentStats.getLockBytes()).isEqualTo(5L);
        assertThat(environmentStats.getRequiredEvictBytes()).isEqualTo(6L);
        assertThat(environmentStats.getNNotResident()).isEqualTo(7L);
        assertThat(environmentStats.getNCacheMiss()).isEqualTo(8L);
        assertThat(environmentStats.getEndOfLog()).isEqualTo(9L);
        assertThat(environmentStats.getLastCheckpointEnd()).isEqualTo(10L);
        assertThat(environmentStats.getLastCheckpointId()).isEqualTo(11L);
        assertThat(environmentStats.getLastCheckpointStart()).isEqualTo(12L);
        assertThat(environmentStats.getNFSyncs()).isEqualTo(13L);
        assertThat(environmentStats.getNFSyncRequests()).isEqualTo(14L);
        assertThat(environmentStats.getNFSyncTimeouts()).isEqualTo(15L);
        assertThat(environmentStats.getNBINsStripped()).isEqualTo(16L);
        assertThat(environmentStats.getNNodesExplicitlyEvicted()).isEqualTo(17L);
        assertThat(environmentStats.getNNodesScanned()).isEqualTo(18L);
        assertThat(environmentStats.getNNodesSelected()).isEqualTo(19L);
        assertThat(environmentStats.getNRepeatFaultReads()).isEqualTo(20L);
        assertThat(environmentStats.getNRepeatIteratorReads()).isEqualTo(21L);
        assertThat(environmentStats.getNTempBufferWrites()).isEqualTo(22L);
        assertThat(environmentStats.getTotalLogSize()).isEqualTo(23L);
        assertThat(environmentStats.getCleanerBacklog()).isEqualTo(24);
        assertThat(environmentStats.getCursorsBins()).isEqualTo(25);
        assertThat(environmentStats.getDbClosedBins()).isEqualTo(26);
        assertThat(environmentStats.getInCompQueueSize()).isEqualTo(27);
        assertThat(environmentStats.getNCheckpoints()).isEqualTo(28);
        assertThat(environmentStats.getNCleanerDeletions()).isEqualTo(29);
        assertThat(environmentStats.getNCleanerEntriesRead()).isEqualTo(30);
        assertThat(environmentStats.getNCleanerRuns()).isEqualTo(31);
        assertThat(environmentStats.getNClusterLNsProcessed()).isEqualTo(32);
        assertThat(environmentStats.getNDeltaINFlush()).isEqualTo(33);
        assertThat(environmentStats.getNEvictPasses()).isEqualTo(34);
        assertThat(environmentStats.getNFullBINFlush()).isEqualTo(35);
        assertThat(environmentStats.getNFullINFlush()).isEqualTo(36);
        assertThat(environmentStats.getNINsCleaned()).isEqualTo(37);
        assertThat(environmentStats.getNINsDead()).isEqualTo(38);
        assertThat(environmentStats.getNINsMigrated()).isEqualTo(39);
        assertThat(environmentStats.getNINsObsolete()).isEqualTo(40);
        assertThat(environmentStats.getNLNQueueHits()).isEqualTo(41);
        assertThat(environmentStats.getNLNsCleaned()).isEqualTo(42);
        assertThat(environmentStats.getNLNsDead()).isEqualTo(43);
        assertThat(environmentStats.getNLNsLocked()).isEqualTo(44);
        assertThat(environmentStats.getNLNsMarked()).isEqualTo(45);
        assertThat(environmentStats.getNLNsMigrated()).isEqualTo(46);
        assertThat(environmentStats.getNLNsObsolete()).isEqualTo(47);
        assertThat(environmentStats.getNLogBuffers()).isEqualTo(48);
        assertThat(environmentStats.getNMarkedLNsProcessed()).isEqualTo(49);
        assertThat(environmentStats.getNPendingLNsLocked()).isEqualTo(50);
        assertThat(environmentStats.getNPendingLNsProcessed()).isEqualTo(51);
        assertThat(environmentStats.getNToBeCleanedLNsProcessed()).isEqualTo(52);
        assertThat(environmentStats.getNonEmptyBins()).isEqualTo(53);
        assertThat(environmentStats.getProcessedBins()).isEqualTo(54);
        assertThat(environmentStats.getSplitBins()).isEqualTo(55);
        assertThat(environmentStats.toString()).contains("nCacheMiss=8");

        PreloadStats preloadStats = new PreloadStats();
        preloadStats.setNINsLoaded(1);
        preloadStats.setNBINsLoaded(2);
        preloadStats.setNLNsLoaded(3);
        preloadStats.setNDINsLoaded(4);
        preloadStats.setNDBINsLoaded(5);
        preloadStats.setNDupCountLNsLoaded(6);
        preloadStats.setStatus(PreloadStatus.SUCCESS);
        assertThat(preloadStats.getNINsLoaded()).isEqualTo(1);
        assertThat(preloadStats.getNBINsLoaded()).isEqualTo(2);
        assertThat(preloadStats.getNLNsLoaded()).isEqualTo(3);
        assertThat(preloadStats.getNDINsLoaded()).isEqualTo(4);
        assertThat(preloadStats.getNDBINsLoaded()).isEqualTo(5);
        assertThat(preloadStats.getNDupCountLNsLoaded()).isEqualTo(6);
        assertThat(preloadStats.getStatus()).isSameAs(PreloadStatus.SUCCESS);
        assertThat(preloadStats.toString()).contains("status=PreloadStatus.SUCCESS");

        BtreeStats btreeStats = new BtreeStats();
        btreeStats.setBottomInternalNodeCount(1L);
        btreeStats.setDuplicateBottomInternalNodeCount(2L);
        btreeStats.setDeletedLeafNodeCount(3L);
        btreeStats.setDupCountLeafNodeCount(4L);
        btreeStats.setInternalNodeCount(5L);
        btreeStats.setDuplicateInternalNodeCount(6L);
        btreeStats.setLeafNodeCount(7L);
        btreeStats.setMainTreeMaxDepth(8);
        btreeStats.setDuplicateTreeMaxDepth(9);
        btreeStats.setINsByLevel(new long[] {10L});
        btreeStats.setBINsByLevel(new long[] {11L});
        btreeStats.setDINsByLevel(new long[] {12L});
        btreeStats.setDBINsByLevel(new long[] {13L});
        assertThat(btreeStats.getBottomInternalNodeCount()).isEqualTo(1L);
        assertThat(btreeStats.getDuplicateBottomInternalNodeCount()).isEqualTo(2L);
        assertThat(btreeStats.getDeletedLeafNodeCount()).isEqualTo(3L);
        assertThat(btreeStats.getDupCountLeafNodeCount()).isEqualTo(4L);
        assertThat(btreeStats.getInternalNodeCount()).isEqualTo(5L);
        assertThat(btreeStats.getDuplicateInternalNodeCount()).isEqualTo(6L);
        assertThat(btreeStats.getLeafNodeCount()).isEqualTo(7L);
        assertThat(btreeStats.getMainTreeMaxDepth()).isEqualTo(8);
        assertThat(btreeStats.getDuplicateTreeMaxDepth()).isEqualTo(9);
        assertThat(btreeStats.getINsByLevel()).containsExactly(10L);
        assertThat(btreeStats.getBINsByLevel()).containsExactly(11L);
        assertThat(btreeStats.getDINsByLevel()).containsExactly(12L);
        assertThat(btreeStats.getDBINsByLevel()).containsExactly(13L);
        assertThat(btreeStats.toString()).contains("numLeafNodes=7");

        LockStats lockStats = new LockStats();
        lockStats.setNOwners(1);
        lockStats.setNWaiters(2);
        lockStats.setNRequests(3L);
        lockStats.setNWaits(4L);
        assertThat(lockStats.getNOwners()).isEqualTo(1);
        assertThat(lockStats.getNWaiters()).isEqualTo(2);
        assertThat(lockStats.getNRequests()).isEqualTo(3L);
        assertThat(lockStats.getNWaits()).isEqualTo(4L);
        assertThat(lockStats.toString()).contains("nOwners=1");

    }

    @Test
    void publicConfigurationObjectsRoundTripOptions() {
        EnvironmentConfig environment = new EnvironmentConfig(new Properties());
        environment.setLockTimeout(11L);
        environment.setTxnTimeout(12L);
        environment.setLocking(false);
        environment.setTxnSerializableIsolation(true);
        environment.setReadOnly(true);
        environment.setExceptionListener(event -> { });
        environment.setConfigParam("je.env.isTransactional", "true");
        assertThat(environment.getLockTimeout()).isEqualTo(11L);
        assertThat(environment.getReadOnly()).isTrue();
        assertThat(environment.toString()).contains("je.env");

        EnvironmentMutableConfig mutable = new EnvironmentMutableConfig();
        mutable.setCacheSize(1024L);
        assertThat(mutable.getCacheSize()).isZero();
        mutable.setCachePercent(20);
        mutable.setTxnNoSync(true);
        mutable.setTxnWriteNoSync(true);
        mutable.setConfigParam("je.env.runCleaner", "false");
        assertThat(mutable.getCachePercent()).isEqualTo(20);
        assertThat(mutable.getConfigParam("je.env.runCleaner")).isEqualTo("false");
        assertThat(mutable.toString()).contains("je.env");

        DatabaseConfig database = new DatabaseConfig();
        database.setExclusiveCreate(true);
        database.setNodeMaxEntries(17);
        database.setNodeMaxDupTreeEntries(18);
        database.setOverrideBtreeComparator(true);
        database.setOverrideDuplicateComparator(true);
        database.setBtreeComparator(String.CASE_INSENSITIVE_ORDER);
        database.setDuplicateComparator(String.CASE_INSENSITIVE_ORDER);
        assertThat(database.getExclusiveCreate()).isTrue();
        assertThat(database.getNodeMaxEntries()).isEqualTo(17);
        assertThat(database.getNodeMaxDupTreeEntries()).isEqualTo(18);
        assertThat(database.getOverrideBtreeComparator()).isTrue();
        assertThat(database.getOverrideDuplicateComparator()).isTrue();

        SequenceConfig sequence = new SequenceConfig();
        sequence.setDecrement(true);
        sequence.setExclusiveCreate(true);
        sequence.setWrap(true);
        assertThat(sequence.getExclusiveCreate()).isTrue();
        assertThat(sequence.getDecrement()).isTrue();
        assertThat(sequence.getWrap()).isTrue();
        CheckpointConfig checkpoint = new CheckpointConfig();
        checkpoint.setKBytes(4);
        checkpoint.setMinutes(5);
        StatsConfig stats = new StatsConfig();
        stats.setClear(true);
        stats.setShowProgressInterval(6);
        PreloadConfig preload = new PreloadConfig();
        preload.setLoadLNs(true);
        CursorConfig cursor = new CursorConfig();
        cursor.setDirtyRead(true);
        JoinConfig join = new JoinConfig();
        join.setNoSort(true);
        VerifyConfig verify = new VerifyConfig();
        verify.setAggressive(true);
        LogScanConfig scan = new LogScanConfig();
        scan.setForwards(true);
        assertThatThrownBy(scan::cloneConfig).isInstanceOf(ClassCastException.class);
        verify.setPropagateExceptions(true);
        verify.setShowProgressInterval(7);
        assertThat(checkpoint).isNotNull();
        assertThat(stats).isNotNull();
        assertThat(preload).isNotNull();
        assertThat(cursor.getDirtyRead()).isTrue();
        assertThat(join.getNoSort()).isTrue();
        assertThat(verify.getAggressive()).isTrue();
        assertThat(verify.getPropagateExceptions()).isTrue();
        assertThat(OperationStatus.SUCCESS.toString()).isEqualTo("OperationStatus.SUCCESS");
        assertThat(ForeignKeyDeleteAction.NULLIFY.toString()).contains("NULLIFY");
    }

    @Test
    void utilityStateObjectsRenderAndOrderTheirEntries() {
        com.sleepycat.je.utilint.EventTrace.addEvent("state object event");
        com.sleepycat.je.utilint.EventTrace.dumpEvents();
        assertThat(new com.sleepycat.je.utilint.EventTrace("state").toString())
                .contains("state");
        com.sleepycat.je.utilint.LevelOrderedINMap map =
                new com.sleepycat.je.utilint.LevelOrderedINMap();
        com.sleepycat.je.tree.IN in = new com.sleepycat.je.tree.IN();
        map.putIN(in);
        assertThat((java.util.Collection) map.get(0)).contains(in);
    }

    @Test
    void evolutionAndRangeExceptionsCarryTheirUserMessages() {
        assertThat(new com.sleepycat.persist.evolve.DeletedClassException("deleted"))
                .hasMessageContaining("deleted");
        assertThat(new com.sleepycat.persist.evolve.IncompatibleClassException("incompatible"))
                .hasMessageContaining("incompatible");
        assertThat(new com.sleepycat.util.keyrange.KeyRangeException("range"))
                .hasMessageContaining("range");
        assertThat(new com.sleepycat.persist.evolve.EvolveInternal()).isNotNull();
        assertThat(new com.sleepycat.persist.model.ModelInternal()).isNotNull();
    }

    @Test
    void exceptionAndEventObjectsPreserveDiagnosticInformation() {
        Exception cause = new IllegalStateException("cause");
        assertThat(new DatabaseNotFoundException()).isNotNull();
        assertThat(new RunRecoveryException(null)).isNotNull();
        assertThat(new DatabaseNotFoundException(cause).getCause()).isSameAs(cause);
        assertThat(new DatabaseNotFoundException("missing", cause)).hasMessageContaining("missing");
        DeadlockException deadlock = new DeadlockException("deadlock");
        deadlock.setOwnerTxnIds(new long[] {1L, 2L});
        deadlock.setWaiterTxnIds(new long[] {3L});
        assertThat(deadlock.getOwnerTxnIds()).containsExactly(1L, 2L);
        assertThat(deadlock.getWaiterTxnIds()).containsExactly(3L);
        ExceptionEvent event = new ExceptionEvent(cause);
        assertThat(event.getException()).isSameAs(cause);
        assertThat(event.getThreadName()).isNotEmpty();
        assertThat(new LockNotGrantedException("locked")).hasMessageContaining("locked");
        RunRecoveryException recovery = new RunRecoveryException(null, "recovery", cause);
        recovery.setAlreadyThrown();
        assertThat(recovery.toString()).contains("previous exception");
    }
}
