/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.AbstractCompactionFilter;
import org.rocksdb.AbstractCompactionFilterFactory;
import org.rocksdb.AdvancedColumnFamilyOptionsInterface;
import org.rocksdb.AdvancedMutableColumnFamilyOptionsInterface;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BuiltinComparator;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.ColumnFamilyOptionsInterface;
import org.rocksdb.LRUCache;
import org.rocksdb.CompactionOptionsFIFO;
import org.rocksdb.CompactionOptionsUniversal;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompactionStyle;
import org.rocksdb.ConcurrentTaskLimiterImpl;
import org.rocksdb.CompressionOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.HashSkipListMemTableConfig;
import org.rocksdb.MemTableConfig;
import org.rocksdb.MutableColumnFamilyOptionsInterface;
import org.rocksdb.PrepopulateBlobCache;
import org.rocksdb.RemoveEmptyValueCompactionFilter;
import org.rocksdb.SstPartitionerFixedPrefixFactory;
import org.rocksdb.StringAppendOperator;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ColumnFamilyOptionsApiTest {

    @Test
    void columnFamilyInterfacesConfigureAndReadBackSettings() {
        try (ColumnFamilyOptions options = new ColumnFamilyOptions();
             SstPartitionerFixedPrefixFactory partitioner = new SstPartitionerFixedPrefixFactory(2);
             LRUCache cache = new LRUCache(1024 * 1024);
             ConcurrentTaskLimiterImpl limiter = new ConcurrentTaskLimiterImpl("coverage", 2);
             StringAppendOperator mergeOperator = new StringAppendOperator(',')) {
            MemTableConfig memTable = new HashSkipListMemTableConfig();
            BlockBasedTableConfig table = new BlockBasedTableConfig();
            AbstractCompactionFilter<?> filter = new RemoveEmptyValueCompactionFilter();
            TestFilterFactory filterFactory = new TestFilterFactory();
            CompressionOptions compression = new CompressionOptions();
            CompressionOptions bottommostCompression = new CompressionOptions();
            CompactionOptionsFIFO fifo = new CompactionOptionsFIFO();
            CompactionOptionsUniversal universal = new CompactionOptionsUniversal();
            ColumnFamilyOptionsInterface<ColumnFamilyOptions> columnFamily = options;
            AdvancedColumnFamilyOptionsInterface<ColumnFamilyOptions> advanced = options;
            MutableColumnFamilyOptionsInterface<ColumnFamilyOptions> mutable = options;
            AdvancedMutableColumnFamilyOptionsInterface<ColumnFamilyOptions> advancedMutable = options;

            assertThat(columnFamily.setComparator(BuiltinComparator.REVERSE_BYTEWISE_COMPARATOR))
                    .isSameAs(options);
            assertThat(columnFamily.setComparator(BuiltinComparator.BYTEWISE_COMPARATOR)).isSameAs(options);
            assertThat(columnFamily.oldDefaults(6, 7)).isSameAs(options);
            assertThat(columnFamily.optimizeForPointLookup(8L)).isSameAs(options);
            assertThat(columnFamily.optimizeForSmallDb()).isSameAs(options);
            assertThat(columnFamily.optimizeForSmallDb(cache)).isSameAs(options);
            assertThat(columnFamily.optimizeLevelStyleCompaction()).isSameAs(options);
            assertThat(columnFamily.optimizeLevelStyleCompaction(9L)).isSameAs(options);
            assertThat(columnFamily.optimizeUniversalStyleCompaction()).isSameAs(options);
            assertThat(columnFamily.optimizeUniversalStyleCompaction(10L)).isSameAs(options);
            assertThat(advancedMutable.setArenaBlockSize(11L)).isSameAs(options);
            assertThat(advancedMutable.setBlobCompactionReadaheadSize(12L)).isSameAs(options);
            assertThat(advancedMutable.setBlobCompressionType(CompressionType.NO_COMPRESSION))
                    .isSameAs(options);
            assertThat(advancedMutable.setBlobFileSize(13L)).isSameAs(options);
            assertThat(advancedMutable.setBlobFileStartingLevel(14)).isSameAs(options);
            assertThat(advancedMutable.setBlobGarbageCollectionAgeCutoff(0.15)).isSameAs(options);
            assertThat(advancedMutable.setBlobGarbageCollectionForceThreshold(0.16))
                    .isSameAs(options);
            assertThat(advanced.setBloomLocality(17)).isSameAs(options);
            assertThat(columnFamily.setBottommostCompressionOptions(bottommostCompression))
                    .isSameAs(options);
            assertThat(columnFamily.setBottommostCompressionType(CompressionType.NO_COMPRESSION))
                    .isSameAs(options);
            assertThat(columnFamily.setCfPaths(Collections.emptyList())).isSameAs(options);
            assertThat(columnFamily.setCompactionFilter(filter)).isSameAs(options);
            assertThat(columnFamily.setCompactionFilterFactory(filterFactory)).isSameAs(options);
            assertThat(advanced.setCompactionOptionsFIFO(fifo)).isSameAs(options);
            assertThat(advanced.setCompactionOptionsUniversal(universal)).isSameAs(options);
            assertThat(advanced.setCompactionPriority(CompactionPriority.RoundRobin))
                    .isSameAs(options);
            assertThat(columnFamily.setCompactionStyle(CompactionStyle.FIFO)).isSameAs(options);
            assertThat(columnFamily.setCompactionThreadLimiter(limiter)).isSameAs(options);
            assertThat(columnFamily.setCompressionOptions(compression)).isSameAs(options);
            assertThat(advanced.setCompressionPerLevel(Arrays.asList(CompressionType.NO_COMPRESSION)))
                    .isSameAs(options);
            assertThat(mutable.setCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(options);
            assertThat(mutable.setDisableAutoCompactions(true)).isSameAs(options);
            assertThat(advancedMutable.setEnableBlobFiles(true)).isSameAs(options);
            assertThat(advancedMutable.setEnableBlobGarbageCollection(true)).isSameAs(options);
            assertThat(advancedMutable.setExperimentalMempurgeThreshold(0.18)).isSameAs(options);
            assertThat(advanced.setForceConsistencyChecks(true)).isSameAs(options);
            assertThat(advancedMutable.setHardPendingCompactionBytesLimit(19L)).isSameAs(options);
            assertThat(advancedMutable.setInplaceUpdateNumLocks(20L)).isSameAs(options);
            assertThat(advanced.setInplaceUpdateSupport(true)).isSameAs(options);
            assertThat(mutable.setLevel0FileNumCompactionTrigger(21)).isSameAs(options);
            assertThat(advancedMutable.setLevel0SlowdownWritesTrigger(22)).isSameAs(options);
            assertThat(advancedMutable.setLevel0StopWritesTrigger(23)).isSameAs(options);
            assertThat(advanced.setLevelCompactionDynamicLevelBytes(true)).isSameAs(options);
            assertThat(columnFamily.setLevelZeroFileNumCompactionTrigger(24)).isSameAs(options);
            assertThat(columnFamily.setLevelZeroSlowdownWritesTrigger(25)).isSameAs(options);
            assertThat(columnFamily.setLevelZeroStopWritesTrigger(26)).isSameAs(options);
            assertThat(mutable.setMaxBytesForLevelBase(27L)).isSameAs(options);
            assertThat(advancedMutable.setMaxBytesForLevelMultiplier(2.8)).isSameAs(options);
            assertThat(columnFamily.setMaxBytesForLevelMultiplier(2.9)).isSameAs(options);
            assertThat(advancedMutable.setMaxBytesForLevelMultiplierAdditional(new int[]{3, 4}))
                    .isSameAs(options);
            assertThat(advanced.setMaxCompactionBytes(28L)).isSameAs(options);
            assertThat(mutable.setMaxCompactionBytes(29L)).isSameAs(options);
            assertThat(advancedMutable.setMaxSequentialSkipInIterations(30L)).isSameAs(options);
            assertThat(advancedMutable.setMaxSuccessiveMerges(31L)).isSameAs(options);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(32L)).isSameAs(options);
            assertThat(advancedMutable.setMaxWriteBufferNumber(33)).isSameAs(options);
            assertThat(columnFamily.setMemTableConfig(memTable)).isSameAs(options);
            assertThat(advancedMutable.setMemtableHugePageSize(34L)).isSameAs(options);
            assertThat(columnFamily.setMemtableMaxRangeDeletions(35)).isSameAs(options);
            assertThat(advancedMutable.setMemtablePrefixBloomSizeRatio(0.36)).isSameAs(options);
            assertThat(advancedMutable.setMemtableWholeKeyFiltering(false)).isSameAs(options);
            assertThat(columnFamily.setMergeOperator(mergeOperator)).isSameAs(options);
            assertThat(columnFamily.setMergeOperatorName("stringappend")).isSameAs(options);
            assertThat(advancedMutable.setMinBlobSize(37L)).isSameAs(options);
            assertThat(advanced.setMinWriteBufferNumberToMerge(38)).isSameAs(options);
            assertThat(advanced.setNumLevels(39)).isSameAs(options);
            assertThat(advanced.setOptimizeFiltersForHits(true)).isSameAs(options);
            assertThat(advancedMutable.setParanoidFileChecks(true)).isSameAs(options);
            assertThat(advancedMutable.setPeriodicCompactionSeconds(40L)).isSameAs(options);
            assertThat(advancedMutable.setPrepopulateBlobCache(
                    PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY)).isSameAs(options);
            assertThat(advancedMutable.setReportBgIoStats(true)).isSameAs(options);
            assertThat(advancedMutable.setSoftPendingCompactionBytesLimit(41L)).isSameAs(options);
            assertThat(columnFamily.setSstPartitionerFactory(partitioner)).isSameAs(options);
            assertThat(columnFamily.setTableFormatConfig(table)).isSameAs(options);
            assertThat(advancedMutable.setTargetFileSizeBase(42L)).isSameAs(options);
            assertThat(advancedMutable.setTargetFileSizeMultiplier(43)).isSameAs(options);
            assertThat(advancedMutable.setTtl(44L)).isSameAs(options);
            assertThat(mutable.setWriteBufferSize(45L)).isSameAs(options);
            assertThat(columnFamily.useCappedPrefixExtractor(2)).isSameAs(options);
            assertThat(columnFamily.useFixedLengthPrefixExtractor(3)).isSameAs(options);
            assertThat(columnFamily.setMaxTableFilesSizeFIFO(32L)).isSameAs(options);

            assertThat(options.compressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.compressionPerLevel()).containsExactly(CompressionType.NO_COMPRESSION);
            assertThat(options.bottommostCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.bottommostCompressionOptions()).isNotNull();
            assertThat(options.compressionOptions()).isNotNull();
            assertThat(options.memTableConfig()).isNotNull();
            assertThat(options.tableFormatConfig()).isNotNull();
            assertThat(options.cfPaths()).isEmpty();
            assertThat(options.compactionFilter()).isNotNull();
            assertThat(options.compactionFilterFactory()).isNotNull();
            assertThat(options.compactionOptionsFIFO()).isNotNull();
            assertThat(options.compactionOptionsUniversal()).isNotNull();
            assertThat(options.compactionPriority()).isEqualTo(CompactionPriority.RoundRobin);
            assertThat(options.compactionStyle()).isEqualTo(CompactionStyle.FIFO);
            assertThat(options.compactionThreadLimiter()).isNotNull();
            assertThat(options.sstPartitionerFactory()).isNotNull();
            assertThat(options.arenaBlockSize()).isEqualTo(11L);
            assertThat(options.blobCompactionReadaheadSize()).isEqualTo(12L);
            assertThat(options.blobCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
            assertThat(options.blobFileSize()).isEqualTo(13L);
            assertThat(options.blobFileStartingLevel()).isEqualTo(14);
            assertThat(options.blobGarbageCollectionAgeCutoff()).isEqualTo(0.15);
            assertThat(options.blobGarbageCollectionForceThreshold()).isEqualTo(0.16);
            assertThat(options.bloomLocality()).isEqualTo(17);
            assertThat(options.disableAutoCompactions()).isTrue();
            assertThat(options.enableBlobFiles()).isTrue();
            assertThat(options.enableBlobGarbageCollection()).isTrue();
            assertThat(options.experimentalMempurgeThreshold()).isEqualTo(0.18);
            assertThat(options.forceConsistencyChecks()).isTrue();
            assertThat(options.hardPendingCompactionBytesLimit()).isEqualTo(19L);
            assertThat(options.inplaceUpdateNumLocks()).isEqualTo(20L);
            assertThat(options.inplaceUpdateSupport()).isTrue();
            assertThat(options.level0FileNumCompactionTrigger()).isEqualTo(24);
            assertThat(options.level0SlowdownWritesTrigger()).isEqualTo(25);
            assertThat(options.level0StopWritesTrigger()).isEqualTo(26);
            assertThat(options.levelCompactionDynamicLevelBytes()).isTrue();
            assertThat(options.levelZeroFileNumCompactionTrigger()).isEqualTo(24);
            assertThat(options.levelZeroSlowdownWritesTrigger()).isEqualTo(25);
            assertThat(options.levelZeroStopWritesTrigger()).isEqualTo(26);
            assertThat(options.maxBytesForLevelBase()).isEqualTo(27L);
            assertThat(options.maxBytesForLevelMultiplier()).isEqualTo(2.9);
            assertThat(options.maxBytesForLevelMultiplierAdditional()).containsExactly(3, 4);
            assertThat(options.maxCompactionBytes()).isEqualTo(29L);
            assertThat(options.maxSequentialSkipInIterations()).isEqualTo(30L);
            assertThat(options.maxSuccessiveMerges()).isEqualTo(31L);
            assertThat(options.maxTableFilesSizeFIFO()).isEqualTo(32L);
            assertThat(options.maxWriteBufferNumber()).isEqualTo(33);
            assertThat(options.memtableHugePageSize()).isEqualTo(34L);
            assertThat(options.memtableMaxRangeDeletions()).isEqualTo(35);
            assertThat(options.memtablePrefixBloomSizeRatio()).isEqualTo(0.36);
            assertThat(options.memtableWholeKeyFiltering()).isFalse();
            assertThat(options.minBlobSize()).isEqualTo(37L);
            assertThat(options.minWriteBufferNumberToMerge()).isEqualTo(38);
            assertThat(options.numLevels()).isEqualTo(39);
            assertThat(options.optimizeFiltersForHits()).isTrue();
            assertThat(options.paranoidFileChecks()).isTrue();
            assertThat(options.periodicCompactionSeconds()).isEqualTo(40L);
            assertThat(options.prepopulateBlobCache())
                    .isEqualTo(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);
            assertThat(options.reportBgIoStats()).isTrue();
            assertThat(options.softPendingCompactionBytesLimit()).isEqualTo(41L);
            assertThat(options.targetFileSizeBase()).isEqualTo(42L);
            assertThat(options.targetFileSizeMultiplier()).isEqualTo(43);
            assertThat(options.ttl()).isEqualTo(44L);
            assertThat(options.writeBufferSize()).isEqualTo(45L);
        }
    }

    private static final class TestFilterFactory extends AbstractCompactionFilterFactory<AbstractCompactionFilter<?>> {
        @Override
        public AbstractCompactionFilter<?> createCompactionFilter(AbstractCompactionFilter.Context context) {
            return new RemoveEmptyValueCompactionFilter();
        }

        @Override
        public String name() {
            return "coverage-filter-factory";
        }
    }
}
