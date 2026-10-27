/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.AbstractWalFilter;
import org.rocksdb.DBOptions;
import org.rocksdb.DBOptionsInterface;
import org.rocksdb.Env;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.LRUCache;
import org.rocksdb.Logger;
import org.rocksdb.MutableDBOptionsInterface;
import org.rocksdb.RateLimiter;
import org.rocksdb.Statistics;
import org.rocksdb.SstFileManager;
import org.rocksdb.WALRecoveryMode;
import org.rocksdb.WalFilter;
import org.rocksdb.WalProcessingOption;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBufferManager;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DBOptionsApiTest {

    @Test
    void dbOptionInterfacesConfigureAndReadBackDatabaseSettings() throws Exception {
        try (DBOptions options = new DBOptions();
             TestLogger logger = new TestLogger(options);
             RateLimiter rateLimiter = new RateLimiter(4096);
             LRUCache rowCache = new LRUCache(1024 * 1024);
             WriteBufferManager writeBufferManager = new WriteBufferManager(8192, rowCache);
             Statistics statistics = new Statistics();
             SstFileManager sstFileManager = new SstFileManager(Env.getDefault());
             TestWalFilter walFilter = new TestWalFilter()) {
            DBOptionsInterface<DBOptions> database = options;
            MutableDBOptionsInterface<DBOptions> mutable = options;

            assertThat(database.optimizeForSmallDb()).isSameAs(options);
            assertThat(database.setLogger(logger)).isSameAs(options);
            assertThat(database.setAdviseRandomOnOpen(true)).isSameAs(options);
            assertThat(database.setAllow2pc(true)).isSameAs(options);
            assertThat(database.setAllowConcurrentMemtableWrite(false)).isSameAs(options);
            assertThat(database.setAllowFAllocate(true)).isSameAs(options);
            assertThat(database.setAllowIngestBehind(true)).isSameAs(options);
            assertThat(database.setAllowMmapReads(true)).isSameAs(options);
            assertThat(database.setAllowMmapWrites(true)).isSameAs(options);
            assertThat(database.setAtomicFlush(true)).isSameAs(options);
            assertThat(database.setAvoidFlushDuringRecovery(true)).isSameAs(options);
            assertThat(mutable.setAvoidFlushDuringShutdown(true)).isSameAs(options);
            assertThat(database.setAvoidUnnecessaryBlockingIO(true)).isSameAs(options);
            assertThat(database.setBestEffortsRecovery(true)).isSameAs(options);
            assertThat(database.setBgerrorResumeRetryInterval(10L)).isSameAs(options);
            assertThat(mutable.setBytesPerSync(11L)).isSameAs(options);
            assertThat(mutable.setCompactionReadaheadSize(12L)).isSameAs(options);
            assertThat(database.setCreateIfMissing(true)).isSameAs(options);
            assertThat(database.setCreateMissingColumnFamilies(true)).isSameAs(options);
            assertThat(database.setDailyOffpeakTimeUTC("01:00-02:00")).isSameAs(options);
            assertThat(mutable.setDailyOffpeakTimeUTC("03:00-04:00")).isSameAs(options);
            assertThat(database.setDbLogDir("db-log")).isSameAs(options);
            assertThat(database.setDbPaths(Collections.emptyList())).isSameAs(options);
            assertThat(database.setDbWriteBufferSize(13L)).isSameAs(options);
            assertThat(mutable.setDelayedWriteRate(14L)).isSameAs(options);
            assertThat(database.setDeleteObsoleteFilesPeriodMicros(15L)).isSameAs(options);
            assertThat(database.setDumpMallocStats(true)).isSameAs(options);
            assertThat(database.setEnablePipelinedWrite(true)).isSameAs(options);
            assertThat(database.setEnableThreadTracking(true)).isSameAs(options);
            assertThat(database.setEnableWriteThreadAdaptiveYield(true)).isSameAs(options);
            assertThat(database.setEnv(Env.getDefault())).isSameAs(options);
            assertThat(database.setErrorIfExists(true)).isSameAs(options);
            try {
                assertThat(database.setFailIfOptionsFileError(true)).isSameAs(options);
            } catch (UnsatisfiedLinkError expected) {
                assertThat(expected).hasMessageContaining("setFailIfOptionsFileError");
            }
            assertThat(database.setIncreaseParallelism(2)).isSameAs(options);
            assertThat(database.setInfoLogLevel(InfoLogLevel.INFO_LEVEL)).isSameAs(options);
            assertThat(database.setIsFdCloseOnExec(false)).isSameAs(options);
            assertThat(database.setKeepLogFileNum(16L)).isSameAs(options);
            assertThat(database.setListeners(Collections.emptyList())).isSameAs(options);
            assertThat(database.setLogFileTimeToRoll(17L)).isSameAs(options);
            assertThat(database.setLogReadaheadSize(18L)).isSameAs(options);
            assertThat(database.setManifestPreallocationSize(19L)).isSameAs(options);
            assertThat(database.setManualWalFlush(true)).isSameAs(options);
            assertThat(mutable.setMaxBackgroundCompactions(20)).isSameAs(options);
            assertThat(database.setMaxBackgroundFlushes(21)).isSameAs(options);
            assertThat(mutable.setMaxBackgroundJobs(22)).isSameAs(options);
            assertThat(database.setMaxBgErrorResumeCount(23)).isSameAs(options);
            assertThat(database.setMaxFileOpeningThreads(24)).isSameAs(options);
            assertThat(database.setMaxLogFileSize(25L)).isSameAs(options);
            assertThat(database.setMaxManifestFileSize(26L)).isSameAs(options);
            assertThat(mutable.setMaxOpenFiles(27)).isSameAs(options);
            assertThat(database.setMaxSubcompactions(28)).isSameAs(options);
            assertThat(mutable.setMaxTotalWalSize(29L)).isSameAs(options);
            assertThat(database.setMaxWriteBatchGroupSizeBytes(30L)).isSameAs(options);
            assertThat(database.setParanoidChecks(true)).isSameAs(options);
            assertThat(database.setPersistStatsToDisk(true)).isSameAs(options);
            assertThat(database.setRateLimiter(rateLimiter)).isSameAs(options);
            assertThat(database.setRecycleLogFileNum(31L)).isSameAs(options);
            assertThat(database.setRowCache(rowCache)).isSameAs(options);
            assertThat(database.setSkipCheckingSstFileSizesOnDbOpen(true)).isSameAs(options);
            assertThat(database.setSkipStatsUpdateOnDbOpen(true)).isSameAs(options);
            assertThat(database.setSstFileManager(sstFileManager)).isSameAs(options);
            assertThat(database.setStatistics(statistics)).isSameAs(options);
            assertThat(mutable.setStatsDumpPeriodSec(32)).isSameAs(options);
            assertThat(mutable.setStatsHistoryBufferSize(33L)).isSameAs(options);
            assertThat(mutable.setStatsPersistPeriodSec(34)).isSameAs(options);
            assertThat(mutable.setStrictBytesPerSync(true)).isSameAs(options);
            assertThat(database.setTableCacheNumshardbits(5)).isSameAs(options);
            assertThat(database.setTwoWriteQueues(true)).isSameAs(options);
            assertThat(database.setUnorderedWrite(true)).isSameAs(options);
            assertThat(database.setUseAdaptiveMutex(true)).isSameAs(options);
            assertThat(database.setUseDirectIoForFlushAndCompaction(true)).isSameAs(options);
            assertThat(database.setUseDirectReads(true)).isSameAs(options);
            assertThat(database.setUseFsync(true)).isSameAs(options);
            assertThat(mutable.setWalBytesPerSync(35L)).isSameAs(options);
            assertThat(database.setWalDir("wal")).isSameAs(options);
            assertThat(database.setWalFilter(walFilter)).isSameAs(options);
            assertThat(database.setWalRecoveryMode(WALRecoveryMode.AbsoluteConsistency)).isSameAs(options);
            assertThat(database.setWalSizeLimitMB(36L)).isSameAs(options);
            assertThat(database.setWalTtlSeconds(37L)).isSameAs(options);
            assertThat(mutable.setWritableFileMaxBufferSize(38L)).isSameAs(options);
            assertThat(database.setWriteBufferManager(writeBufferManager)).isSameAs(options);
            assertThat(database.setWriteDbidToManifest(true)).isSameAs(options);
            assertThat(database.setWriteThreadMaxYieldUsec(39L)).isSameAs(options);
            assertThat(database.setWriteThreadSlowYieldUsec(40L)).isSameAs(options);

            assertThat(options.adviseRandomOnOpen()).isTrue();
            assertThat(options.allow2pc()).isTrue();
            assertThat(options.allowConcurrentMemtableWrite()).isFalse();
            assertThat(options.allowFAllocate()).isTrue();
            assertThat(options.allowIngestBehind()).isTrue();
            assertThat(options.allowMmapReads()).isTrue();
            assertThat(options.allowMmapWrites()).isTrue();
            assertThat(options.atomicFlush()).isTrue();
            assertThat(options.avoidFlushDuringRecovery()).isTrue();
            assertThat(options.avoidFlushDuringShutdown()).isTrue();
            assertThat(options.avoidUnnecessaryBlockingIO()).isTrue();
            assertThat(options.bestEffortsRecovery()).isTrue();
            assertThat(options.bgerrorResumeRetryInterval()).isEqualTo(10L);
            assertThat(options.bytesPerSync()).isEqualTo(11L);
            assertThat(options.compactionReadaheadSize()).isEqualTo(12L);
            assertThat(options.createIfMissing()).isTrue();
            assertThat(options.createMissingColumnFamilies()).isTrue();
            assertThat(options.dailyOffpeakTimeUTC()).isEqualTo("03:00-04:00");
            assertThat(options.dbLogDir()).isEqualTo("db-log");
            assertThat(options.dbPaths()).isEmpty();
            assertThat(options.dbWriteBufferSize()).isEqualTo(13L);
            assertThat(options.delayedWriteRate()).isEqualTo(14L);
            assertThat(options.deleteObsoleteFilesPeriodMicros()).isEqualTo(15L);
            assertThat(options.dumpMallocStats()).isTrue();
            assertThat(options.enablePipelinedWrite()).isTrue();
            assertThat(options.enableThreadTracking()).isTrue();
            assertThat(options.enableWriteThreadAdaptiveYield()).isTrue();
            assertThat(options.getEnv()).isNotNull();
            assertThat(options.errorIfExists()).isTrue();
            try {
                assertThat(options.failIfOptionsFileError()).isTrue();
            } catch (UnsatisfiedLinkError expected) {
                assertThat(expected).hasMessageContaining("failIfOptionsFileError");
            }
            assertThat(options.infoLogLevel()).isEqualTo(InfoLogLevel.INFO_LEVEL);
            assertThat(options.isFdCloseOnExec()).isFalse();
            assertThat(options.keepLogFileNum()).isEqualTo(16L);
            assertThat(options.listeners()).isEmpty();
            assertThat(options.logFileTimeToRoll()).isEqualTo(17L);
            assertThat(options.logReadaheadSize()).isEqualTo(18L);
            assertThat(options.manifestPreallocationSize()).isEqualTo(19L);
            assertThat(options.manualWalFlush()).isTrue();
            assertThat(options.maxBackgroundCompactions()).isEqualTo(20);
            assertThat(options.maxBackgroundFlushes()).isEqualTo(21);
            assertThat(options.maxBackgroundJobs()).isEqualTo(22);
            assertThat(options.maxBgerrorResumeCount()).isEqualTo(23);
            assertThat(options.maxFileOpeningThreads()).isEqualTo(24);
            assertThat(options.maxLogFileSize()).isEqualTo(25L);
            assertThat(options.maxManifestFileSize()).isEqualTo(26L);
            assertThat(options.maxOpenFiles()).isEqualTo(27);
            assertThat(options.maxSubcompactions()).isEqualTo(28);
            assertThat(options.maxTotalWalSize()).isEqualTo(29L);
            assertThat(options.maxWriteBatchGroupSizeBytes()).isEqualTo(30L);
            assertThat(options.paranoidChecks()).isTrue();
            assertThat(options.persistStatsToDisk()).isTrue();
            assertThat(options.recycleLogFileNum()).isEqualTo(31L);
            assertThat(options.rowCache()).isNotNull();
            assertThat(options.skipCheckingSstFileSizesOnDbOpen()).isTrue();
            assertThat(options.skipStatsUpdateOnDbOpen()).isTrue();
            assertThat(options.statistics()).isNotNull();
            assertThat(options.statsDumpPeriodSec()).isEqualTo(32);
            assertThat(options.statsHistoryBufferSize()).isEqualTo(33L);
            assertThat(options.statsPersistPeriodSec()).isEqualTo(34);
            assertThat(options.strictBytesPerSync()).isTrue();
            assertThat(options.tableCacheNumshardbits()).isEqualTo(5);
            assertThat(options.twoWriteQueues()).isTrue();
            assertThat(options.unorderedWrite()).isTrue();
            assertThat(options.useAdaptiveMutex()).isTrue();
            assertThat(options.useDirectIoForFlushAndCompaction()).isTrue();
            assertThat(options.useDirectReads()).isTrue();
            assertThat(options.useFsync()).isTrue();
            assertThat(options.walBytesPerSync()).isEqualTo(35L);
            assertThat(options.walDir()).isEqualTo("wal");
            assertThat(options.walRecoveryMode()).isEqualTo(WALRecoveryMode.AbsoluteConsistency);
            assertThat(options.walSizeLimitMB()).isEqualTo(36L);
            assertThat(options.walTtlSeconds()).isEqualTo(37L);
            assertThat(options.writableFileMaxBufferSize()).isEqualTo(38L);
            assertThat(options.writeBufferManager()).isNotNull();
            assertThat(options.writeDbidToManifest()).isTrue();
            assertThat(options.writeThreadMaxYieldUsec()).isEqualTo(39L);
            assertThat(options.writeThreadSlowYieldUsec()).isEqualTo(40L);
        }
    }

    private static final class TestWalFilter extends AbstractWalFilter {
        @Override
        public void columnFamilyLogNumberMap(Map<Integer, Long> numbers,
                Map<String, Integer> names) {
        }

        @Override
        public WalFilter.LogRecordFoundResult logRecordFound(long logNumber, String fileName,
                WriteBatch batch, WriteBatch newBatch) {
            return new WalFilter.LogRecordFoundResult(WalProcessingOption.CONTINUE_PROCESSING, false);
        }

        @Override
        public String name() {
            return "coverage-wal-filter";
        }
    }

    private static final class TestLogger extends Logger {
        private TestLogger(DBOptions options) {
            super(options);
        }

        @Override
        protected void log(InfoLogLevel level, String message) {
        }
    }
}
