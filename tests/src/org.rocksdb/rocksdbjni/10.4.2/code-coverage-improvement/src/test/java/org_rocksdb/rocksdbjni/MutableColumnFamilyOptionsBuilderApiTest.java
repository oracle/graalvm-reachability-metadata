/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.AdvancedMutableColumnFamilyOptionsInterface;
import org.rocksdb.CompressionType;
import org.rocksdb.MutableColumnFamilyOptions;
import org.rocksdb.MutableColumnFamilyOptionsInterface;
import org.rocksdb.PrepopulateBlobCache;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MutableColumnFamilyOptionsBuilderApiTest {

    @Test
    void parsedValuesAreReturnedByPublicGetters() {
        MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder builder =
                MutableColumnFamilyOptions.parse(
                        "blob_garbage_collection_age_cutoff=0.75;"
                                + "blob_file_starting_level=3.0;"
                                + "max_bytes_for_level_multiplier_additional=1:2:3;"
                                + "arena_block_size=4096.0;disable_auto_compactions=false");

        assertThat(builder.blobGarbageCollectionAgeCutoff()).isEqualTo(0.75);
        assertThat(builder.blobFileStartingLevel()).isEqualTo(3);
        assertThat(builder.maxBytesForLevelMultiplierAdditional()).containsExactly(1, 2, 3);
        assertThat(builder.arenaBlockSize()).isEqualTo(4096);
        assertThat(builder.disableAutoCompactions()).isFalse();
        assertThat(builder.build().toString())
                .isEqualTo("blob_garbage_collection_age_cutoff=0.75;"
                        + "blob_file_starting_level=3;"
                        + "max_bytes_for_level_multiplier_additional=1:2:3;"
                        + "arena_block_size=4096;disable_auto_compactions=false");
    }

    @Test
    void builderRoundTripsMutableColumnFamilySettings() {
        MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder builder =
                MutableColumnFamilyOptions.builder();
        MutableColumnFamilyOptionsInterface<MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder>
                mutable = builder;
        AdvancedMutableColumnFamilyOptionsInterface<MutableColumnFamilyOptions.MutableColumnFamilyOptionsBuilder>
                advanced = builder;

        assertThat(mutable.setWriteBufferSize(101L)).isSameAs(builder);
        assertThat(advanced.setArenaBlockSize(102L)).isSameAs(builder);
        assertThat(advanced.setMemtablePrefixBloomSizeRatio(0.03)).isSameAs(builder);
        assertThat(advanced.setMemtableWholeKeyFiltering(false)).isSameAs(builder);
        assertThat(advanced.setMemtableHugePageSize(103L)).isSameAs(builder);
        assertThat(advanced.setBlobCompactionReadaheadSize(117L)).isSameAs(builder);
        assertThat(advanced.setBlobCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(builder);
        assertThat(advanced.setBlobFileSize(118L)).isSameAs(builder);
        assertThat(advanced.setBlobFileStartingLevel(19)).isSameAs(builder);
        assertThat(advanced.setBlobGarbageCollectionAgeCutoff(0.20)).isSameAs(builder);
        assertThat(advanced.setBlobGarbageCollectionForceThreshold(0.21)).isSameAs(builder);
        assertThat(advanced.setMaxSuccessiveMerges(104L)).isSameAs(builder);
        assertThat(advanced.setMaxWriteBufferNumber(5)).isSameAs(builder);
        assertThat(advanced.setInplaceUpdateNumLocks(105L)).isSameAs(builder);
        assertThat(advanced.setExperimentalMempurgeThreshold(0.06)).isSameAs(builder);
        assertThat(advanced.setEnableBlobFiles(true)).isSameAs(builder);
        assertThat(advanced.setEnableBlobGarbageCollection(true)).isSameAs(builder);
        assertThat(mutable.setDisableAutoCompactions(true)).isSameAs(builder);
        assertThat(advanced.setSoftPendingCompactionBytesLimit(106L)).isSameAs(builder);
        assertThat(advanced.setHardPendingCompactionBytesLimit(107L)).isSameAs(builder);
        assertThat(mutable.setLevel0FileNumCompactionTrigger(7)).isSameAs(builder);
        assertThat(advanced.setLevel0SlowdownWritesTrigger(8)).isSameAs(builder);
        assertThat(advanced.setLevel0StopWritesTrigger(9)).isSameAs(builder);
        assertThat(mutable.setMaxBytesForLevelBase(108L)).isSameAs(builder);
        assertThatThrownBy(builder::maxBytesForLevelMultiplier)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("has not been set");
        assertThatThrownBy(() -> advanced.setMaxBytesForLevelMultiplier(2.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept a double");
        assertThatThrownBy(() -> builder.setMaxBytesForLevelMultiplier(2.2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept a double");
        assertThat(advanced.setMaxBytesForLevelMultiplierAdditional(new int[]{10, 11}))
                .isSameAs(builder);
        assertThat(mutable.setMaxCompactionBytes(109L)).isSameAs(builder);
        assertThat(advanced.setMaxSequentialSkipInIterations(110L)).isSameAs(builder);
        assertThat(advanced.setMaxSuccessiveMerges(111L)).isSameAs(builder);
        assertThat(advanced.setMaxWriteBufferNumber(12)).isSameAs(builder);
        assertThat(advanced.setMemtableHugePageSize(112L)).isSameAs(builder);
        assertThat(advanced.setMemtablePrefixBloomSizeRatio(0.13)).isSameAs(builder);
        assertThat(advanced.setMemtableWholeKeyFiltering(true)).isSameAs(builder);
        assertThat(advanced.setMinBlobSize(113L)).isSameAs(builder);
        assertThat(advanced.setParanoidFileChecks(true)).isSameAs(builder);
        assertThat(advanced.setPeriodicCompactionSeconds(114L)).isSameAs(builder);
        assertThat(advanced.setPrepopulateBlobCache(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY))
                .isSameAs(builder);
        assertThat(advanced.setReportBgIoStats(true)).isSameAs(builder);
        assertThat(advanced.setTargetFileSizeBase(115L)).isSameAs(builder);
        assertThat(advanced.setTargetFileSizeMultiplier(14)).isSameAs(builder);
        assertThat(advanced.setTtl(116L)).isSameAs(builder);
        assertThat(mutable.setCompressionType(CompressionType.NO_COMPRESSION)).isSameAs(builder);

        assertThat(builder.writeBufferSize()).isEqualTo(101L);
        assertThat(builder.arenaBlockSize()).isEqualTo(102L);
        assertThat(builder.memtablePrefixBloomSizeRatio()).isEqualTo(0.13);
        assertThat(builder.memtableWholeKeyFiltering()).isTrue();
        assertThat(builder.memtableHugePageSize()).isEqualTo(112L);
        assertThat(builder.blobCompactionReadaheadSize()).isEqualTo(117L);
        assertThat(builder.blobCompressionType()).isEqualTo(CompressionType.NO_COMPRESSION);
        assertThat(builder.blobFileSize()).isEqualTo(118L);
        assertThat(builder.blobFileStartingLevel()).isEqualTo(19);
        assertThat(builder.blobGarbageCollectionAgeCutoff()).isEqualTo(0.20);
        assertThat(builder.blobGarbageCollectionForceThreshold()).isEqualTo(0.21);
        assertThat(builder.enableBlobFiles()).isTrue();
        assertThat(builder.enableBlobGarbageCollection()).isTrue();
        assertThat(builder.maxSuccessiveMerges()).isEqualTo(111L);
        assertThat(builder.maxWriteBufferNumber()).isEqualTo(12);
        assertThat(builder.inplaceUpdateNumLocks()).isEqualTo(105L);
        assertThat(builder.experimentalMempurgeThreshold()).isEqualTo(0.06);
        assertThat(builder.disableAutoCompactions()).isTrue();
        assertThat(builder.softPendingCompactionBytesLimit()).isEqualTo(106L);
        assertThat(builder.hardPendingCompactionBytesLimit()).isEqualTo(107L);
        assertThat(builder.level0FileNumCompactionTrigger()).isEqualTo(7);
        assertThat(builder.level0SlowdownWritesTrigger()).isEqualTo(8);
        assertThat(builder.level0StopWritesTrigger()).isEqualTo(9);
        assertThat(builder.maxBytesForLevelBase()).isEqualTo(108L);
        assertThat(builder.maxBytesForLevelMultiplierAdditional()).containsExactly(10, 11);
        assertThat(builder.maxCompactionBytes()).isEqualTo(109L);
        assertThat(builder.maxSequentialSkipInIterations()).isEqualTo(110L);
        assertThat(builder.minBlobSize()).isEqualTo(113L);
        assertThat(builder.paranoidFileChecks()).isTrue();
        assertThat(builder.periodicCompactionSeconds()).isEqualTo(114L);
        assertThat(builder.prepopulateBlobCache())
                .isEqualTo(PrepopulateBlobCache.PREPOPULATE_BLOB_FLUSH_ONLY);
        assertThat(builder.reportBgIoStats()).isTrue();
        assertThat(builder.targetFileSizeBase()).isEqualTo(115L);
        assertThat(builder.targetFileSizeMultiplier()).isEqualTo(14);
        assertThat(builder.ttl()).isEqualTo(116L);
        assertThat(builder.compressionType()).isEqualTo(CompressionType.NO_COMPRESSION);

        MutableColumnFamilyOptions built = builder.build();
        assertThat(built).isNotNull();
    }
}
