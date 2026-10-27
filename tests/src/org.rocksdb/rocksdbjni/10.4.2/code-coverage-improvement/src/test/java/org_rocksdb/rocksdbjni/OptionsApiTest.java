/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.AdvancedColumnFamilyOptionsInterface;
import org.rocksdb.AdvancedMutableColumnFamilyOptionsInterface;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyOptionsInterface;
import org.rocksdb.CompactionOptionsFIFO;
import org.rocksdb.CompactionOptionsUniversal;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionOptions;
import org.rocksdb.DbPath;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.Logger;
import org.rocksdb.CompressionType;
import org.rocksdb.HashSkipListMemTableConfig;
import org.rocksdb.MemTableConfig;
import org.rocksdb.MutableColumnFamilyOptionsInterface;
import org.rocksdb.Options;
import org.rocksdb.PrepopulateBlobCache;
import org.rocksdb.SstPartitionerFixedPrefixFactory;
import org.rocksdb.StringAppendOperator;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionsApiTest {

    @BeforeAll
    static void loadNative() {
        org.rocksdb.RocksDB.loadLibrary();
    }

    @Test
    void prepareForBulkLoadDisablesAutomaticCompaction() {
        try (Options options = new Options()) {
            assertThat(options.prepareForBulkLoad()).isSameAs(options);
            assertThat(options.disableAutoCompactions()).isTrue();
        }
    }

    @Test
    void optionsExposeDatabaseSettingsThroughTheConcreteOptionsApi() {
        DbPath dbPath = new DbPath(java.nio.file.Path.of("options-db"), 4096);
        try (Options options = new Options();
             org.rocksdb.LRUCache rowCache = new org.rocksdb.LRUCache(4096);
             TestLogger logger = new TestLogger(options)) {
            BlockBasedTableConfig table = new BlockBasedTableConfig();
            assertThat(options.setTableFormatConfig(table)).isSameAs(options);
            assertThat(options.setBottommostCompressionType(CompressionType.NO_COMPRESSION))
                    .isSameAs(options);
            assertThat(options.setCreateIfMissing(true)).isSameAs(options);
            assertThat(options.setDbPaths(Collections.singletonList(dbPath))).isSameAs(options);
            assertThat(options.setMaxTotalWalSize(4096L)).isSameAs(options);
            assertThat(options.setMaxWriteBatchGroupSizeBytes(2048L)).isSameAs(options);
            assertThat(options.setParanoidChecks(true)).isSameAs(options);
            assertThat(options.setPersistStatsToDisk(true)).isSameAs(options);
            assertThat(options.setRecycleLogFileNum(2L)).isSameAs(options);
            assertThat(options.setRowCache(rowCache)).isSameAs(options);
            assertThat(options.setLogger(logger)).isSameAs(options);

            assertThat(options.bottommostCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.createIfMissing()).isTrue();
            assertThat(options.dbPaths()).containsExactly(dbPath);
            assertThat(options.maxTotalWalSize()).isEqualTo(4096L);
            assertThat(options.maxWriteBatchGroupSizeBytes()).isEqualTo(2048L);
            assertThat(options.memTableFactoryName()).isNotBlank();
            assertThat(options.paranoidChecks()).isTrue();
            assertThat(options.persistStatsToDisk()).isTrue();
            assertThat(options.recycleLogFileNum()).isEqualTo(2L);
            assertThat(options.rowCache()).isSameAs(rowCache);
        }
    }

    @Test
    void optionInterfacesConfigureAndReadBackColumnFamilySettings() {
        try (Options options = new Options();
             SstPartitionerFixedPrefixFactory partitioner = new SstPartitionerFixedPrefixFactory(2)) {
            MemTableConfig memTable = new HashSkipListMemTableConfig();
            BlockBasedTableConfig table = new BlockBasedTableConfig();
            StringAppendOperator mergeOperator = new StringAppendOperator(',');
            CompressionOptions compression = new CompressionOptions();
            CompressionOptions bottommostCompression = new CompressionOptions();
            CompactionOptionsFIFO fifo = new CompactionOptionsFIFO();
            CompactionOptionsUniversal universal = new CompactionOptionsUniversal();
            ColumnFamilyOptionsInterface<Options> columnFamily = options;
            AdvancedColumnFamilyOptionsInterface<Options> advanced = options;
            MutableColumnFamilyOptionsInterface<Options> mutable = options;
            AdvancedMutableColumnFamilyOptionsInterface<Options> advancedMutable = options;

            assertThat(columnFamily.setMemTableConfig(memTable)).isSameAs(options);
            assertThat(columnFamily.setTableFormatConfig(table)).isSameAs(options);
            assertThat(mutable.setMaxBytesForLevelBase(111L)).isSameAs(options);
            assertThat(advancedMutable.setMaxBytesForLevelMultiplier(2.5)).isSameAs(options);
            assertThat(advancedMutable.setMaxBytesForLevelMultiplierAdditional(new int[]{3, 4})).isSameAs(options);
            assertThat(advanced.setMaxCompactionBytes(222L)).isSameAs(options);
            assertThat(advancedMutable.setMaxSequentialSkipInIterations(333L)).isSameAs(options);
            assertThat(advancedMutable.setMaxSuccessiveMerges(4L)).isSameAs(options);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(444L)).isSameAs(options);
            assertThat(advancedMutable.setMaxWriteBufferNumber(5)).isSameAs(options);
            assertThat(advancedMutable.setMemtableHugePageSize(555L)).isSameAs(options);
            assertThat(columnFamily.setMemtableMaxRangeDeletions(6)).isSameAs(options);
            assertThat(advancedMutable.setMemtablePrefixBloomSizeRatio(0.25)).isSameAs(options);
            assertThat(advancedMutable.setMemtableWholeKeyFiltering(false)).isSameAs(options);
            assertThat(columnFamily.setMergeOperator(mergeOperator)).isSameAs(options);
            assertThat(advancedMutable.setMinBlobSize(666L)).isSameAs(options);
            assertThat(advanced.setMinWriteBufferNumberToMerge(7)).isSameAs(options);
            assertThat(advanced.setNumLevels(8)).isSameAs(options);
            assertThat(advanced.setOptimizeFiltersForHits(true)).isSameAs(options);
            assertThat(advancedMutable.setParanoidFileChecks(true)).isSameAs(options);
            assertThat(advancedMutable.setPeriodicCompactionSeconds(777L)).isSameAs(options);
            assertThat(advancedMutable.setPrepopulateBlobCache(
                    PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY)).isSameAs(options);
            assertThat(advancedMutable.setReportBgIoStats(true)).isSameAs(options);
            assertThat(advancedMutable.setSoftPendingCompactionBytesLimit(888L)).isSameAs(options);
            assertThat(columnFamily.setSstPartitionerFactory(partitioner)).isSameAs(options);
            assertThat(advancedMutable.setTargetFileSizeBase(999L)).isSameAs(options);
            assertThat(advancedMutable.setTargetFileSizeMultiplier(10)).isSameAs(options);
            assertThat(advancedMutable.setTtl(1000L)).isSameAs(options);
            assertThat(mutable.setWriteBufferSize(1001L)).isSameAs(options);
            assertThat(columnFamily.useCappedPrefixExtractor(2)).isSameAs(options);
            assertThat(columnFamily.useFixedLengthPrefixExtractor(3)).isSameAs(options);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(444L)).isSameAs(options);

            options.setCompactionPriority(CompactionPriority.RoundRobin);
            options.setCompactionStyle(CompactionStyle.FIFO);
            options.setPrepopulateBlobCache(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);
            options.setCompactionOptionsFIFO(fifo);
            options.setCompactionOptionsUniversal(universal);
            options.setCompressionOptions(compression);
            options.setBottommostCompressionOptions(bottommostCompression);
            options.setBottommostCompressionType(CompressionType.NO_COMPRESSION);
            options.setCompressionPerLevel(Arrays.asList(CompressionType.NO_COMPRESSION,
                    CompressionType.SNAPPY_COMPRESSION));
            options.setCompressionType(CompressionType.NO_COMPRESSION);
            options.setCfPaths(Collections.emptyList());
            options.setMemtableMaxRangeDeletions(6);
            options.setArenaBlockSize(100L);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(444L)).isSameAs(options);

            assertThat(options.compactionPriority()).isEqualTo(CompactionPriority.RoundRobin);
            assertThat(options.compactionStyle()).isEqualTo(CompactionStyle.FIFO);
            assertThat(options.prepopulateBlobCache())
                    .isEqualTo(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);
            assertThat(options.memTableConfig()).isNotNull();
            assertThat(options.tableFormatConfig()).isNotNull();
            assertThat(options.compactionOptionsFIFO()).isNotNull();
            assertThat(options.compactionOptionsUniversal()).isNotNull();
            assertThat(options.compressionOptions()).isNotNull();
            assertThat(options.bottommostCompressionOptions()).isNotNull();
            assertThat(options.compressionPerLevel()).containsExactly(
                    CompressionType.NO_COMPRESSION, CompressionType.SNAPPY_COMPRESSION);
            assertThat(options.compressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.cfPaths()).isEmpty();
            assertThat(options.compactionFilter()).isNull();
            assertThat(options.compactionFilterFactory()).isNull();
            assertThat(options.compactionThreadLimiter()).isNull();
            assertThat(options.sstPartitionerFactory()).isNotNull();

            assertThat(options.maxBytesForLevelBase()).isEqualTo(111L);
            assertThat(options.maxBytesForLevelMultiplier()).isEqualTo(2.5);
            assertThat(options.maxBytesForLevelMultiplierAdditional()).containsExactly(3, 4);
            assertThat(options.maxCompactionBytes()).isEqualTo(222L);
            assertThat(options.maxSequentialSkipInIterations()).isEqualTo(333L);
            assertThat(options.maxSuccessiveMerges()).isEqualTo(4L);
            assertThat(options.maxTableFilesSizeFIFO()).isEqualTo(444L);
            assertThat(options.maxWriteBufferNumber()).isEqualTo(5);
            assertThat(options.memtableHugePageSize()).isEqualTo(555L);
            assertThat(options.memtableMaxRangeDeletions()).isEqualTo(6);
            assertThat(options.memtablePrefixBloomSizeRatio()).isEqualTo(0.25);
            assertThat(options.memtableWholeKeyFiltering()).isFalse();
            assertThat(options.minBlobSize()).isEqualTo(666L);
            assertThat(options.minWriteBufferNumberToMerge()).isEqualTo(7);
            assertThat(options.numLevels()).isEqualTo(8);
            assertThat(options.optimizeFiltersForHits()).isTrue();
            assertThat(options.paranoidFileChecks()).isTrue();
            assertThat(options.periodicCompactionSeconds()).isEqualTo(777L);
            assertThat(options.reportBgIoStats()).isTrue();
            assertThat(options.softPendingCompactionBytesLimit()).isEqualTo(888L);
            assertThat(options.targetFileSizeBase()).isEqualTo(999L);
            assertThat(options.targetFileSizeMultiplier()).isEqualTo(10);
            assertThat(options.ttl()).isEqualTo(1000L);
            assertThat(options.writeBufferSize()).isEqualTo(1001L);

            options.setAdviseRandomOnOpen(true);
            options.setAllow2pc(true);
            options.setAllowConcurrentMemtableWrite(false);
            options.setAllowFAllocate(true);
            options.setAllowIngestBehind(true);
            options.setAllowMmapReads(true);
            options.setAllowMmapWrites(true);
            options.setAtomicFlush(true);
            options.setAvoidFlushDuringRecovery(true);
            options.setAvoidFlushDuringShutdown(true);
            options.setAvoidUnnecessaryBlockingIO(true);
            options.setBestEffortsRecovery(true);
            options.setBgerrorResumeRetryInterval(11L);
            options.setBlobCompactionReadaheadSize(12L);
            options.setBlobFileSize(13L);
            options.setBlobFileStartingLevel(14);
            options.setBlobGarbageCollectionAgeCutoff(0.15);
            options.setBlobGarbageCollectionForceThreshold(0.16);
            options.setBloomLocality(17);
            options.setBytesPerSync(18L);
            options.setCompactionReadaheadSize(19L);
            options.setCreateMissingColumnFamilies(true);
            options.setDailyOffpeakTimeUTC("01:00-02:00");
            options.setDbLogDir("db-log");
            options.setDbWriteBufferSize(20L);
            options.setDelayedWriteRate(21L);
            options.setDeleteObsoleteFilesPeriodMicros(22L);
            options.setDisableAutoCompactions(true);
            options.setDumpMallocStats(true);
            options.setEnableBlobFiles(true);
            options.setEnableBlobGarbageCollection(true);
            options.setEnablePipelinedWrite(true);
            options.setEnableThreadTracking(true);
            options.setEnableWriteThreadAdaptiveYield(true);
            options.setErrorIfExists(true);
            options.setExperimentalMempurgeThreshold(0.23);
            options.setForceConsistencyChecks(true);
            options.setHardPendingCompactionBytesLimit(24L);
            options.setInplaceUpdateNumLocks(25L);
            options.setInplaceUpdateSupport(true);
            options.setIsFdCloseOnExec(false);
            options.setKeepLogFileNum(26L);
            options.setLevel0FileNumCompactionTrigger(27);
            options.setLevel0SlowdownWritesTrigger(28);
            options.setLevel0StopWritesTrigger(29);
            options.setLevelCompactionDynamicLevelBytes(true);
            options.setLevelZeroFileNumCompactionTrigger(30);
            options.setLevelZeroSlowdownWritesTrigger(31);
            options.setLevelZeroStopWritesTrigger(32);
            options.setLogFileTimeToRoll(33L);
            options.setLogReadaheadSize(34L);
            options.setManifestPreallocationSize(35L);
            options.setManualWalFlush(true);
            options.setMaxBackgroundCompactions(36);
            options.setMaxBackgroundFlushes(37);
            options.setMaxBackgroundJobs(38);
            options.setMaxBgErrorResumeCount(39);
            options.setMaxFileOpeningThreads(40);
            options.setMaxLogFileSize(41L);
            options.setMaxManifestFileSize(42L);
            options.setMaxSubcompactions(43);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(444L)).isSameAs(options);

            assertThat(options.adviseRandomOnOpen()).isTrue();
            assertThat(options.allow2pc()).isTrue();
            assertThat(options.allowConcurrentMemtableWrite()).isFalse();
            assertThat(options.allowFAllocate()).isTrue();
            assertThat(options.allowIngestBehind()).isTrue();
            assertThat(options.allowMmapReads()).isTrue();
            assertThat(options.allowMmapWrites()).isTrue();
            assertThat(options.arenaBlockSize()).isEqualTo(100L);
            assertThat(options.atomicFlush()).isTrue();
            assertThat(options.avoidFlushDuringRecovery()).isTrue();
            assertThat(options.avoidFlushDuringShutdown()).isTrue();
            assertThat(options.avoidUnnecessaryBlockingIO()).isTrue();
            assertThat(options.bestEffortsRecovery()).isTrue();
            assertThat(options.bgerrorResumeRetryInterval()).isEqualTo(11L);
            assertThat(options.blobCompactionReadaheadSize()).isEqualTo(12L);
            assertThat(options.blobCompressionType()).isNotNull();
            assertThat(options.blobFileSize()).isEqualTo(13L);
            assertThat(options.blobFileStartingLevel()).isEqualTo(14);
            assertThat(options.blobGarbageCollectionAgeCutoff()).isEqualTo(0.15);
            assertThat(options.blobGarbageCollectionForceThreshold()).isEqualTo(0.16);
            assertThat(options.bloomLocality()).isEqualTo(17);
            assertThat(options.bytesPerSync()).isEqualTo(18L);
            assertThat(options.compactionReadaheadSize()).isEqualTo(19L);
            assertThat(options.createMissingColumnFamilies()).isTrue();
            assertThat(options.dailyOffpeakTimeUTC()).isEqualTo("01:00-02:00");
            assertThat(options.dbLogDir()).isEqualTo("db-log");
            assertThat(options.dbWriteBufferSize()).isEqualTo(20L);
            assertThat(options.delayedWriteRate()).isEqualTo(21L);
            assertThat(options.deleteObsoleteFilesPeriodMicros()).isEqualTo(22L);
            assertThat(options.disableAutoCompactions()).isTrue();
            assertThat(options.dumpMallocStats()).isTrue();
            assertThat(options.enableBlobFiles()).isTrue();
            assertThat(options.enableBlobGarbageCollection()).isTrue();
            assertThat(options.enablePipelinedWrite()).isTrue();
            assertThat(options.enableThreadTracking()).isTrue();
            assertThat(options.enableWriteThreadAdaptiveYield()).isTrue();
            assertThat(options.errorIfExists()).isTrue();
            assertThat(options.experimentalMempurgeThreshold()).isEqualTo(0.23);
            try {
                assertThat(options.failIfOptionsFileError()).isTrue();
            } catch (UnsatisfiedLinkError expected) {
                assertThat(expected).hasMessageContaining("failIfOptionsFileError");
            }
            assertThat(options.forceConsistencyChecks()).isTrue();
            assertThat(options.hardPendingCompactionBytesLimit()).isEqualTo(24L);
            assertThat(options.inplaceUpdateNumLocks()).isEqualTo(25L);
            assertThat(options.inplaceUpdateSupport()).isTrue();
            assertThat(options.isFdCloseOnExec()).isFalse();
            assertThat(options.keepLogFileNum()).isEqualTo(26L);
            assertThat(options.level0FileNumCompactionTrigger()).isEqualTo(30);
            assertThat(options.level0SlowdownWritesTrigger()).isEqualTo(31);
            assertThat(options.level0StopWritesTrigger()).isEqualTo(32);
            assertThat(options.levelCompactionDynamicLevelBytes()).isTrue();
            assertThat(options.levelZeroFileNumCompactionTrigger()).isEqualTo(30);
            assertThat(options.levelZeroSlowdownWritesTrigger()).isEqualTo(31);
            assertThat(options.levelZeroStopWritesTrigger()).isEqualTo(32);
            assertThat(options.listeners()).isNotNull();
            assertThat(options.logFileTimeToRoll()).isEqualTo(33L);
            assertThat(options.logReadaheadSize()).isEqualTo(34L);
            assertThat(options.manifestPreallocationSize()).isEqualTo(35L);
            assertThat(options.manualWalFlush()).isTrue();
            assertThat(options.maxBackgroundCompactions()).isEqualTo(36);
            assertThat(options.maxBackgroundFlushes()).isEqualTo(37);
            assertThat(options.maxBackgroundJobs()).isEqualTo(38);
            assertThat(options.maxBgerrorResumeCount()).isEqualTo(39);
            assertThat(options.maxBytesForLevelBase()).isEqualTo(111L);
            assertThat(options.maxBytesForLevelMultiplier()).isEqualTo(2.5);
            assertThat(options.maxCompactionBytes()).isEqualTo(222L);
            assertThat(options.maxFileOpeningThreads()).isEqualTo(40);
            assertThat(options.maxLogFileSize()).isEqualTo(41L);
            assertThat(options.maxManifestFileSize()).isEqualTo(42L);
            assertThat(options.maxSequentialSkipInIterations()).isEqualTo(333L);
            assertThat(options.maxSubcompactions()).isEqualTo(43);
        }
    }

    private static final class TestLogger extends Logger {
        private TestLogger(Options options) {
            super(options);
        }

        @Override
        protected void log(InfoLogLevel level, String message) {
        }
    }
}
