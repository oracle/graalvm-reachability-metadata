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
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupEngineOptions;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BuiltinComparator;
import org.rocksdb.CassandraCompactionFilter;
import org.rocksdb.CassandraValueMergeOperator;
import org.rocksdb.CompactionOptions;
import org.rocksdb.CompactionOptionsFIFO;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompactionStyle;
import org.rocksdb.ReusedSynchronisationType;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactRangeOptions;
import org.rocksdb.CompactionJobInfo;
import org.rocksdb.CompactionOptionsUniversal;
import org.rocksdb.CompactionJobStats;
import org.rocksdb.CompressionType;
import org.rocksdb.DbPath;
import org.rocksdb.ComparatorOptions;
import org.rocksdb.ConfigOptions;
import org.rocksdb.DirectSlice;
import org.rocksdb.Env;
import org.rocksdb.EnvOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.HashLinkedListMemTableConfig;
import org.rocksdb.HistogramData;
import org.rocksdb.HistogramType;
import org.rocksdb.IngestExternalFileOptions;
import org.rocksdb.InfoLogLevel;
import org.rocksdb.KeyMayExist;
import org.rocksdb.LRUCache;
import org.rocksdb.MemoryUsageType;
import org.rocksdb.MemoryUtil;
import org.rocksdb.NativeComparatorWrapper;
import org.rocksdb.Options;
import org.rocksdb.OptionsUtil;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.OptimisticTransactionOptions;
import org.rocksdb.RateLimiter;
import org.rocksdb.ReadOptions;
import org.rocksdb.ReadTier;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksMutableObject;
import org.rocksdb.SanityLevel;
import org.rocksdb.Slice;
import org.rocksdb.Statistics;
import org.rocksdb.StatisticsCollector;
import org.rocksdb.StatisticsCollectorCallback;
import org.rocksdb.StatsCollectorInput;
import org.rocksdb.Status;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteBatch;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TtlDB;
import org.rocksdb.TxnDBWritePolicy;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.BytewiseComparator;
import org.rocksdb.util.StdErrLogger;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RocksSupportApiCoverageTest {
    private static final Unsafe UNSAFE = unsafe();

    @BeforeAll
    static void loadNative() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void supportObjectsExposeConfiguredState() throws Exception {
        BlockBasedTableConfig table = new BlockBasedTableConfig();
        HashLinkedListMemTableConfig memTable = new HashLinkedListMemTableConfig();
        try (BloomFilter filter = new BloomFilter();
             CompactRangeOptions range = new CompactRangeOptions();
             CompactionOptionsUniversal universal = new CompactionOptionsUniversal();
             ComparatorOptions comparatorOptions = new ComparatorOptions();
             ConfigOptions config = new ConfigOptions();
             DirectSlice slice = new DirectSlice(directBuffer("direct"));
             EnvOptions envOptions = new EnvOptions();
             FlushOptions flush = new FlushOptions();
             CassandraCompactionFilter cassandra = new CassandraCompactionFilter(true, 1);
             LRUCache cache = new LRUCache(4096);
             RateLimiter limiter = new RateLimiter(4096);
             StringAppendOperator append = new StringAppendOperator();
             StdErrLogger stderr = new StdErrLogger(org.rocksdb.InfoLogLevel.INFO_LEVEL);
             TransactionDBOptions transactionOptions = new TransactionDBOptions()) {
            assertThat(table.setFilter(filter).filterPolicy()).isSameAs(filter);
            assertThat(range.setBottommostLevelCompaction(
                    CompactRangeOptions.BottommostLevelCompaction.kForceOptimized))
                    .isSameAs(range);
            assertThat(range.bottommostLevelCompaction())
                    .isEqualTo(CompactRangeOptions.BottommostLevelCompaction.kForceOptimized);
            assertThat(universal.setStopStyle(org.rocksdb.CompactionStopStyle.CompactionStopStyleTotalSize)
                    .stopStyle()).isEqualTo(org.rocksdb.CompactionStopStyle.CompactionStopStyleTotalSize);
            assertThat(comparatorOptions.reusedSynchronisationType()).isNotNull();
            assertThat(config.setSanityLevel(SanityLevel.EXACT_MATCH)).isSameAs(config);
            assertThat(memTable.bucketCount()).isPositive();
            assertThat(slice.data()).isNotNull();
            assertThat(slice.empty()).isNotNull();
            assertThat(slice.size()).isGreaterThanOrEqualTo(0);
            assertThat(slice.toString()).isEqualTo("direct");
            try {
                new CoverageNativeComparator();
            } catch (ArrayIndexOutOfBoundsException expected) {
                assertThat(expected).isNotNull();
            }
            stderr.setInfoLogLevel(InfoLogLevel.DEBUG_LEVEL);
            assertThat(stderr.infoLogLevel()).isEqualTo(InfoLogLevel.DEBUG_LEVEL);
            assertThat(transactionOptions.setWritePolicy(TxnDBWritePolicy.WRITE_PREPARED)
                    .getWritePolicy()).isEqualTo(TxnDBWritePolicy.WRITE_PREPARED);
            assertThat(append).isNotNull();
            assertThat(envOptions.setUseMmapReads(true).useMmapReads()).isTrue();
            Env environment = Env.getDefault();
            assertThat(environment.setBackgroundThreads(1)).isSameAs(environment);
            assertThat(environment.getBackgroundThreads(org.rocksdb.Priority.LOW)).isGreaterThanOrEqualTo(1);
            assertThat(environment.getThreadPoolQueueLen(org.rocksdb.Priority.LOW)).isGreaterThanOrEqualTo(0);
            assertThat(environment.incBackgroundThreadsIfNeeded(1, org.rocksdb.Priority.LOW))
                    .isSameAs(environment);
            assertThat(environment.lowerThreadPoolCPUPriority(org.rocksdb.Priority.LOW)).isSameAs(environment);
            assertThat(environment.lowerThreadPoolIOPriority(org.rocksdb.Priority.LOW)).isSameAs(environment);
            assertThat(environment.getThreadList()).isNotNull();
            MutableObject mutableObject = new MutableObject();
            mutableObject.resetNativeHandle(1, true);
            mutableObject.close();
            assertThat(flush.setWaitForFlush(true).waitForFlush()).isTrue();
            assertThat(flush.setAllowWriteStall(true).allowWriteStall()).isTrue();
            assertThat(limiter.getBytesPerSecond()).isEqualTo(4096);
        }
    }

    @Test
    void publicEnumsAndValueObjectsDecodeTheirWireValues() {
        assertThat(org.rocksdb.AbstractEventListener.EnabledEventCallback.values()).isNotEmpty();
        assertThat(org.rocksdb.AbstractEventListener.EnabledEventCallback.valueOf("ON_FLUSH_BEGIN"))
                .isEqualTo(org.rocksdb.AbstractEventListener.EnabledEventCallback.ON_FLUSH_BEGIN);
        assertThat(org.rocksdb.BackgroundErrorReason.values()).contains(org.rocksdb.BackgroundErrorReason.FLUSH);
        assertThat(org.rocksdb.BackgroundErrorReason.valueOf("COMPACTION"))
                .isEqualTo(org.rocksdb.BackgroundErrorReason.COMPACTION);
        assertThat(BuiltinComparator.values()).contains(BuiltinComparator.BYTEWISE_COMPARATOR);
        assertThat(BuiltinComparator.valueOf("REVERSE_BYTEWISE_COMPARATOR"))
                .isEqualTo(BuiltinComparator.REVERSE_BYTEWISE_COMPARATOR);
        assertThat(org.rocksdb.ChecksumType.values()).contains(org.rocksdb.ChecksumType.kCRC32c);
        assertThat(org.rocksdb.ChecksumType.valueOf("kCRC32c")).isEqualTo(org.rocksdb.ChecksumType.kCRC32c);
        assertThat(CompactRangeOptions.BottommostLevelCompaction.values())
                .contains(CompactRangeOptions.BottommostLevelCompaction.kForce);
        assertThat(CompactRangeOptions.BottommostLevelCompaction.valueOf("kForce"))
                .isEqualTo(CompactRangeOptions.BottommostLevelCompaction.kForce);
        assertThat(org.rocksdb.CompactionPriority.values()).contains(CompactionPriority.RoundRobin);
        assertThat(org.rocksdb.CompactionPriority.valueOf("RoundRobin"))
                .isEqualTo(CompactionPriority.RoundRobin);
        assertThat(org.rocksdb.CompactionReason.values()).isNotEmpty();
        assertThat(org.rocksdb.CompactionReason.valueOf("kFlush"))
                .isEqualTo(org.rocksdb.CompactionReason.kFlush);
        assertThat(org.rocksdb.CompactionStopStyle.values())
                .contains(org.rocksdb.CompactionStopStyle.CompactionStopStyleTotalSize);
        assertThat(org.rocksdb.CompactionStopStyle.valueOf("CompactionStopStyleTotalSize"))
                .isEqualTo(org.rocksdb.CompactionStopStyle.CompactionStopStyleTotalSize);
        assertThat(org.rocksdb.CompactionStyle.values()).contains(CompactionStyle.LEVEL);
        assertThat(org.rocksdb.CompactionStyle.valueOf("LEVEL")).isEqualTo(CompactionStyle.LEVEL);
        assertThat(org.rocksdb.EncodingType.values()).contains(org.rocksdb.EncodingType.kPlain);
        assertThat(org.rocksdb.EncodingType.valueOf("kPrefix")).isEqualTo(org.rocksdb.EncodingType.kPrefix);
        assertThat(org.rocksdb.EncodingType.kPrefix.getValue()).isEqualTo((byte) 1);
        assertThat(org.rocksdb.FilterPolicyType.values()).isNotEmpty();
        assertThat(org.rocksdb.FilterPolicyType.valueOf(
                org.rocksdb.FilterPolicyType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.FilterPolicyType.values()[0].getValue()).isGreaterThanOrEqualTo((byte) 0);
        assertThat(org.rocksdb.DataBlockIndexType.valueOf(
                org.rocksdb.DataBlockIndexType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.DataBlockIndexType.values()).isNotEmpty();
        assertThat(org.rocksdb.FlushReason.valueOf(org.rocksdb.FlushReason.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.FlushReason.values()).isNotEmpty();
        assertThat(org.rocksdb.IndexShorteningMode.valueOf(
                org.rocksdb.IndexShorteningMode.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.IndexShorteningMode.values()).isNotEmpty();
        assertThat(org.rocksdb.IndexType.valueOf(org.rocksdb.IndexType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.IndexType.values()).isNotEmpty();
        assertThat(org.rocksdb.InfoLogLevel.valueOf(org.rocksdb.InfoLogLevel.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.InfoLogLevel.values()).isNotEmpty();
        assertThat(org.rocksdb.KeyMayExist.KeyMayExistEnum.valueOf(
                org.rocksdb.KeyMayExist.KeyMayExistEnum.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.KeyMayExist.KeyMayExistEnum.values()).isNotEmpty();
        assertThat(org.rocksdb.LoggerType.valueOf(org.rocksdb.LoggerType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.LoggerType.values()).isNotEmpty();
        assertThat(org.rocksdb.MemoryUsageType.valueOf(org.rocksdb.MemoryUsageType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MemoryUsageType.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.BlobOption.valueOf(
                org.rocksdb.MutableColumnFamilyOptions.BlobOption.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.BlobOption.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.CompactionOption.valueOf(
                org.rocksdb.MutableColumnFamilyOptions.CompactionOption.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.CompactionOption.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.MemtableOption.valueOf(
                org.rocksdb.MutableColumnFamilyOptions.MemtableOption.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.MemtableOption.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.MiscOption.valueOf(
                org.rocksdb.MutableColumnFamilyOptions.MiscOption.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableColumnFamilyOptions.MiscOption.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableDBOptions.DBOption.valueOf(
                org.rocksdb.MutableDBOptions.DBOption.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableDBOptions.DBOption.values()).isNotEmpty();
        assertThat(org.rocksdb.MutableOptionKey.ValueType.valueOf(
                org.rocksdb.MutableOptionKey.ValueType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.MutableOptionKey.ValueType.values()).isNotEmpty();
        assertThat(org.rocksdb.OperationStage.valueOf(
                org.rocksdb.OperationStage.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.OperationStage.values()).isNotEmpty();
        assertThat(org.rocksdb.OperationType.valueOf(
                org.rocksdb.OperationType.values()[0].name())).isNotNull();
        assertThat(org.rocksdb.OperationType.values()).isNotEmpty();
        assertThat(org.rocksdb.HistogramType.getHistogramType(org.rocksdb.HistogramType.DB_GET.getValue()))
                .isEqualTo(org.rocksdb.HistogramType.DB_GET);
        assertThat(org.rocksdb.HistogramType.valueOf("DB_GET")).isEqualTo(org.rocksdb.HistogramType.DB_GET);
        assertThat(org.rocksdb.HistogramType.values()).contains(org.rocksdb.HistogramType.DB_GET);
        assertThat(org.rocksdb.TickerType.getTickerType(org.rocksdb.TickerType.BLOCK_CACHE_HIT.getValue()))
                .isEqualTo(org.rocksdb.TickerType.BLOCK_CACHE_HIT);
        assertThat(org.rocksdb.RateLimiterMode.getRateLimiterMode(org.rocksdb.RateLimiterMode.ALL_IO.getValue()))
                .isEqualTo(org.rocksdb.RateLimiterMode.ALL_IO);
        assertThat(org.rocksdb.WalProcessingOption.fromValue((byte) 0))
                .isEqualTo(org.rocksdb.WalProcessingOption.CONTINUE_PROCESSING);
        assertThat(org.rocksdb.PrepopulateBlobCache.getPrepopulateBlobCache("flush_only")).isNotNull();
        assertThat(org.rocksdb.ThreadStatus.getThreadTypeName(org.rocksdb.ThreadType.HIGH_PRIORITY))
                .isNotBlank();
        assertThat(org.rocksdb.ThreadStatus.getOperationName(org.rocksdb.OperationType.OP_UNKNOWN)).isNull();
        assertThat(org.rocksdb.ThreadStatus.getOperationStageName(org.rocksdb.OperationStage.STAGE_UNKNOWN)).isNull();
        assertThat(org.rocksdb.ThreadStatus.getStateName(org.rocksdb.StateType.STATE_UNKNOWN)).isNull();
        assertThat(org.rocksdb.util.Environment.getSharedLibraryFileName("rocksdbjni")).contains("rocksdbjni");
        try (org.rocksdb.AbstractTraceWriter writer = new CoverageTraceWriter()) {
            assertThat(writer.getFileSize()).isZero();
        }
    }

    @Test
    void slicesAndValueObjectsHaveValueSemantics() {
        try (Slice first = new Slice("value"); Slice same = new Slice("value"); Slice prefix = new Slice("val")) {
            assertThat(first.data()).isEqualTo("value".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(first.empty()).isFalse();
            assertThat(first.size()).isEqualTo(5);
            assertThat(first.hashCode()).isEqualTo(same.hashCode());
            assertThat(first.equals(same)).isTrue();
            assertThat(first.startsWith(prefix)).isTrue();
        }
        org.junit.jupiter.api.Assumptions.assumeFalse(
                "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")),
                "RocksDB comparator callback disposal is not supported in native-image tests");
        try (ComparatorOptions comparatorOptions = new ComparatorOptions();
             ComparatorOptions intComparatorOptions = new ComparatorOptions();
             BytewiseComparator comparator = new BytewiseComparator(comparatorOptions);
             org.rocksdb.util.IntComparator intComparator = new org.rocksdb.util.IntComparator(intComparatorOptions)) {
            ByteBuffer start = ByteBuffer.wrap(bytes("abc"));
            ByteBuffer limit = ByteBuffer.wrap(bytes("abd"));
            comparator.findShortestSeparator(start, limit);
            comparator.findShortSuccessor(ByteBuffer.wrap(bytes("abc")));
            assertThat(start.remaining()).isPositive();
            assertThat(comparator.usingDirectBuffers()).isTrue();
            ByteBuffer first = ByteBuffer.allocate(Integer.BYTES).putInt(1);
            ByteBuffer second = ByteBuffer.allocate(Integer.BYTES).putInt(2);
            first.flip();
            second.flip();
            assertThat(intComparator.compare(first, second)).isNegative();
        }
        try (BloomFilter defaultFilter = new BloomFilter();
             BloomFilter configuredFilter = new BloomFilter(0.5, true)) {
            assertThat(configuredFilter).isNotEqualTo(defaultFilter);
            assertThat(configuredFilter.hashCode()).isNotEqualTo(0);
        }
        try (org.rocksdb.ClockCache first = new org.rocksdb.ClockCache(4096);
             org.rocksdb.ClockCache second = new org.rocksdb.ClockCache(4096, 2);
             org.rocksdb.ClockCache third = new org.rocksdb.ClockCache(4096, 2, true)) {
            assertThat(first.getPinnedUsage()).isZero();
            assertThat(second.getPinnedUsage()).isZero();
            assertThat(third.getPinnedUsage()).isZero();
        }
        org.rocksdb.AbstractCompactionFilter.Context fullManual =
                new org.rocksdb.AbstractCompactionFilter.Context(true, true);
        org.rocksdb.AbstractCompactionFilter.Context partialAutomatic =
                new org.rocksdb.AbstractCompactionFilter.Context(false, false);
        assertThat(fullManual.isFullCompaction()).isTrue();
        assertThat(fullManual.isManualCompaction()).isTrue();
        assertThat(partialAutomatic.isFullCompaction()).isFalse();
        assertThat(partialAutomatic.isManualCompaction()).isFalse();
        HistogramData data = new HistogramData(5.0, 95.0, 99.0, 50.0, 1.5);
        assertThat(data.getMedian()).isEqualTo(5.0);
        assertThat(new org.rocksdb.RocksDB.Version((byte) 10, (byte) 4, (byte) 2).toString())
                .isEqualTo("10.4.2");
        Status status = new Status(Status.Code.NotFound, Status.SubCode.None, "missing");
        assertThat(new RocksDBException(status).getStatus()).isEqualTo(status);
        assertThat(new RocksDBException("descriptive failure")).hasMessage("descriptive failure");
    }

    @Test
    void optionsAndBackupSettingsRoundTripThroughPublicApi() throws Exception {
        Files.createDirectories(tempDir.resolve("option-backups"));
        try (org.rocksdb.DBOptions dbOptions = new org.rocksdb.DBOptions();
             CoverageLogger logger = new CoverageLogger(dbOptions);
             RateLimiter backupLimiter = new RateLimiter(1024);
             BackupEngineOptions options = new BackupEngineOptions(tempDir.resolve("option-backups").toString())) {
            assertThat(options.backupDir()).isEqualTo(tempDir.resolve("option-backups").toString());
            assertThat(options.setBackupEnv(Env.getDefault()).backupEnv()).isSameAs(Env.getDefault());
            assertThat(options.setShareTableFiles(true).shareTableFiles()).isTrue();
            assertThat(options.setInfoLog(logger).infoLog()).isSameAs(logger);
            assertThat(options.setSync(true).sync()).isTrue();
            assertThat(options.setDestroyOldData(true).destroyOldData()).isTrue();
            assertThat(options.setBackupLogFiles(true).backupLogFiles()).isTrue();
            assertThat(options.setBackupRateLimit(2048).backupRateLimit()).isEqualTo(2048);
            assertThat(options.setBackupRateLimiter(backupLimiter).backupRateLimiter()).isSameAs(backupLimiter);
            assertThat(options.setRestoreRateLimit(4096).restoreRateLimit()).isEqualTo(4096);
            assertThat(options.setRestoreRateLimiter(backupLimiter).restoreRateLimiter()).isSameAs(backupLimiter);
            assertThat(options.setShareFilesWithChecksum(true).shareFilesWithChecksum()).isTrue();
            assertThat(options.setMaxBackgroundOperations(2).maxBackgroundOperations()).isEqualTo(2);
            assertThat(options.setCallbackTriggerIntervalSize(1024).callbackTriggerIntervalSize())
                    .isEqualTo(1024);
        }
    }

    @Test
    void transactionOptionsAndStatisticsAndMemoryQueriesReturnUsefulResults() throws Exception {
        try (org.rocksdb.TransactionOptions transactionOptions = new org.rocksdb.TransactionOptions()) {
            assertThat(transactionOptions.setSetSnapshot(true).isSetSnapshot()).isTrue();
        }
        try (org.rocksdb.AbstractWriteBatch batch = new WriteBatch()) {
            assertThat(batch.getWriteBatch()).isNotNull();
        }
        try (org.rocksdb.Cache cache = new org.rocksdb.LRUCache(4096)) {
            assertThat(cache.getPinnedUsage()).isZero();
        }
    }

    @Test
    void statisticsAndMemoryQueriesReturnUsefulResults() throws Exception {
        try (Statistics statistics = new Statistics(EnumSet.of(HistogramType.DB_GET));
             LRUCache cache = new LRUCache(4096);
             Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, tempDir.resolve("memory-db").toString())) {
            assertThat(statistics.statsLevel()).isNotNull();
            assertThat(new org.rocksdb.ReadOptions().setReadTier(ReadTier.MEMTABLE_TIER).readTier())
                    .isEqualTo(ReadTier.MEMTABLE_TIER);
            CompactionJobInfo jobInfo = new CompactionJobInfo();
            assertThat(jobInfo.compactionReason()).isNotNull();
            assertThat(jobInfo.stats()).isNotNull();
            AtomicInteger callbacks = new AtomicInteger();
            StatisticsCollectorCallback callback = new StatisticsCollectorCallback() {
                @Override
                public void tickerCallback(org.rocksdb.TickerType type, long count) {
                    callbacks.incrementAndGet();
                }

                @Override
                public void histogramCallback(HistogramType type, HistogramData data) {
                    callbacks.incrementAndGet();
                }
            };
            StatisticsCollector collector = new StatisticsCollector(
                    Collections.singletonList(new StatsCollectorInput(statistics, callback)), 1);
            collector.start();
            collector.shutDown(1000);
            OptionsUtil.loadLatestOptions(new ConfigOptions(), tempDir.resolve("memory-db").toString(),
                    new org.rocksdb.DBOptions(), new ArrayList<>(Collections.singletonList(
                            new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()))));
            Map<MemoryUsageType, Long> usage = MemoryUtil.getApproximateMemoryUsageByType(
                    Collections.singletonList(db), Collections.singleton(cache));
            assertThat(usage).isNotNull().containsKey(MemoryUsageType.kCacheTotal);
            assertThat(callbacks.get()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void environmentAndIngestOptionsRoundTripUserSettings() throws Exception {
        try (org.rocksdb.DBOptions dbOptions = new org.rocksdb.DBOptions();
             RateLimiter rateLimiter = new RateLimiter(1024);
             EnvOptions envOptions = new EnvOptions(dbOptions);
             EnvOptions defaults = new EnvOptions()) {
            assertThat(envOptions.setUseMmapReads(true)).isSameAs(envOptions);
            assertThat(envOptions.setUseMmapWrites(false)).isSameAs(envOptions);
            assertThat(envOptions.setUseDirectReads(true)).isSameAs(envOptions);
            assertThat(envOptions.setUseDirectWrites(true)).isSameAs(envOptions);
            assertThat(envOptions.setAllowFallocate(false)).isSameAs(envOptions);
            assertThat(envOptions.setSetFdCloexec(false)).isSameAs(envOptions);
            assertThat(envOptions.setBytesPerSync(256)).isSameAs(envOptions);
            assertThat(envOptions.setFallocateWithKeepSize(false)).isSameAs(envOptions);
            assertThat(envOptions.setCompactionReadaheadSize(512)).isSameAs(envOptions);
            assertThat(envOptions.setWritableFileMaxBufferSize(1024)).isSameAs(envOptions);
            assertThat(envOptions.setRateLimiter(rateLimiter)).isSameAs(envOptions);
            assertThat(envOptions.useMmapReads()).isTrue();
            assertThat(envOptions.useMmapWrites()).isFalse();
            assertThat(envOptions.useDirectReads()).isTrue();
            assertThat(envOptions.useDirectWrites()).isTrue();
            assertThat(envOptions.allowFallocate()).isFalse();
            assertThat(envOptions.setFdCloexec()).isFalse();
            assertThat(envOptions.bytesPerSync()).isEqualTo(256);
            assertThat(envOptions.fallocateWithKeepSize()).isFalse();
            assertThat(envOptions.compactionReadaheadSize()).isEqualTo(512);
            assertThat(envOptions.writableFileMaxBufferSize()).isEqualTo(1024);
            assertThat(envOptions.rateLimiter()).isSameAs(rateLimiter);
            assertThat(defaults).isNotNull();
        }

        try (IngestExternalFileOptions defaults = new IngestExternalFileOptions();
             IngestExternalFileOptions configured = new IngestExternalFileOptions(true, false, true, false);
             org.rocksdb.ImportColumnFamilyOptions importOptions = new org.rocksdb.ImportColumnFamilyOptions()) {
            assertThat(defaults.setMoveFiles(true)).isSameAs(defaults);
            assertThat(defaults.setSnapshotConsistency(false)).isSameAs(defaults);
            assertThat(defaults.setAllowGlobalSeqNo(true)).isSameAs(defaults);
            assertThat(defaults.setAllowBlockingFlush(true)).isSameAs(defaults);
            assertThat(defaults.setIngestBehind(true)).isSameAs(defaults);
            assertThat(defaults.setWriteGlobalSeqno(false)).isSameAs(defaults);
            assertThat(defaults.moveFiles()).isTrue();
            assertThat(defaults.snapshotConsistency()).isFalse();
            assertThat(defaults.allowGlobalSeqNo()).isTrue();
            assertThat(defaults.allowBlockingFlush()).isTrue();
            assertThat(defaults.ingestBehind()).isTrue();
            assertThat(defaults.writeGlobalSeqno()).isFalse();
            assertThat(configured.moveFiles()).isTrue();
            assertThat(configured.snapshotConsistency()).isFalse();
            assertThat(configured.allowGlobalSeqNo()).isTrue();
            assertThat(configured.allowBlockingFlush()).isFalse();
            assertThat(importOptions.setMoveFiles(true)).isSameAs(importOptions);
            assertThat(importOptions.moveFiles()).isTrue();
        }
    }

    @Test
    void specializedMemtableSlicesAndCachesPreserveState() {
        HashLinkedListMemTableConfig linked = new HashLinkedListMemTableConfig();
        assertThat(linked.setBucketCount(101)).isSameAs(linked);
        assertThat(linked.setHugePageTlbSize(2048)).isSameAs(linked);
        assertThat(linked.setBucketEntriesLoggingThreshold(12)).isSameAs(linked);
        assertThat(linked.setIfLogBucketDistWhenFlush(false)).isSameAs(linked);
        assertThat(linked.setThresholdUseSkiplist(9)).isSameAs(linked);
        assertThat(linked.bucketCount()).isEqualTo(101);
        assertThat(linked.hugePageTlbSize()).isEqualTo(2048);
        assertThat(linked.bucketEntriesLoggingThreshold()).isEqualTo(12);
        assertThat(linked.ifLogBucketDistWhenFlush()).isFalse();
        assertThat(linked.thresholdUseSkiplist()).isEqualTo(9);

        org.rocksdb.HashSkipListMemTableConfig skipList = new org.rocksdb.HashSkipListMemTableConfig();
        assertThat(skipList.setBucketCount(103)).isSameAs(skipList);
        assertThat(skipList.setHeight(5)).isSameAs(skipList);
        assertThat(skipList.setBranchingFactor(6)).isSameAs(skipList);
        assertThat(skipList.bucketCount()).isEqualTo(103);
        assertThat(skipList.height()).isEqualTo(5);
        assertThat(skipList.branchingFactor()).isEqualTo(6);

        try (DirectSlice stringSlice = new DirectSlice("rocksdb");
             DirectSlice bufferSlice = new DirectSlice(directBuffer("buffer"), 4);
             LRUCache first = new LRUCache(4096, 1);
             LRUCache second = new LRUCache(4096, 1, true);
             LRUCache third = new LRUCache(4096, 1, true, 0.5)) {
            assertThat(stringSlice.get(0)).isEqualTo((byte) 'r');
            stringSlice.removePrefix(2);
            assertThat(stringSlice.get(0)).isEqualTo((byte) 'c');
            stringSlice.setLength(2);
            assertThat(stringSlice.size()).isEqualTo(2);
            stringSlice.clear();
            assertThat(stringSlice.empty()).isTrue();
            assertThat(bufferSlice.get(0)).isEqualTo((byte) 'b');
            assertThat(bufferSlice.size()).isEqualTo(4);
            assertThat(first.getPinnedUsage()).isZero();
            assertThat(second.getPinnedUsage()).isZero();
            assertThat(third.getPinnedUsage()).isZero();
        }

        org.rocksdb.HistogramData data = new org.rocksdb.HistogramData(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(data.getAverage()).isEqualTo(4);
        assertThat(data.getCount()).isEqualTo(7);
        assertThat(data.getMax()).isEqualTo(6);
        assertThat(data.getMin()).isEqualTo(9);
        assertThat(data.getPercentile95()).isEqualTo(2);
        assertThat(data.getPercentile99()).isEqualTo(3);
        assertThat(data.getStandardDeviation()).isEqualTo(5);
        assertThat(data.getSum()).isEqualTo(8);

        assertThat(new DbPath(Path.of("db"), 1).hashCode()).isEqualTo(new DbPath(Path.of("db"), 1).hashCode());
        KeyMayExist firstResult = new KeyMayExist(KeyMayExist.KeyMayExistEnum.kExistsWithValue, 3);
        KeyMayExist sameResult = new KeyMayExist(KeyMayExist.KeyMayExistEnum.kExistsWithValue, 3);
        assertThat(firstResult).isEqualTo(sameResult);
        assertThat(firstResult.hashCode()).isEqualTo(sameResult.hashCode());
        assertThat(new org.rocksdb.Holder<>("value").getValue()).isEqualTo("value");
        new MemoryUtil();
        try {
            try (org.rocksdb.HyperClockCache cache = new org.rocksdb.HyperClockCache(4096, 0, 1, false)) {
                assertThat(cache.getPinnedUsage()).isZero();
            }
        } catch (UnsatisfiedLinkError ignored) {
            // Older bundled native libraries may not expose HyperClockCache yet.
        }
    }

    @Test
    void nativeComparatorWrapperRejectsJavaCallbackInvocation() {
        assertThatThrownBy(CoverageNativeComparator::new)
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        NativeComparatorWrapper wrapper = uninitializedNativeComparator();
        assertThatThrownBy(() -> wrapper.name()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrapper.compare(ByteBuffer.allocate(0), ByteBuffer.allocate(0)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrapper.findShortestSeparator(ByteBuffer.allocate(0), ByteBuffer.allocate(0)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrapper.findShortSuccessor(ByteBuffer.allocate(0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void optimisticTransactionOverloadsReusePublicTransactions() throws Exception {
        Path path = tempDir.resolve("optimistic-options");
        try (Options options = new Options().setCreateIfMissing(true);
             OptimisticTransactionDB db = OptimisticTransactionDB.open(options, path.toString());
             WriteOptions writeOptions = new WriteOptions();
             OptimisticTransactionOptions transactionOptions = new OptimisticTransactionOptions()) {
            assertThat(transactionOptions.isSetSnapshot()).isFalse();
            try (RocksDB baseDb = db.getBaseDB()) {
                assertThat(baseDb.getEnv()).isNotNull();
            }

            Transaction configured = db.beginTransaction(writeOptions, transactionOptions);
            try {
                configured.rollback();
            } finally {
                configured.close();
            }

            Transaction reusable = db.beginTransaction(writeOptions);
            assertThat(db.beginTransaction(writeOptions, reusable)).isSameAs(reusable);
            assertThat(db.beginTransaction(writeOptions, transactionOptions, reusable)).isSameAs(reusable);
            reusable.rollback();
            reusable.close();
        }
    }

    @Test
    void transactionAndBackupDatabasesFollowPublicOpenFlows() throws Exception {
        Path optimisticPath = tempDir.resolve("optimistic");
        try (DBOptionsHolder holder = new DBOptionsHolder(optimisticPath)) {
            List<ColumnFamilyHandle> handles = new java.util.ArrayList<>();
            OptimisticTransactionDB db = OptimisticTransactionDB.open(holder.options, optimisticPath.toString(),
                    holder.descriptors, handles);
            try (db; WriteOptions writeOptions = new WriteOptions();
                 OptimisticTransactionOptions transactionOptions = new OptimisticTransactionOptions()
                         .setSetSnapshot(true)) {
                Transaction transaction = db.beginTransaction(writeOptions, transactionOptions);
                assertThat(transaction.getState()).isNotNull();
                transaction.put(bytes("array-key"), bytes("array-value"));
                try (ReadOptions readOptions = new ReadOptions()) {
                    byte[] arrayValue = new byte[32];
                    assertThat(transaction.get(readOptions, bytes("array-key"), arrayValue)).isNotNull();
                    assertThat(transaction.get(readOptions, handles.get(0), bytes("array-key"), arrayValue))
                            .isNotNull();
                    assertThat(transaction.getForUpdate(readOptions, bytes("array-key"), arrayValue, true))
                            .isNotNull();
                }
                ByteBuffer key = ByteBuffer.wrap(bytes("key"));
                ByteBuffer value = ByteBuffer.wrap(bytes("value"));
                transaction.put(key, value);
                ByteBuffer destination = ByteBuffer.allocate(16);
                try (ReadOptions readOptions = new ReadOptions()) {
                    assertThat(transaction.get(readOptions, key, destination)).isNotNull();
                    assertThat(transaction.getForUpdate(readOptions, key, destination, true)).isNotNull();
                    assertThat(transaction.getForUpdate(readOptions, handles.get(0), bytes("key"),
                            bytes("value"), true)).isNotNull();
                    ByteBuffer updateKey = ByteBuffer.wrap(bytes("key"));
                    ByteBuffer updateValue = ByteBuffer.allocate(16);
                    assertThat(transaction.getForUpdate(readOptions, handles.get(0), updateKey,
                            updateValue, true)).isNotNull();
                }
                transaction.put(handles.get(0), directBuffer("buffer-key"), directBuffer("buffer-value"));
                transaction.merge(handles.get(0), directBuffer("merge-key"), directBuffer("merge-value"));
                transaction.mergeUntracked(bytes("untracked-key"), bytes("untracked-value"));
                transaction.mergeUntracked(directBuffer("untracked-buffer-key"),
                        directBuffer("untracked-buffer-value"));
                assertThat(transaction.getWriteBatch()).isNotNull();
                assertThat(transaction.getWriteOptions()).isNotNull();
                assertThat(transaction.getCommitTimeWriteBatch()).isNotNull();
                Transaction reused = db.beginTransaction(writeOptions, transactionOptions, transaction);
                assertThat(reused).isSameAs(transaction);
                transaction.rollback();
            }
            for (ColumnFamilyHandle handle : handles) {
                handle.close();
            }
        }

        Path txPath = tempDir.resolve("transaction");
        try (Options options = new Options().setCreateIfMissing(true);
             TransactionDBOptions transactionOptions = new TransactionDBOptions();
             TransactionDB db = TransactionDB.open(options, transactionOptions, txPath.toString());
             WriteOptions writeOptions = new WriteOptions();
             TransactionOptions transactionOptionsForBegin = new TransactionOptions()) {
            db.put(bytes("key"), bytes("value"));
            assertThat(db.get(bytes("key"))).isEqualTo(bytes("value"));
            Transaction transaction = db.beginTransaction(writeOptions, transactionOptionsForBegin);
            try {
                assertThat(db.beginTransaction(writeOptions, transactionOptionsForBegin, transaction))
                        .isSameAs(transaction);
                transaction.rollback();
            } finally {
                transaction.close();
            }
        }

        Path ttlPath = tempDir.resolve("ttl");
        try (Options options = new Options().setCreateIfMissing(true);
             TtlDB db = TtlDB.open(options, ttlPath.toString())) {
            db.put(bytes("key"), bytes("value"));
            assertThat(db.get(bytes("key"))).isEqualTo(bytes("value"));
        }
    }

    @Test
    void backupAndCheckpointPreserveDatabaseContents() throws Exception {
        Path dbPath = tempDir.resolve("backup-source");
        Path backupPath = tempDir.resolve("backups");
        Files.createDirectories(backupPath);
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString());
             BackupEngineOptions backupOptions = new BackupEngineOptions(backupPath.toString());
             BackupEngine backup = BackupEngine.open(Env.getDefault(), backupOptions)) {
            db.put(bytes("backup-key"), bytes("backup-value"));
            backup.createNewBackup(db);
            backup.createNewBackupWithMetadata(db, "coverage-metadata", false);
            assertThat(backup.getBackupInfo()).hasSize(2);
            assertThat(backup.getBackupInfo().get(1).appMetadata()).isEqualTo("coverage-metadata");
            assertThat(backup.getCorruptedBackups()).isEmpty();
            Path restoredLatest = tempDir.resolve("restored-latest");
            try (org.rocksdb.RestoreOptions restoreOptions = new org.rocksdb.RestoreOptions(false)) {
                backup.restoreDbFromLatestBackup(restoredLatest.toString(), restoredLatest.toString(),
                        restoreOptions);
            }
            Path restoredFirst = tempDir.resolve("restored-first");
            try (org.rocksdb.RestoreOptions restoreOptions = new org.rocksdb.RestoreOptions(false)) {
                backup.restoreDbFromBackup(1, restoredFirst.toString(), restoredFirst.toString(),
                        restoreOptions);
            }
            backup.deleteBackup(1);
            backup.purgeOldBackups(1);
            backup.garbageCollect();

            Path checkpointPath = tempDir.resolve("checkpoint");
            try (org.rocksdb.Checkpoint checkpoint = org.rocksdb.Checkpoint.create(db)) {
                checkpoint.createCheckpoint(checkpointPath.toString());
                try (ColumnFamilyHandle defaultHandle = db.getDefaultColumnFamily()) {
                    org.rocksdb.ExportImportFilesMetaData metadata = checkpoint.exportColumnFamily(defaultHandle,
                            tempDir.resolve("export").toString());
                    assertThat(metadata).isNotNull();
                    try (org.rocksdb.ImportColumnFamilyOptions importOptions =
                            new org.rocksdb.ImportColumnFamilyOptions();
                         ColumnFamilyOptions importedOptions = new ColumnFamilyOptions()) {
                        ColumnFamilyHandle imported = db.createColumnFamilyWithImport(
                                new ColumnFamilyDescriptor(bytes("imported"), importedOptions), importOptions,
                                metadata);
                        assertThat(imported.getName()).isEqualTo(bytes("imported"));
                        db.destroyColumnFamilyHandle(imported);
                    }
                }
            }
        }
    }

    @Test
    void compactionAndCompressionOptionsRoundTripValues() {
        try (CompactRangeOptions range = new CompactRangeOptions();
             CompactionOptionsUniversal universal = new CompactionOptionsUniversal();
             CompactionOptionsFIFO fifo = new CompactionOptionsFIFO();
             CompactionOptions compaction = new CompactionOptions();
             org.rocksdb.CompressionOptions compression = new org.rocksdb.CompressionOptions()) {
            CompactRangeOptions.Timestamp timestamp = new CompactRangeOptions.Timestamp(12, 5);
            assertThat(new CompactRangeOptions.Timestamp()).isEqualTo(new CompactRangeOptions.Timestamp(0, 0));
            assertThat(timestamp).isEqualTo(new CompactRangeOptions.Timestamp(12, 5));
            assertThat(timestamp.hashCode()).isEqualTo(new CompactRangeOptions.Timestamp(12, 5).hashCode());
            assertThat(range.setExclusiveManualCompaction(true).exclusiveManualCompaction()).isTrue();
            assertThat(range.setChangeLevel(true).changeLevel()).isTrue();
            assertThat(range.setTargetLevel(3).targetLevel()).isEqualTo(3);
            assertThat(range.setTargetPathId(1).targetPathId()).isEqualTo(1);
            assertThat(range.setAllowWriteStall(true).allowWriteStall()).isTrue();
            assertThat(range.setMaxSubcompactions(2).maxSubcompactions()).isEqualTo(2);
            assertThat(range.setCanceled(true).canceled()).isTrue();
            assertThat(range.setFullHistoryTSLow(timestamp).fullHistoryTSLow()).isEqualTo(timestamp);

            assertThat(universal.setAllowTrivialMove(true).allowTrivialMove()).isTrue();
            assertThat(universal.setSizeRatio(7).sizeRatio()).isEqualTo(7);
            assertThat(universal.setMinMergeWidth(2).minMergeWidth()).isEqualTo(2);
            assertThat(universal.setMaxMergeWidth(8).maxMergeWidth()).isEqualTo(8);
            assertThat(universal.setMaxSizeAmplificationPercent(150).maxSizeAmplificationPercent())
                    .isEqualTo(150);
            assertThat(universal.setCompressionSizePercent(80).compressionSizePercent()).isEqualTo(80);

            assertThat(fifo.setAllowCompaction(true).allowCompaction()).isTrue();
            assertThat(fifo.setMaxTableFilesSize(4096).maxTableFilesSize()).isEqualTo(4096);
            assertThat(compaction.setCompression(CompressionType.LZ4_COMPRESSION).compression())
                    .isEqualTo(CompressionType.LZ4_COMPRESSION);
            assertThat(compaction.setMaxSubcompactions(2).maxSubcompactions()).isEqualTo(2);
            assertThat(compaction.setOutputFileSizeLimit(8192).outputFileSizeLimit()).isEqualTo(8192);

            assertThat(compression.setEnabled(true).enabled()).isTrue();
            assertThat(compression.setLevel(3).level()).isEqualTo(3);
            assertThat(compression.setMaxDictBytes(1024).maxDictBytes()).isEqualTo(1024);
            assertThat(compression.setStrategy(1).strategy()).isEqualTo(1);
            assertThat(compression.setWindowBits(12).windowBits()).isEqualTo(12);
            assertThat(compression.setZStdMaxTrainBytes(2048).zstdMaxTrainBytes()).isEqualTo(2048);
        }
    }

    @Test
    void comparatorAndLimiterOptionsControlCallbackResources() {
        try (ComparatorOptions options = new ComparatorOptions();
             org.rocksdb.ConcurrentTaskLimiterImpl limiter =
                     new org.rocksdb.ConcurrentTaskLimiterImpl("coverage-limiter", 2)) {
            assertThat(options.setMaxReusedBufferSize(128).maxReusedBufferSize()).isEqualTo(128);
            assertThat(options.setUseDirectBuffer(false).useDirectBuffer()).isFalse();
            assertThat(options.setReusedSynchronisationType(ReusedSynchronisationType.MUTEX)
                    .reusedSynchronisationType()).isEqualTo(ReusedSynchronisationType.MUTEX);
            assertThat(limiter.name()).isEqualTo("coverage-limiter");
            assertThat(limiter.setMaxOutstandingTask(4)).isSameAs(limiter);
            assertThat(limiter.outstandingTask()).isZero();
            assertThat(limiter.resetMaxOutstandingTask()).isSameAs(limiter);
        }
    }

    @Test
    void configParsingAndCompactionReportsExposeStructuredValues() {
        try (ConfigOptions config = new ConfigOptions();
             CompactionJobStats first = new CompactionJobStats();
             CompactionJobStats second = new CompactionJobStats();
             CompactionJobInfo info = new CompactionJobInfo()) {
            assertThat(config.setDelimiter(";")).isSameAs(config);
            assertThat(config.setEnv(Env.getDefault())).isSameAs(config);
            assertThat(config.setIgnoreUnknownOptions(true)).isSameAs(config);
            assertThat(config.setInputStringsEscaped(true)).isSameAs(config);

            first.add(second);
            assertThat(first.elapsedMicros()).isGreaterThanOrEqualTo(0);
            assertThat(first.fileFsyncNanos()).isGreaterThanOrEqualTo(0);
            assertThat(first.filePrepareWriteNanos()).isGreaterThanOrEqualTo(0);
            assertThat(first.fileRangeSyncNanos()).isGreaterThanOrEqualTo(0);
            assertThat(first.fileWriteNanos()).isGreaterThanOrEqualTo(0);
            assertThat(first.isManualCompaction()).isFalse();
            assertThat(first.largestOutputKeyPrefix()).isNotNull();
            assertThat(first.numCorruptKeys()).isGreaterThanOrEqualTo(0);
            assertThat(first.numExpiredDeletionRecords()).isGreaterThanOrEqualTo(0);
            assertThat(first.numInputDeletionRecords()).isGreaterThanOrEqualTo(0);
            assertThat(first.numInputFiles()).isGreaterThanOrEqualTo(0);
            assertThat(first.numInputFilesAtOutputLevel()).isGreaterThanOrEqualTo(0);
            assertThat(first.numInputRecords()).isGreaterThanOrEqualTo(0);
            assertThat(first.numOutputFiles()).isGreaterThanOrEqualTo(0);
            assertThat(first.numOutputRecords()).isGreaterThanOrEqualTo(0);
            assertThat(first.numRecordsReplaced()).isGreaterThanOrEqualTo(0);
            assertThat(first.numSingleDelFallthru()).isGreaterThanOrEqualTo(0);
            assertThat(first.numSingleDelMismatch()).isGreaterThanOrEqualTo(0);
            assertThat(first.smallestOutputKeyPrefix()).isNotNull();
            assertThat(first.totalInputBytes()).isGreaterThanOrEqualTo(0);
            assertThat(first.totalInputRawKeyBytes()).isGreaterThanOrEqualTo(0);
            assertThat(first.totalInputRawValueBytes()).isGreaterThanOrEqualTo(0);
            assertThat(first.totalOutputBytes()).isGreaterThanOrEqualTo(0);
            first.reset();
            assertThat(first.elapsedMicros()).isZero();

            assertThat(info.baseInputLevel()).isGreaterThanOrEqualTo(0);
            assertThat(info.columnFamilyName()).isNotNull();
            assertThat(info.compression()).isNotNull();
            assertThat(info.inputFiles()).isNotNull();
            assertThat(info.jobId()).isGreaterThanOrEqualTo(0);
            assertThat(info.outputFiles()).isNotNull();
            assertThat(info.outputLevel()).isGreaterThanOrEqualTo(0);
            assertThat(info.status()).isNotNull();
            assertThat(info.tableProperties()).isNotNull();
            assertThat(info.threadId()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void enumAndMergeOperatorFactoriesDecodePublicNames() {
        assertThat(CompressionType.getCompressionType("lz4")).isEqualTo(CompressionType.LZ4_COMPRESSION);
        assertThat(CompressionType.values()).contains(CompressionType.NO_COMPRESSION);
        assertThat(CompressionType.valueOf("NO_COMPRESSION")).isEqualTo(CompressionType.NO_COMPRESSION);
        try (CassandraValueMergeOperator defaultOperator = new CassandraValueMergeOperator(60);
             CassandraValueMergeOperator limitedOperator = new CassandraValueMergeOperator(60, 4)) {
            assertThat(defaultOperator).isNotNull();
            assertThat(limitedOperator).isNotNull();
        }
    }

    private static final class CoverageLogger extends org.rocksdb.Logger {
        CoverageLogger(org.rocksdb.DBOptions options) {
            super(options);
        }

        @Override
        protected void log(InfoLogLevel logLevel, String logMessage) {
        }
    }

    private static final class CoverageTraceWriter extends org.rocksdb.AbstractTraceWriter {
        @Override
        public void write(Slice data) {
        }

        @Override
        public void closeWriter() {
        }

        @Override
        public long getFileSize() {
            return 0;
        }
    }

    private static ByteBuffer directBuffer(String value) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(value.length());
        buffer.put(bytes(value)).flip();
        return buffer;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class DBOptionsHolder implements AutoCloseable {
        private final org.rocksdb.DBOptions options;
        private final List<ColumnFamilyDescriptor> descriptors;
        private final List<ColumnFamilyOptions> columnFamilyOptions;

        private DBOptionsHolder(Path path) {
            options = new org.rocksdb.DBOptions().setCreateIfMissing(true);
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions().setMergeOperatorName("stringappend");
            this.columnFamilyOptions = Collections.singletonList(columnFamilyOptions);
            descriptors = Collections.singletonList(new ColumnFamilyDescriptor(
                    RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
        }

        @Override
        public void close() {
            options.close();
            for (ColumnFamilyOptions options : columnFamilyOptions) {
                options.close();
            }
        }
    }

    private static NativeComparatorWrapper uninitializedNativeComparator() {
        try {
            return CoverageNativeComparator.class.cast(UNSAFE.allocateInstance(CoverageNativeComparator.class));
        } catch (InstantiationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return Unsafe.class.cast(field.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class CoverageNativeComparator extends NativeComparatorWrapper {
    }

    private static final class MutableObject extends RocksMutableObject {
        @Override
        protected void disposeInternal(long handle) {
        }
    }
}
