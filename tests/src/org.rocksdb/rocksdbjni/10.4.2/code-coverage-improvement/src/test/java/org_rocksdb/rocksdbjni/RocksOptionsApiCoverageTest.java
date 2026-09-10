/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.AbstractCompactionFilter;
import org.rocksdb.AbstractCompactionFilterFactory;
import org.rocksdb.AbstractComparator;
import org.rocksdb.AbstractEventListener;
import org.rocksdb.AbstractWalFilter;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BuiltinComparator;
import org.rocksdb.Cache;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionOptionsFIFO;
import org.rocksdb.CompactionOptionsUniversal;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionOptions;
import org.rocksdb.ConfigOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.ConcurrentTaskLimiterImpl;
import org.rocksdb.DBOptions;
import org.rocksdb.DbPath;
import org.rocksdb.HashLinkedListMemTableConfig;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.Logger;
import org.rocksdb.MutableColumnFamilyOptions;
import org.rocksdb.MutableDBOptions;
import org.rocksdb.OptionString;
import org.rocksdb.Options;
import org.rocksdb.PrepopulateBlobCache;
import org.rocksdb.RateLimiter;
import org.rocksdb.RocksDB;
import org.rocksdb.SstPartitionerFixedPrefixFactory;
import org.rocksdb.Statistics;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.TablePropertiesCollectorFactory;
import org.rocksdb.WALRecoveryMode;
import org.rocksdb.WriteBatch;
import org.rocksdb.WalFilter;
import org.rocksdb.WalProcessingOption;
import org.rocksdb.WriteBufferManager;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RocksOptionsApiCoverageTest {

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void optionsExposeEnvironmentAndDefaultValueObjects() {
        try (Options options = new Options().setCreateIfMissing(true);
             Options copy = new Options(options)) {
            assertThat(options.getEnv()).isSameAs(org.rocksdb.Env.getDefault());
            assertThat(copy.createIfMissing()).isTrue();
            assertThat(new OptionString()).isNotNull();
        }
    }

    @Test
    void columnFamilyConfigurationRoundTripsThroughPublicApi() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")),
                "RocksDB callback comparators are not supported in native-image tests");
        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig();
        HashLinkedListMemTableConfig memTableConfig = new HashLinkedListMemTableConfig();
        try (ColumnFamilyOptions options = new ColumnFamilyOptions();
             Options fullOptions = new Options();
             CompressionOptions compressionOptions = new CompressionOptions();
             CompactionOptionsFIFO fifo = new CompactionOptionsFIFO();
             CompactionOptionsUniversal universal = new CompactionOptionsUniversal();
             ConcurrentTaskLimiterImpl limiter = new ConcurrentTaskLimiterImpl("coverage", 2);
             SstPartitionerFixedPrefixFactory partitioner = new SstPartitionerFixedPrefixFactory(2)) {
            AbstractComparator comparator = new CoverageComparator();
            AbstractCompactionFilter<?> filter = new org.rocksdb.RemoveEmptyValueCompactionFilter();
            AbstractCompactionFilterFactory<?> filterFactory = new CoverageFilterFactory();
            Cache cache = new org.rocksdb.LRUCache(1024 * 1024);
            List<DbPath> paths = Collections.singletonList(new DbPath(tempDir.resolve("cf"), 4096));

            assertThat(options.optimizeForSmallDb()).isSameAs(options);
            assertThat(options.optimizeForSmallDb(cache)).isSameAs(options);
            assertThat(options.optimizeForPointLookup(4)).isSameAs(options);
            assertThat(options.optimizeLevelStyleCompaction()).isSameAs(options);
            assertThat(options.optimizeLevelStyleCompaction(4096)).isSameAs(options);
            assertThat(options.optimizeUniversalStyleCompaction()).isSameAs(options);
            assertThat(options.optimizeUniversalStyleCompaction(4096)).isSameAs(options);
            assertThat(options.oldDefaults(7, 8)).isSameAs(options);
            assertThat(options.setTableFormatConfig(tableConfig)).isSameAs(options);
            assertThat(options.setMemTableConfig(memTableConfig)).isSameAs(options);
            assertThat(options.setComparator(comparator)).isSameAs(options);
            assertThat(options.setComparator(BuiltinComparator.REVERSE_BYTEWISE_COMPARATOR)).isSameAs(options);
            assertThat(options.setMergeOperatorName("stringappend")).isSameAs(options);
            assertThat(options.setMergeOperator(new StringAppendOperator())).isSameAs(options);
            assertThat(options.useFixedLengthPrefixExtractor(2)).isSameAs(options);
            assertThat(options.useCappedPrefixExtractor(3)).isSameAs(options);
            assertThat(options.setCompactionFilter(filter)).isSameAs(options);
            assertThat(options.setCompactionFilterFactory(filterFactory)).isSameAs(options);
            assertThat(options.setCompactionStyle(CompactionStyle.LEVEL)).isSameAs(options);
            assertThat(options.setCompactionPriority(CompactionPriority.RoundRobin)).isSameAs(options);
            assertThat(options.setCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(options);
            assertThat(options.setCompressionPerLevel(Arrays.asList(
                    CompressionType.NO_COMPRESSION, CompressionType.LZ4_COMPRESSION))).isSameAs(options);
            assertThat(options.setBottommostCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(options);
            assertThat(options.setBottommostCompressionOptions(compressionOptions)).isSameAs(options);
            assertThat(options.setCompressionOptions(compressionOptions)).isSameAs(options);
            assertThat(options.setCompactionOptionsFIFO(fifo)).isSameAs(options);
            assertThat(options.setCompactionOptionsUniversal(universal)).isSameAs(options);
            assertThat(options.setCompactionThreadLimiter(limiter)).isSameAs(options);
            assertThat(options.setSstPartitionerFactory(partitioner)).isSameAs(options);
            assertThat(options.setCfPaths(paths)).isSameAs(options);
            assertThat(options.setWriteBufferSize(8192)).isSameAs(options);
            assertThat(options.setMaxWriteBufferNumber(3)).isSameAs(options);
            assertThat(options.setMinWriteBufferNumberToMerge(2)).isSameAs(options);
            assertThat(options.setLevelZeroFileNumCompactionTrigger(4)).isSameAs(options);
            assertThat(options.setLevelZeroSlowdownWritesTrigger(8)).isSameAs(options);
            assertThat(options.setLevelZeroStopWritesTrigger(12)).isSameAs(options);
            assertThat(options.setLevel0FileNumCompactionTrigger(5)).isSameAs(options);
            assertThat(options.setLevel0SlowdownWritesTrigger(9)).isSameAs(options);
            assertThat(options.setLevel0StopWritesTrigger(13)).isSameAs(options);
            assertThat(options.setMaxBytesForLevelBase(32768)).isSameAs(options);
            assertThat(options.setMaxBytesForLevelMultiplier(4.0)).isSameAs(options);
            assertThat(options.setMaxBytesForLevelMultiplierAdditional(new int[] {1, 2, 3})).isSameAs(options);
            assertThat(options.setMaxCompactionBytes(65536)).isSameAs(options);
            assertThat(options.setMaxTableFilesSizeFIFO(131072)).isSameAs(options);
            assertThat(options.setMaxSequentialSkipInIterations(7)).isSameAs(options);
            assertThat(options.setArenaBlockSize(4096)).isSameAs(options);
            assertThat(options.setMemtableHugePageSize(0)).isSameAs(options);
            assertThat(options.setMemtableMaxRangeDeletions(10)).isSameAs(options);
            assertThat(options.setMemtablePrefixBloomSizeRatio(0.5)).isSameAs(options);
            assertThat(options.setMemtableWholeKeyFiltering(true)).isSameAs(options);
            assertThat(options.setExperimentalMempurgeThreshold(0.25)).isSameAs(options);
            assertThat(options.setInplaceUpdateSupport(true)).isSameAs(options);
            assertThat(options.setInplaceUpdateNumLocks(8)).isSameAs(options);
            assertThat(options.setOptimizeFiltersForHits(true)).isSameAs(options);
            assertThat(options.setBloomLocality(1)).isSameAs(options);
            assertThat(options.setMinWriteBufferNumberToMerge(2)).isSameAs(options);
            assertThat(options.setNumLevels(4)).isSameAs(options);
            assertThat(options.setLevelCompactionDynamicLevelBytes(true)).isSameAs(options);
            assertThat(options.setForceConsistencyChecks(true)).isSameAs(options);
            assertThat(options.setDisableAutoCompactions(true)).isSameAs(options);
            assertThat(options.setParanoidFileChecks(true)).isSameAs(options);
            assertThat(options.setMaxSuccessiveMerges(4)).isSameAs(options);
            assertThat(options.setSoftPendingCompactionBytesLimit(100000)).isSameAs(options);
            assertThat(options.setHardPendingCompactionBytesLimit(200000)).isSameAs(options);
            assertThat(options.setTargetFileSizeBase(16384)).isSameAs(options);
            assertThat(options.setTargetFileSizeMultiplier(2)).isSameAs(options);
            assertThat(options.setEnableBlobFiles(true)).isSameAs(options);
            assertThat(options.setEnableBlobGarbageCollection(true)).isSameAs(options);
            assertThat(options.setMinBlobSize(1024)).isSameAs(options);
            assertThat(options.setBlobFileSize(65536)).isSameAs(options);
            assertThat(options.setBlobCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(options);
            assertThat(options.setBlobFileStartingLevel(1)).isSameAs(options);
            assertThat(options.setBlobCompactionReadaheadSize(4096)).isSameAs(options);
            assertThat(options.setBlobGarbageCollectionAgeCutoff(0.5)).isSameAs(options);
            assertThat(options.setBlobGarbageCollectionForceThreshold(0.5)).isSameAs(options);
            assertThat(options.setPrepopulateBlobCache(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY))
                    .isSameAs(options);
            assertThat(options.setTtl(60)).isSameAs(options);
            assertThat(options.setPeriodicCompactionSeconds(60)).isSameAs(options);
            assertThat(options.setReportBgIoStats(true)).isSameAs(options);

            assertThat(options.cfPaths()).containsExactly(new DbPath(tempDir.resolve("cf"), 4096));
            assertThat(options.sstPartitionerFactory()).isNotNull();
            assertThat(options.tableFactoryName()).isNotBlank();
            assertThat(options.tableFormatConfig()).isNotNull();
            assertThat(options.arenaBlockSize()).isEqualTo(4096);
            assertThat(options.blobCompactionReadaheadSize()).isEqualTo(4096);
            assertThat(options.blobCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.blobFileSize()).isEqualTo(65536);
            assertThat(options.blobFileStartingLevel()).isEqualTo(1);
            assertThat(options.blobGarbageCollectionAgeCutoff()).isEqualTo(0.5);
            assertThat(options.blobGarbageCollectionForceThreshold()).isEqualTo(0.5);
            assertThat(options.bloomLocality()).isEqualTo(1);
            assertThat(options.bottommostCompressionOptions()).isNotNull();
            assertThat(options.compactionFilter()).isNotNull();
            assertThat(options.compactionFilterFactory()).isNotNull();
            assertThat(options.compactionOptionsFIFO()).isNotNull();
            assertThat(options.compactionOptionsUniversal()).isNotNull();
            assertThat(options.compactionThreadLimiter()).isNotNull();
            assertThat(options.compressionOptions()).isNotNull();
            assertThat(options.compressionPerLevel()).containsExactly(CompressionType.NO_COMPRESSION,
                    CompressionType.LZ4_COMPRESSION);
            assertThat(options.compressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.disableAutoCompactions()).isTrue();
            assertThat(options.enableBlobGarbageCollection()).isTrue();
            assertThat(options.experimentalMempurgeThreshold()).isEqualTo(0.25);
            assertThat(options.forceConsistencyChecks()).isTrue();
            assertThat(options.hardPendingCompactionBytesLimit()).isEqualTo(200000);
            assertThat(options.inplaceUpdateNumLocks()).isEqualTo(8);
            assertThat(options.inplaceUpdateSupport()).isTrue();
            assertThat(options.level0FileNumCompactionTrigger()).isEqualTo(5);
            assertThat(options.level0SlowdownWritesTrigger()).isEqualTo(9);
            assertThat(options.level0StopWritesTrigger()).isEqualTo(13);
            assertThat(options.levelCompactionDynamicLevelBytes()).isTrue();
            assertThat(options.levelZeroFileNumCompactionTrigger()).isPositive();
            assertThat(options.levelZeroSlowdownWritesTrigger()).isPositive();
            assertThat(options.levelZeroStopWritesTrigger()).isPositive();
            assertThat(options.maxBytesForLevelBase()).isEqualTo(32768);
            assertThat(options.maxBytesForLevelMultiplier()).isEqualTo(4.0);
            assertThat(options.maxBytesForLevelMultiplierAdditional()).containsExactly(1, 2, 3);
            assertThat(options.maxCompactionBytes()).isEqualTo(65536);
            assertThat(options.maxSequentialSkipInIterations()).isEqualTo(7);
            assertThat(options.maxSuccessiveMerges()).isEqualTo(4);
            assertThat(options.maxTableFilesSizeFIFO()).isEqualTo(131072);
            assertThat(options.maxWriteBufferNumber()).isEqualTo(3);
            assertThat(options.memTableConfig()).isNotNull();
            assertThat(options.memTableFactoryName()).isNotBlank();
            assertThat(options.memtableHugePageSize()).isZero();
            assertThat(options.memtableMaxRangeDeletions()).isEqualTo(10);
            assertThat(options.memtablePrefixBloomSizeRatio()).isEqualTo(0.5);
            assertThat(options.minBlobSize()).isEqualTo(1024);
            assertThat(options.minWriteBufferNumberToMerge()).isEqualTo(2);
            assertThat(options.optimizeFiltersForHits()).isTrue();
            assertThat(options.paranoidFileChecks()).isTrue();
            assertThat(options.periodicCompactionSeconds()).isEqualTo(60);
            assertThat(options.reportBgIoStats()).isTrue();
            assertThat(options.compactionPriority()).isEqualTo(CompactionPriority.RoundRobin);
            assertThat(options.compactionStyle()).isEqualTo(CompactionStyle.LEVEL);
            assertThat(options.bottommostCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.prepopulateBlobCache())
                    .isEqualTo(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);
            assertThat(options.writeBufferSize()).isEqualTo(8192);
            assertThat(options.numLevels()).isEqualTo(4);
            assertThat(options.enableBlobFiles()).isTrue();
            assertThat(options.memtableWholeKeyFiltering()).isTrue();
            assertThat(options.softPendingCompactionBytesLimit()).isEqualTo(100000);
            assertThat(options.targetFileSizeBase()).isEqualTo(16384);
            assertThat(options.targetFileSizeMultiplier()).isEqualTo(2);
            assertThat(options.ttl()).isEqualTo(60);

            assertThat(fullOptions.optimizeForPointLookup(4)).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeForSmallDb()).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeForSmallDb(cache)).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeLevelStyleCompaction()).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeLevelStyleCompaction(4096)).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeUniversalStyleCompaction()).isSameAs(fullOptions);
            assertThat(fullOptions.optimizeUniversalStyleCompaction(4096)).isSameAs(fullOptions);
            assertThat(fullOptions.oldDefaults(7, 8)).isSameAs(fullOptions);
            assertThat(fullOptions.setAdviseRandomOnOpen(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllow2pc(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllowConcurrentMemtableWrite(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllowFAllocate(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllowIngestBehind(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllowMmapReads(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAllowMmapWrites(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setArenaBlockSize(4096)).isSameAs(fullOptions);
            assertThat(fullOptions.setAtomicFlush(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAvoidFlushDuringRecovery(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAvoidFlushDuringShutdown(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setAvoidUnnecessaryBlockingIO(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setBestEffortsRecovery(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setBgerrorResumeRetryInterval(1000)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobCompactionReadaheadSize(128)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobFileSize(4096)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobFileStartingLevel(1)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobGarbageCollectionAgeCutoff(0.4)).isSameAs(fullOptions);
            assertThat(fullOptions.setBlobGarbageCollectionForceThreshold(0.6)).isSameAs(fullOptions);
            assertThat(fullOptions.setBloomLocality(1)).isSameAs(fullOptions);
            assertThat(fullOptions.setBottommostCompressionOptions(compressionOptions)).isSameAs(fullOptions);
            assertThat(fullOptions.setBottommostCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(fullOptions);
            assertThat(fullOptions.setBytesPerSync(256)).isSameAs(fullOptions);
            assertThat(fullOptions.setCfPaths(paths)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionFilter(filter)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionFilterFactory(filterFactory)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionOptionsFIFO(fifo)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionOptionsUniversal(universal)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionPriority(CompactionPriority.RoundRobin)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionReadaheadSize(256)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionStyle(CompactionStyle.LEVEL)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompactionThreadLimiter(limiter)).isSameAs(fullOptions);
            assertThat(fullOptions.setComparator(comparator)).isSameAs(fullOptions);
            assertThat(fullOptions.setComparator(BuiltinComparator.BYTEWISE_COMPARATOR)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompressionOptions(compressionOptions)).isSameAs(fullOptions);
            assertThat(fullOptions.setCompressionPerLevel(Arrays.asList(CompressionType.NO_COMPRESSION)))
                    .isSameAs(fullOptions);
            assertThat(fullOptions.setCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(fullOptions);
            assertThat(fullOptions.setCreateMissingColumnFamilies(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setDailyOffpeakTimeUTC("01:00-05:00")).isSameAs(fullOptions);
            assertThat(fullOptions.setDbLogDir(tempDir.resolve("option-logs").toString())).isSameAs(fullOptions);
            assertThat(fullOptions.setDbPaths(paths)).isSameAs(fullOptions);
            assertThat(fullOptions.setDbWriteBufferSize(4096)).isSameAs(fullOptions);
            assertThat(fullOptions.setDelayedWriteRate(256)).isSameAs(fullOptions);
            assertThat(fullOptions.setDeleteObsoleteFilesPeriodMicros(100)).isSameAs(fullOptions);
            assertThat(fullOptions.setDisableAutoCompactions(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setDumpMallocStats(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnableBlobFiles(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnableBlobGarbageCollection(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnablePipelinedWrite(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnableThreadTracking(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnableWriteThreadAdaptiveYield(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setEnv(org.rocksdb.Env.getDefault())).isSameAs(fullOptions);
            assertThat(fullOptions.setErrorIfExists(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setExperimentalMempurgeThreshold(0.3)).isSameAs(fullOptions);
            try {
                assertThat(fullOptions.setFailIfOptionsFileError(true)).isSameAs(fullOptions);
            } catch (UnsatisfiedLinkError ignored) {
                // The older native test library does not export this newer setter.
            }
            assertThat(fullOptions.setForceConsistencyChecks(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setHardPendingCompactionBytesLimit(200000)).isSameAs(fullOptions);
            assertThat(fullOptions.setIncreaseParallelism(2)).isSameAs(fullOptions);
            assertThat(fullOptions.setInfoLogLevel(InfoLogLevel.INFO_LEVEL)).isSameAs(fullOptions);
            assertThat(fullOptions.setInplaceUpdateNumLocks(2)).isSameAs(fullOptions);
            assertThat(fullOptions.setInplaceUpdateSupport(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setIsFdCloseOnExec(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setKeepLogFileNum(2)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevel0FileNumCompactionTrigger(4)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevel0SlowdownWritesTrigger(8)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevel0StopWritesTrigger(12)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevelCompactionDynamicLevelBytes(true)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevelZeroFileNumCompactionTrigger(4)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevelZeroSlowdownWritesTrigger(8)).isSameAs(fullOptions);
            assertThat(fullOptions.setLevelZeroStopWritesTrigger(12)).isSameAs(fullOptions);
            assertThat(cache.getUsage()).isZero();
            cache.close();
        }
    }

    @Test
    void optionsAndDbOptionsExposeConfiguredValues() throws Exception {
        try (Statistics statistics = new Statistics();
             RateLimiter rateLimiter = new RateLimiter(4096);
             org.rocksdb.LRUCache rowCache = new org.rocksdb.LRUCache(4096);
             WriteBufferManager writeBufferManager = new WriteBufferManager(8192, rowCache);
             DBOptions dbOptions = new DBOptions();
             Options options = new Options(dbOptions, new ColumnFamilyOptions());
             CoverageLogger logger = new CoverageLogger(dbOptions);
             CoverageLogger optionsLogger = new CoverageLogger(options)) {
            logger.setInfoLogLevel(InfoLogLevel.DEBUG_LEVEL);
            assertThat(logger.infoLogLevel()).isEqualTo(InfoLogLevel.DEBUG_LEVEL);
            assertThat(dbOptions.setLogger(logger)).isSameAs(dbOptions);
            assertThat(options.setLogger(optionsLogger)).isSameAs(options);
            assertThat(dbOptions.setCreateIfMissing(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setCreateMissingColumnFamilies(true)).isSameAs(dbOptions);
            assertThat(dbOptions.optimizeForSmallDb()).isSameAs(dbOptions);
            assertThat(dbOptions.setWritableFileMaxBufferSize(2048)).isSameAs(dbOptions);
            assertThat(optionsLogger.infoLogLevel()).isEqualTo(InfoLogLevel.INFO_LEVEL);
            assertThat(dbOptions.setListeners(Collections.singletonList(new CoverageEventListener()))).isSameAs(dbOptions);
            assertThat(dbOptions.setEnv(org.rocksdb.Env.getDefault())).isSameAs(dbOptions);
            assertThat(dbOptions.setSstFileManager(new org.rocksdb.SstFileManager(org.rocksdb.Env.getDefault())))
                    .isSameAs(dbOptions);
            assertThat(dbOptions.setWalFilter(new CoverageWalFilter())).isSameAs(dbOptions);
            assertThat(dbOptions.setAdviseRandomOnOpen(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllow2pc(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllowConcurrentMemtableWrite(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllowFAllocate(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllowIngestBehind(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllowMmapReads(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAllowMmapWrites(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAtomicFlush(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAvoidFlushDuringRecovery(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAvoidFlushDuringShutdown(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setAvoidUnnecessaryBlockingIO(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setBestEffortsRecovery(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setBgerrorResumeRetryInterval(1000)).isSameAs(dbOptions);
            assertThat(dbOptions.setBytesPerSync(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setCompactionReadaheadSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setDailyOffpeakTimeUTC("01:00-05:00")).isSameAs(dbOptions);
            assertThat(dbOptions.setDbLogDir(tempDir.resolve("logs").toString())).isSameAs(dbOptions);
            assertThat(dbOptions.setDbPaths(Collections.singletonList(new DbPath(tempDir.resolve("db"), 4096))))
                    .isSameAs(dbOptions);
            assertThat(dbOptions.setDbWriteBufferSize(16384)).isSameAs(dbOptions);
            assertThat(dbOptions.setDelayedWriteRate(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setDeleteObsoleteFilesPeriodMicros(1000)).isSameAs(dbOptions);
            assertThat(dbOptions.setDumpMallocStats(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setEnablePipelinedWrite(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setEnableThreadTracking(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setEnableWriteThreadAdaptiveYield(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setErrorIfExists(true)).isSameAs(dbOptions);
            try {
                assertThat(dbOptions.setFailIfOptionsFileError(true)).isSameAs(dbOptions);
            } catch (UnsatisfiedLinkError ignored) {
                // The older native test library does not export this newer setter.
            }
            assertThat(dbOptions.setIncreaseParallelism(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setInfoLogLevel(InfoLogLevel.INFO_LEVEL)).isSameAs(dbOptions);
            assertThat(dbOptions.setIsFdCloseOnExec(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setKeepLogFileNum(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setLogFileTimeToRoll(60)).isSameAs(dbOptions);
            assertThat(dbOptions.setLogReadaheadSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setManifestPreallocationSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setManualWalFlush(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxBackgroundCompactions(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxBackgroundFlushes(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxBackgroundJobs(4)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxBgErrorResumeCount(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxFileOpeningThreads(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxLogFileSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxManifestFileSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxOpenFiles(32)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxSubcompactions(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxTotalWalSize(8192)).isSameAs(dbOptions);
            assertThat(dbOptions.setMaxWriteBatchGroupSizeBytes(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setParanoidChecks(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setPersistStatsToDisk(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setRateLimiter(rateLimiter)).isSameAs(dbOptions);
            assertThat(dbOptions.setRecycleLogFileNum(2)).isSameAs(dbOptions);
            assertThat(dbOptions.setRowCache(rowCache)).isSameAs(dbOptions);
            assertThat(dbOptions.setSkipCheckingSstFileSizesOnDbOpen(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setSkipStatsUpdateOnDbOpen(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setStatistics(statistics)).isSameAs(dbOptions);
            assertThat(dbOptions.setStatsDumpPeriodSec(1)).isSameAs(dbOptions);
            assertThat(dbOptions.setStatsHistoryBufferSize(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setStatsPersistPeriodSec(1)).isSameAs(dbOptions);
            assertThat(dbOptions.setStrictBytesPerSync(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setTableCacheNumshardbits(4)).isSameAs(dbOptions);
            assertThat(dbOptions.setTwoWriteQueues(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setUnorderedWrite(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setUseAdaptiveMutex(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setUseDirectIoForFlushAndCompaction(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setUseDirectReads(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setUseFsync(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setWalBytesPerSync(4096)).isSameAs(dbOptions);
            assertThat(dbOptions.setWalDir(tempDir.resolve("wal").toString())).isSameAs(dbOptions);
            assertThat(dbOptions.setWalRecoveryMode(WALRecoveryMode.PointInTimeRecovery)).isSameAs(dbOptions);
            assertThat(dbOptions.setWalSizeLimitMB(1)).isSameAs(dbOptions);
            assertThat(dbOptions.setWalTtlSeconds(60)).isSameAs(dbOptions);
            assertThat(dbOptions.setWriteBufferManager(writeBufferManager)).isSameAs(dbOptions);
            assertThat(dbOptions.setWriteDbidToManifest(true)).isSameAs(dbOptions);
            assertThat(dbOptions.setWriteThreadMaxYieldUsec(100)).isSameAs(dbOptions);
            assertThat(dbOptions.setWriteThreadSlowYieldUsec(100)).isSameAs(dbOptions);

            assertThat(dbOptions.bytesPerSync()).isEqualTo(4096);
            assertThat(dbOptions.compactionReadaheadSize()).isEqualTo(4096);
            assertThat(dbOptions.dailyOffpeakTimeUTC()).isEqualTo("01:00-05:00");
            assertThat(dbOptions.delayedWriteRate()).isEqualTo(4096);
            assertThat(dbOptions.deleteObsoleteFilesPeriodMicros()).isEqualTo(1000);
            assertThat(dbOptions.skipCheckingSstFileSizesOnDbOpen()).isTrue();
            assertThat(dbOptions.skipStatsUpdateOnDbOpen()).isTrue();
            assertThat(dbOptions.statsDumpPeriodSec()).isEqualTo(1);
            assertThat(dbOptions.statsHistoryBufferSize()).isEqualTo(4096);
            assertThat(dbOptions.statsPersistPeriodSec()).isEqualTo(1);
            assertThat(dbOptions.strictBytesPerSync()).isTrue();
            assertThat(dbOptions.tableCacheNumshardbits()).isEqualTo(4);
            assertThat(dbOptions.twoWriteQueues()).isTrue();
            assertThat(dbOptions.unorderedWrite()).isTrue();
            assertThat(dbOptions.useAdaptiveMutex()).isTrue();
            assertThat(dbOptions.useDirectIoForFlushAndCompaction()).isTrue();
            assertThat(dbOptions.useDirectReads()).isTrue();
            assertThat(dbOptions.walBytesPerSync()).isEqualTo(4096);
            assertThat(dbOptions.walFilter()).isNotNull();
            assertThat(dbOptions.walSizeLimitMB()).isEqualTo(1);
            assertThat(dbOptions.walTtlSeconds()).isEqualTo(60);
            assertThat(dbOptions.writableFileMaxBufferSize()).isEqualTo(2048);
            assertThat(dbOptions.writeBufferManager()).isSameAs(writeBufferManager);
            assertThat(dbOptions.writeDbidToManifest()).isTrue();
            assertThat(dbOptions.writeThreadMaxYieldUsec()).isEqualTo(100);
            assertThat(dbOptions.writeThreadSlowYieldUsec()).isEqualTo(100);
            assertThat(dbOptions.adviseRandomOnOpen()).isTrue();
            assertThat(dbOptions.allow2pc()).isTrue();
            assertThat(dbOptions.allowConcurrentMemtableWrite()).isTrue();
            assertThat(dbOptions.allowFAllocate()).isTrue();
            assertThat(dbOptions.allowIngestBehind()).isTrue();
            assertThat(dbOptions.allowMmapWrites()).isTrue();
            assertThat(dbOptions.atomicFlush()).isTrue();
            assertThat(dbOptions.avoidFlushDuringRecovery()).isTrue();
            assertThat(dbOptions.avoidFlushDuringShutdown()).isTrue();
            assertThat(dbOptions.avoidUnnecessaryBlockingIO()).isTrue();
            assertThat(dbOptions.bestEffortsRecovery()).isTrue();
            assertThat(dbOptions.bgerrorResumeRetryInterval()).isEqualTo(1000);
            assertThat(dbOptions.createIfMissing()).isTrue();
            assertThat(dbOptions.createMissingColumnFamilies()).isTrue();
            assertThat(dbOptions.dbPaths()).containsExactly(new DbPath(tempDir.resolve("db"), 4096));
            assertThat(dbOptions.dbWriteBufferSize()).isEqualTo(16384);
            assertThat(dbOptions.dumpMallocStats()).isTrue();
            assertThat(dbOptions.enablePipelinedWrite()).isTrue();
            assertThat(dbOptions.enableThreadTracking()).isTrue();
            assertThat(dbOptions.enableWriteThreadAdaptiveYield()).isTrue();
            assertThat(dbOptions.errorIfExists()).isTrue();
            assertThat(dbOptions.isFdCloseOnExec()).isTrue();
            assertThat(dbOptions.keepLogFileNum()).isEqualTo(2);
            assertThat(dbOptions.listeners()).hasSize(1);
            assertThat(dbOptions.logFileTimeToRoll()).isEqualTo(60);
            assertThat(dbOptions.logReadaheadSize()).isEqualTo(4096);
            assertThat(dbOptions.manifestPreallocationSize()).isEqualTo(4096);
            assertThat(dbOptions.manualWalFlush()).isTrue();
            assertThat(dbOptions.maxBackgroundCompactions()).isEqualTo(2);
            assertThat(dbOptions.maxBackgroundFlushes()).isEqualTo(2);
            assertThat(dbOptions.maxBackgroundJobs()).isEqualTo(4);
            assertThat(dbOptions.maxBgerrorResumeCount()).isEqualTo(2);
            assertThat(dbOptions.maxFileOpeningThreads()).isEqualTo(2);
            assertThat(dbOptions.maxLogFileSize()).isEqualTo(4096);
            assertThat(dbOptions.maxManifestFileSize()).isEqualTo(4096);
            assertThat(dbOptions.maxSubcompactions()).isEqualTo(2);
            assertThat(dbOptions.maxTotalWalSize()).isEqualTo(8192);
            assertThat(dbOptions.maxWriteBatchGroupSizeBytes()).isEqualTo(4096);
            assertThat(dbOptions.paranoidChecks()).isTrue();
            assertThat(dbOptions.persistStatsToDisk()).isTrue();
            assertThat(dbOptions.recycleLogFileNum()).isEqualTo(2);
            assertThat(dbOptions.rowCache()).isSameAs(rowCache);
            try {
                assertThat(dbOptions.failIfOptionsFileError()).isTrue();
            } catch (UnsatisfiedLinkError ignored) {
                // The older native test library does not export this newer getter.
            }

            assertThat(dbOptions.walRecoveryMode()).isEqualTo(WALRecoveryMode.PointInTimeRecovery);
            assertThat(dbOptions.statistics()).isNotNull();
            assertThat(dbOptions.maxOpenFiles()).isEqualTo(32);
            assertThat(dbOptions.useFsync()).isTrue();
            assertThat(dbOptions.allowMmapReads()).isTrue();
            assertThat(dbOptions.dbLogDir()).isEqualTo(tempDir.resolve("logs").toString());
            assertThat(dbOptions.walDir()).isEqualTo(tempDir.resolve("wal").toString());

            assertThat(options.setListeners(Collections.singletonList(new CoverageEventListener()))).isSameAs(options);
            assertThat(options.setLogFileTimeToRoll(60)).isSameAs(options);
            assertThat(options.setLogReadaheadSize(4096)).isSameAs(options);
            assertThat(options.setManifestPreallocationSize(4096)).isSameAs(options);
            assertThat(options.setManualWalFlush(true)).isSameAs(options);
            assertThat(options.setMaxBackgroundCompactions(2)).isSameAs(options);
            assertThat(options.setMaxBackgroundFlushes(2)).isSameAs(options);
            assertThat(options.setMaxBackgroundJobs(4)).isSameAs(options);
            assertThat(options.setMaxOpenFiles(32)).isSameAs(options);
            assertThat(options.setWritableFileMaxBufferSize(2048)).isSameAs(options);
            assertThat(options.setMaxBgErrorResumeCount(2)).isSameAs(options);
            assertThat(options.setMaxFileOpeningThreads(2)).isSameAs(options);
            assertThat(options.setMaxLogFileSize(4096)).isSameAs(options);
            assertThat(options.setMaxManifestFileSize(4096)).isSameAs(options);
            assertThat(options.setMaxSubcompactions(2)).isSameAs(options);
            assertThat(options.setMaxTotalWalSize(8192)).isSameAs(options);
            assertThat(options.setMaxWriteBatchGroupSizeBytes(4096)).isSameAs(options);
            assertThat(options.setParanoidChecks(true)).isSameAs(options);
            assertThat(options.setPersistStatsToDisk(true)).isSameAs(options);
            assertThat(options.setRateLimiter(rateLimiter)).isSameAs(options);
            assertThat(options.setRecycleLogFileNum(2)).isSameAs(options);
            assertThat(options.setRowCache(rowCache)).isSameAs(options);
            assertThat(options.setSkipCheckingSstFileSizesOnDbOpen(true)).isSameAs(options);
            assertThat(options.setSkipStatsUpdateOnDbOpen(true)).isSameAs(options);
            assertThat(options.setSstFileManager(new org.rocksdb.SstFileManager(org.rocksdb.Env.getDefault())))
                    .isSameAs(options);
            assertThat(options.setStatistics(statistics)).isSameAs(options);
            assertThat(options.setStatsDumpPeriodSec(1)).isSameAs(options);
            assertThat(options.setStatsHistoryBufferSize(4096)).isSameAs(options);
            assertThat(options.setStatsPersistPeriodSec(1)).isSameAs(options);
            assertThat(options.setStrictBytesPerSync(true)).isSameAs(options);
            assertThat(options.setTableCacheNumshardbits(4)).isSameAs(options);
            assertThat(options.setTwoWriteQueues(true)).isSameAs(options);
            assertThat(options.setUnorderedWrite(true)).isSameAs(options);
            assertThat(options.setUseAdaptiveMutex(true)).isSameAs(options);
            assertThat(options.setUseDirectIoForFlushAndCompaction(true)).isSameAs(options);
            assertThat(options.setUseDirectReads(true)).isSameAs(options);
            assertThat(options.setUseFsync(true)).isSameAs(options);
            assertThat(options.setWalBytesPerSync(4096)).isSameAs(options);
            assertThat(options.setWalDir(tempDir.resolve("options-wal").toString())).isSameAs(options);
            assertThat(options.setWalFilter(new CoverageWalFilter())).isSameAs(options);
            assertThat(options.setWalRecoveryMode(WALRecoveryMode.PointInTimeRecovery)).isSameAs(options);
            assertThat(options.walRecoveryMode()).isEqualTo(WALRecoveryMode.PointInTimeRecovery);
            assertThat(options.setWalSizeLimitMB(1)).isSameAs(options);
            assertThat(options.setWalTtlSeconds(60)).isSameAs(options);
            assertThat(options.setWriteBufferManager(writeBufferManager)).isSameAs(options);
            assertThat(options.setWriteDbidToManifest(true)).isSameAs(options);
            assertThat(options.setWriteThreadMaxYieldUsec(100)).isSameAs(options);
            assertThat(options.setWriteThreadSlowYieldUsec(100)).isSameAs(options);
            assertThat(options.setCreateMissingColumnFamilies(true)).isSameAs(options);
            assertThat(options.setAdviseRandomOnOpen(false)).isSameAs(options);
            assertThat(options.setAllow2pc(true)).isSameAs(options);
            assertThat(options.setAllowConcurrentMemtableWrite(true)).isSameAs(options);
            assertThat(options.setAllowFAllocate(true)).isSameAs(options);
            assertThat(options.setAllowIngestBehind(true)).isSameAs(options);
            assertThat(options.setAllowMmapReads(true)).isSameAs(options);
            assertThat(options.setAllowMmapWrites(true)).isSameAs(options);
            assertThat(options.setAtomicFlush(true)).isSameAs(options);
            assertThat(options.setAvoidFlushDuringRecovery(true)).isSameAs(options);
            assertThat(options.setAvoidFlushDuringShutdown(true)).isSameAs(options);
            assertThat(options.setAvoidUnnecessaryBlockingIO(true)).isSameAs(options);
            assertThat(options.setBestEffortsRecovery(true)).isSameAs(options);
            assertThat(options.setBgerrorResumeRetryInterval(1000)).isSameAs(options);
            assertThat(options.setBytesPerSync(4096)).isSameAs(options);
            assertThat(options.setCompactionReadaheadSize(4096)).isSameAs(options);
            assertThat(options.setDailyOffpeakTimeUTC("01:00-05:00")).isSameAs(options);
            assertThat(options.setDbLogDir(tempDir.resolve("logs").toString())).isSameAs(options);
            assertThat(options.setDbPaths(Collections.singletonList(new DbPath(tempDir.resolve("db"), 4096))))
                    .isSameAs(options);
            assertThat(options.setDbWriteBufferSize(16384)).isSameAs(options);
            assertThat(options.setDelayedWriteRate(4096)).isSameAs(options);
            assertThat(options.setDeleteObsoleteFilesPeriodMicros(1000)).isSameAs(options);
            assertThat(options.setDumpMallocStats(true)).isSameAs(options);
            assertThat(options.setEnablePipelinedWrite(true)).isSameAs(options);
            assertThat(options.setEnableThreadTracking(true)).isSameAs(options);
            assertThat(options.setEnableWriteThreadAdaptiveYield(true)).isSameAs(options);
            assertThat(options.setErrorIfExists(true)).isSameAs(options);
            assertThat(options.setExperimentalMempurgeThreshold(0.3)).isSameAs(options);
            try {
                assertThat(options.setFailIfOptionsFileError(true)).isSameAs(options);
            } catch (UnsatisfiedLinkError ignored) {
                // The older native test library does not export this newer setter.
            }
            assertThat(options.setForceConsistencyChecks(true)).isSameAs(options);
            assertThat(options.setIncreaseParallelism(2)).isSameAs(options);
            assertThat(options.setInfoLogLevel(InfoLogLevel.INFO_LEVEL)).isSameAs(options);
            assertThat(options.setInplaceUpdateNumLocks(2)).isSameAs(options);
            assertThat(options.setInplaceUpdateSupport(true)).isSameAs(options);
            assertThat(options.setIsFdCloseOnExec(true)).isSameAs(options);
            assertThat(options.setKeepLogFileNum(2)).isSameAs(options);
            assertThat(options.setLevel0FileNumCompactionTrigger(4)).isSameAs(options);
            assertThat(options.setLevel0SlowdownWritesTrigger(8)).isSameAs(options);
            assertThat(options.setLevel0StopWritesTrigger(12)).isSameAs(options);
            assertThat(options.setLevelCompactionDynamicLevelBytes(true)).isSameAs(options);
            assertThat(options.setLevelZeroFileNumCompactionTrigger(4)).isSameAs(options);
            assertThat(options.setLevelZeroSlowdownWritesTrigger(8)).isSameAs(options);
            assertThat(options.setLevelZeroStopWritesTrigger(12)).isSameAs(options);
            TablePropertiesCollectorFactory collectorFactory =
                    TablePropertiesCollectorFactory.NewCompactOnDeletionCollectorFactory(1, 1, 0.5);
            options.setTablePropertiesCollectorFactory(Collections.singletonList(collectorFactory));
            List<TablePropertiesCollectorFactory> factories = options.tablePropertiesCollectorFactory();
            assertThat(factories).hasSize(1);
            factories.get(0).close();
            collectorFactory.close();
        }
    }

    @Test
    void columnFamilyConstructorsAndPropertyParsingCreateUsableOptions() {
        Properties properties = new Properties();
        properties.setProperty("write_buffer_size", "2048");
        try (ConfigOptions config = new ConfigOptions();
             ColumnFamilyOptions parsed = ColumnFamilyOptions.getColumnFamilyOptionsFromProps(config, properties);
             ColumnFamilyOptions copied = new ColumnFamilyOptions(parsed);
             Options source = new Options();
             DBOptions originalDbOptions = new DBOptions();
             DBOptions copiedDbOptions = new DBOptions(originalDbOptions);
             DBOptions fromDbOptions = new DBOptions(source);
             ColumnFamilyOptions fromOptions = new ColumnFamilyOptions(source)) {
            Properties dbProperties = new Properties();
            dbProperties.setProperty("max_open_files", "17");
            try (DBOptions parsedDbOptions = DBOptions.getDBOptionsFromProps(config, dbProperties)) {
                assertThat(parsedDbOptions.maxOpenFiles()).isEqualTo(17);
            }
            assertThat(parsed.writeBufferSize()).isEqualTo(2048);
            assertThat(copied.writeBufferSize()).isEqualTo(2048);
            assertThat(fromOptions.memTableFactoryName()).isNotBlank();
            assertThat(copiedDbOptions.maxOpenFiles()).isEqualTo(originalDbOptions.maxOpenFiles());
            assertThat(fromDbOptions.maxOpenFiles()).isEqualTo(source.maxOpenFiles());
        }
    }

    @Test
    void blockTableConfigurationRoundTripsThroughPublicApi() {
        BlockBasedTableConfig table = new BlockBasedTableConfig();
        try (Cache cache = new org.rocksdb.LRUCache(4096)) {
            assertThat(table.setBlockAlign(true).blockAlign()).isTrue();
            assertThat(table.setBlockCache(cache).blockCacheSize()).isPositive();
            assertThat(table.setBlockCacheSize(2048).blockCacheSize()).isEqualTo(2048);
            assertThat(table.setBlockRestartInterval(7).blockRestartInterval()).isEqualTo(7);
            assertThat(table.setBlockSize(8192).blockSize()).isEqualTo(8192);
            assertThat(table.setBlockSizeDeviation(3).blockSizeDeviation()).isEqualTo(3);
            assertThat(table.setCacheIndexAndFilterBlocks(true).cacheIndexAndFilterBlocks()).isTrue();
            assertThat(table.setCacheIndexAndFilterBlocksWithHighPriority(true)
                    .cacheIndexAndFilterBlocksWithHighPriority()).isTrue();
            assertThat(table.setCacheNumShardBits(2).cacheNumShardBits()).isEqualTo(2);
            assertThat(table.setChecksumType(org.rocksdb.ChecksumType.kCRC32c).checksumType())
                    .isEqualTo(org.rocksdb.ChecksumType.kCRC32c);
            assertThat(table.setDataBlockHashTableUtilRatio(0.75).dataBlockHashTableUtilRatio())
                    .isEqualTo(0.75);
            assertThat(table.setDataBlockIndexType(org.rocksdb.DataBlockIndexType.kDataBlockBinaryAndHash)
                    .dataBlockIndexType()).isEqualTo(org.rocksdb.DataBlockIndexType.kDataBlockBinaryAndHash);
            assertThat(table.setEnableIndexCompression(false).enableIndexCompression()).isFalse();
            assertThat(table.setFormatVersion(3).formatVersion()).isEqualTo(3);
            assertThat(table.setHashIndexAllowCollision(true).hashIndexAllowCollision()).isTrue();
            assertThat(table.setIndexBlockRestartInterval(9).indexBlockRestartInterval()).isEqualTo(9);
            assertThat(table.setIndexShortening(org.rocksdb.IndexShorteningMode.kShortenSeparators)
                    .indexShortening()).isEqualTo(org.rocksdb.IndexShorteningMode.kShortenSeparators);
            assertThat(table.setIndexType(org.rocksdb.IndexType.kHashSearch).indexType())
                    .isEqualTo(org.rocksdb.IndexType.kHashSearch);
            assertThat(table.setMetadataBlockSize(1024).metadataBlockSize()).isEqualTo(1024);
            assertThat(table.setNoBlockCache(true).noBlockCache()).isTrue();
            assertThat(table.setOptimizeFiltersForMemory(true).optimizeFiltersForMemory()).isTrue();
            assertThat(table.setPartitionFilters(true).partitionFilters()).isTrue();
            assertThat(table.setPinL0FilterAndIndexBlocksInCache(true)
                    .pinL0FilterAndIndexBlocksInCache()).isTrue();
            assertThat(table.setPinTopLevelIndexAndFilter(true).pinTopLevelIndexAndFilter()).isTrue();
            assertThat(table.setReadAmpBytesPerBit(4).readAmpBytesPerBit()).isEqualTo(4);
            assertThat(table.setUseDeltaEncoding(true).useDeltaEncoding()).isTrue();
            assertThat(table.setVerifyCompression(true).verifyCompression()).isTrue();
            assertThat(table.setWholeKeyFiltering(true).wholeKeyFiltering()).isTrue();
            assertThat(table.setPersistentCache(null)).isSameAs(table);
        }
    }

    @Test
    void mutableOptionParsingDrivesPublicGetterEntries() {
        org.rocksdb.MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder builder =
                org.rocksdb.MutableColumnFamilyOptions.parse(
                        "blob_garbage_collection_age_cutoff=0.25;"
                                + "blob_file_starting_level=3;"
                                + "max_bytes_for_level_multiplier_additional=1:2;"
                                + "arena_block_size=4096;disable_auto_compactions=true");

        assertThat(builder.blobGarbageCollectionAgeCutoff()).isEqualTo(0.25);
        assertThat(builder.blobFileStartingLevel()).isEqualTo(3);
        assertThat(builder.maxBytesForLevelMultiplierAdditional()).containsExactly(1, 2);
        assertThat(builder.arenaBlockSize()).isEqualTo(4096);
        assertThat(builder.disableAutoCompactions()).isTrue();
    }

    @Test
    void invalidMutableOptionValuesRenderTheirPublicOptionValue() {
        assertThatThrownBy(() -> org.rocksdb.MutableDBOptions.parse("max_open_files=not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_open_files=not-a-number");

    }

    @Test
    void propertyAndMutableOptionBuildersPreserveValues() {
        Properties cfProperties = new Properties();
        cfProperties.setProperty("write_buffer_size", "12345");
        cfProperties.setProperty("max_write_buffer_number", "3");
        Properties dbProperties = new Properties();
        dbProperties.setProperty("max_open_files", "42");

        MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder cfBuilder = MutableColumnFamilyOptions.builder();
        MutableDBOptions.MutableDBOptionsBuilder dbBuilder = MutableDBOptions.builder();
        try (ColumnFamilyOptions cf = ColumnFamilyOptions.getColumnFamilyOptionsFromProps(cfProperties);
             DBOptions db = DBOptions.getDBOptionsFromProps(dbProperties)) {
            assertThat(cf.writeBufferSize()).isEqualTo(12345);
            assertThat(db.maxOpenFiles()).isEqualTo(42);
            assertThat(cfBuilder.setWriteBufferSize(8192).writeBufferSize()).isEqualTo(8192);
            assertThat(cfBuilder.setCompressionType(CompressionType.NO_COMPRESSION).compressionType())
                    .isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(cfBuilder.setDisableAutoCompactions(true).disableAutoCompactions()).isTrue();
            assertThat(cfBuilder.setEnableBlobFiles(true).enableBlobFiles()).isTrue();
            assertThat(cfBuilder.setMinBlobSize(512).minBlobSize()).isEqualTo(512);
            assertThat(cfBuilder.setBlobFileSize(4096).blobFileSize()).isEqualTo(4096);
            assertThat(cfBuilder.setBlobCompressionType(CompressionType.NO_COMPRESSION).blobCompressionType())
                    .isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(cfBuilder.setBlobFileStartingLevel(1).blobFileStartingLevel()).isEqualTo(1);
            assertThat(cfBuilder.setBlobCompactionReadaheadSize(128).blobCompactionReadaheadSize())
                    .isEqualTo(128);
            assertThat(cfBuilder.setBlobGarbageCollectionAgeCutoff(0.4).blobGarbageCollectionAgeCutoff())
                    .isEqualTo(0.4);
            assertThat(cfBuilder.setBlobGarbageCollectionForceThreshold(0.6)
                    .blobGarbageCollectionForceThreshold()).isEqualTo(0.6);
            assertThat(cfBuilder.setMaxBytesForLevelMultiplierAdditional(new int[] {2, 4})
                    .maxBytesForLevelMultiplierAdditional()).containsExactly(2, 4);
            try {
                assertThat(cfBuilder.setMaxBytesForLevelMultiplier(3.0).maxBytesForLevelMultiplier())
                        .isEqualTo(3.0);
            } catch (IllegalArgumentException expected) {
                assertThat(expected).hasMessageContaining("does not accept a double");
            }
            assertThat(cfBuilder.setEnableBlobGarbageCollection(true).enableBlobGarbageCollection()).isTrue();
            assertThat(cfBuilder.setExperimentalMempurgeThreshold(0.3).experimentalMempurgeThreshold())
                    .isEqualTo(0.3);
            assertThat(cfBuilder.setHardPendingCompactionBytesLimit(8192).hardPendingCompactionBytesLimit())
                    .isEqualTo(8192);
            assertThat(cfBuilder.setLevel0FileNumCompactionTrigger(4).level0FileNumCompactionTrigger())
                    .isEqualTo(4);
            assertThat(cfBuilder.setLevel0SlowdownWritesTrigger(8).level0SlowdownWritesTrigger()).isEqualTo(8);
            assertThat(cfBuilder.setLevel0StopWritesTrigger(12).level0StopWritesTrigger()).isEqualTo(12);
            assertThat(cfBuilder.setSoftPendingCompactionBytesLimit(4096).softPendingCompactionBytesLimit())
                    .isEqualTo(4096);
            assertThat(cfBuilder.getUnknown()).isEmpty();
            assertThat(cfBuilder.setMaxBytesForLevelBase(4096).maxBytesForLevelBase()).isEqualTo(4096);
            assertThat(cfBuilder.setMaxCompactionBytes(8192).maxCompactionBytes()).isEqualTo(8192);
            assertThat(cfBuilder.setMaxSuccessiveMerges(3).maxSuccessiveMerges()).isEqualTo(3);
            assertThat(cfBuilder.setTargetFileSizeBase(2048).targetFileSizeBase()).isEqualTo(2048);
            assertThat(cfBuilder.setTargetFileSizeMultiplier(2).targetFileSizeMultiplier()).isEqualTo(2);
            assertThat(cfBuilder.setMaxWriteBufferNumber(3).maxWriteBufferNumber()).isEqualTo(3);
            assertThat(cfBuilder.setMaxSequentialSkipInIterations(4).maxSequentialSkipInIterations())
                    .isEqualTo(4);
            assertThat(cfBuilder.setArenaBlockSize(1024).arenaBlockSize()).isEqualTo(1024);
            assertThat(cfBuilder.setMemtableHugePageSize(0).memtableHugePageSize()).isEqualTo(0);
            assertThat(cfBuilder.setMemtablePrefixBloomSizeRatio(0.2).memtablePrefixBloomSizeRatio())
                    .isEqualTo(0.2);
            assertThat(cfBuilder.setMemtableWholeKeyFiltering(true).memtableWholeKeyFiltering()).isTrue();
            assertThat(cfBuilder.setInplaceUpdateNumLocks(2).inplaceUpdateNumLocks()).isEqualTo(2);
            assertThat(cfBuilder.setParanoidFileChecks(true).paranoidFileChecks()).isTrue();
            assertThat(cfBuilder.setPeriodicCompactionSeconds(10).periodicCompactionSeconds()).isEqualTo(10);
            assertThat(cfBuilder.setTtl(10).ttl()).isEqualTo(10);
            assertThat(cfBuilder.setReportBgIoStats(true).reportBgIoStats()).isTrue();
            assertThat(cfBuilder.setPrepopulateBlobCache(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY)
                    .prepopulateBlobCache()).isEqualTo(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);

            assertThat(dbBuilder.setMaxBackgroundJobs(3).maxBackgroundJobs()).isEqualTo(3);
            assertThat(dbBuilder.setMaxBackgroundCompactions(2).maxBackgroundCompactions()).isEqualTo(2);
            assertThat(dbBuilder.setAvoidFlushDuringShutdown(true).avoidFlushDuringShutdown()).isTrue();
            assertThat(dbBuilder.setWritableFileMaxBufferSize(1024).writableFileMaxBufferSize()).isEqualTo(1024);
            assertThat(dbBuilder.setDelayedWriteRate(2048).delayedWriteRate()).isEqualTo(2048);
            assertThat(dbBuilder.setMaxTotalWalSize(4096).maxTotalWalSize()).isEqualTo(4096);
            assertThat(dbBuilder.setDeleteObsoleteFilesPeriodMicros(100).deleteObsoleteFilesPeriodMicros())
                    .isEqualTo(100);
            assertThat(dbBuilder.setStatsDumpPeriodSec(2).statsDumpPeriodSec()).isEqualTo(2);
            assertThat(dbBuilder.setStatsPersistPeriodSec(3).statsPersistPeriodSec()).isEqualTo(3);
            assertThat(dbBuilder.setStatsHistoryBufferSize(512).statsHistoryBufferSize()).isEqualTo(512);
            assertThat(dbBuilder.setMaxOpenFiles(20).maxOpenFiles()).isEqualTo(20);
            assertThat(dbBuilder.setBytesPerSync(256).bytesPerSync()).isEqualTo(256);
            assertThat(dbBuilder.setWalBytesPerSync(256).walBytesPerSync()).isEqualTo(256);
            assertThat(dbBuilder.setStrictBytesPerSync(true).strictBytesPerSync()).isTrue();
            assertThat(dbBuilder.setCompactionReadaheadSize(512).compactionReadaheadSize()).isEqualTo(512);
            assertThat(dbBuilder.setDailyOffpeakTimeUTC("02:00-03:00").dailyOffpeakTimeUTC())
                    .isEqualTo("02:00-03:00");
            assertThat(dbBuilder.build().toString()).contains("max_background_jobs=3");
            MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder parsedMutable = MutableColumnFamilyOptions
                    .parse("write_buffer_size=4096;max_write_buffer_number=2");
            assertThat(parsedMutable.writeBufferSize()).isEqualTo(4096);
            assertThat(parsedMutable.maxWriteBufferNumber()).isEqualTo(2);
            assertThat(MutableDBOptions.parse("max_open_files=10").build().toString())
                    .contains("max_open_files=10");
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void abstractComparatorDefaultKeyShorteningPreservesKeys() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")),
                "RocksDB comparator callbacks are not supported in native-image tests");
        try (org.rocksdb.ComparatorOptions comparatorOptions = new org.rocksdb.ComparatorOptions();
             CoverageComparator comparator = new CoverageComparator(comparatorOptions)) {
            ByteBuffer start = ByteBuffer.wrap(bytes("alpha"));
            ByteBuffer limit = ByteBuffer.wrap(bytes("omega"));
            comparator.findShortestSeparator(start, limit);
            comparator.findShortSuccessor(start);
            assertThat(start.position()).isZero();
            assertThat(start.remaining()).isEqualTo(5);
            assertThat(start.array()).isEqualTo(bytes("alpha"));
            assertThat(limit.position()).isZero();
            assertThat(limit.remaining()).isEqualTo(5);
            assertThat(limit.array()).isEqualTo(bytes("omega"));
        }
    }

    private static final class CoverageComparator extends AbstractComparator {
        CoverageComparator() {
            this(new org.rocksdb.ComparatorOptions());
        }

        CoverageComparator(org.rocksdb.ComparatorOptions options) {
            super(options);
        }

        @Override
        public String name() {
            return "coverage-comparator";
        }

        @Override
        public int compare(ByteBuffer first, ByteBuffer second) {
            return Integer.compare(first.remaining(), second.remaining());
        }
    }

    private static final class CoverageLogger extends Logger {
        CoverageLogger(DBOptions options) {
            super(options);
        }

        CoverageLogger(Options options) {
            super(options);
        }

        @Override
        protected void log(InfoLogLevel logLevel, String logMessage) {
        }
    }

    @Test
    void callbackObjectsReleaseNativeHandlesThroughClose() {
        try (CoverageEventListener listener = new CoverageEventListener();
             CoverageFilterFactory filterFactory = new CoverageFilterFactory()) {
            assertThat(listener.isOwningHandle()).isTrue();
            assertThat(filterFactory.isOwningHandle()).isTrue();
        }
    }

    private static final class CoverageEventListener extends AbstractEventListener {
    }

    private static final class CoverageWalFilter extends AbstractWalFilter {
        @Override
        public void columnFamilyLogNumberMap(java.util.Map<Integer, Long> map,
                java.util.Map<String, Integer> names) {
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

    private static final class CoverageFilterFactory extends AbstractCompactionFilterFactory<AbstractCompactionFilter<?>> {
        @Override
        public AbstractCompactionFilter<?> createCompactionFilter(
                org.rocksdb.AbstractCompactionFilter.Context context) {
            return new org.rocksdb.RemoveEmptyValueCompactionFilter();
        }

        @Override
        public String name() {
            return "coverage-filter-factory";
        }
    }
}
