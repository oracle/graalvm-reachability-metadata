/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_cpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import io.smallrye.common.cpu.CacheInfo;
import io.smallrye.common.cpu.CacheLevelInfo;
import io.smallrye.common.cpu.CacheType;
import io.smallrye.common.cpu.ProcessorInfo;
import org.junit.jupiter.api.Test;

public class Smallrye_common_cpuTest {
    @Test
    void cacheTypesExposeExpectedCapabilities() {
        assertThat(CacheType.values()).containsExactly(
                CacheType.UNKNOWN,
                CacheType.DATA,
                CacheType.INSTRUCTION,
                CacheType.UNIFIED);

        assertThat(CacheType.valueOf("UNKNOWN")).isSameAs(CacheType.UNKNOWN);
        assertThat(CacheType.valueOf("DATA")).isSameAs(CacheType.DATA);
        assertThat(CacheType.valueOf("INSTRUCTION")).isSameAs(CacheType.INSTRUCTION);
        assertThat(CacheType.valueOf("UNIFIED")).isSameAs(CacheType.UNIFIED);

        assertThat(CacheType.UNKNOWN.isData()).isFalse();
        assertThat(CacheType.UNKNOWN.isInstruction()).isFalse();
        assertThat(CacheType.DATA.isData()).isTrue();
        assertThat(CacheType.DATA.isInstruction()).isFalse();
        assertThat(CacheType.INSTRUCTION.isData()).isFalse();
        assertThat(CacheType.INSTRUCTION.isInstruction()).isTrue();
        assertThat(CacheType.UNIFIED.isData()).isTrue();
        assertThat(CacheType.UNIFIED.isInstruction()).isTrue();
    }

    @Test
    void cacheTypeValuesReturnsIndependentSnapshots() {
        CacheType[] cacheTypes = CacheType.values();
        CacheType firstCacheType = cacheTypes[0];

        cacheTypes[0] = CacheType.UNIFIED;

        assertThat(CacheType.values()[0]).isSameAs(firstCacheType);
        assertThat(CacheType.values()).isNotSameAs(cacheTypes);
    }

    @Test
    void cacheTypeMembershipHelpersHandleEachOverload() {
        assertThat(CacheType.DATA.in(CacheType.DATA)).isTrue();
        assertThat(CacheType.DATA.in(CacheType.INSTRUCTION)).isFalse();
        assertThat(CacheType.DATA.in((CacheType) null)).isFalse();

        assertThat(CacheType.INSTRUCTION.in(CacheType.DATA, CacheType.INSTRUCTION)).isTrue();
        assertThat(CacheType.INSTRUCTION.in(CacheType.UNKNOWN, CacheType.UNIFIED)).isFalse();
        assertThat(CacheType.INSTRUCTION.in(CacheType.UNKNOWN, null)).isFalse();

        assertThat(CacheType.UNIFIED.in(CacheType.DATA, CacheType.INSTRUCTION, CacheType.UNIFIED))
                .isTrue();
        assertThat(CacheType.UNIFIED.in(CacheType.UNKNOWN, CacheType.DATA, CacheType.INSTRUCTION))
                .isFalse();
        assertThat(CacheType.UNIFIED.in(CacheType.UNKNOWN, null, CacheType.DATA)).isFalse();

        assertThat(CacheType.UNKNOWN.in(CacheType.DATA, null, CacheType.UNKNOWN, CacheType.UNIFIED)).isTrue();
        assertThat(CacheType.UNKNOWN.in(CacheType.DATA, CacheType.INSTRUCTION, CacheType.UNIFIED)).isFalse();
        assertThat(CacheType.UNKNOWN.in(new CacheType[0])).isFalse();
        assertThat(CacheType.UNKNOWN.in((CacheType[]) null)).isFalse();
    }

    @Test
    void cacheTypeFullSetDetectionRequiresEveryCacheType() {
        assertThat(CacheType.isFull(null)).isFalse();
        assertThat(CacheType.isFull(EnumSet.noneOf(CacheType.class))).isFalse();
        assertThat(CacheType.isFull(EnumSet.of(CacheType.DATA, CacheType.INSTRUCTION, CacheType.UNIFIED)))
                .isFalse();
        assertThat(CacheType.isFull(EnumSet.allOf(CacheType.class))).isTrue();
    }

    @Test
    void availableProcessorsMatchesRuntimeProcessorCount() {
        int availableProcessors = ProcessorInfo.availableProcessors();

        assertThat(availableProcessors).isPositive();
        assertThat(availableProcessors).isEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    void cacheInfoReportsInternallyConsistentCacheLevels() {
        assertThat(new CacheInfo()).isNotNull();

        int levelEntryCount = CacheInfo.getLevelEntryCount();
        assertThat(levelEntryCount).isNotNegative();

        int smallestDataLineSize = Integer.MAX_VALUE;
        int smallestInstructionLineSize = Integer.MAX_VALUE;

        for (int index = 0; index < levelEntryCount; index++) {
            CacheLevelInfo cacheLevelInfo = CacheInfo.getCacheLevelInfo(index);

            assertThat(cacheLevelInfo).isNotNull();
            assertThat(cacheLevelInfo.getCacheLevel()).isNotNegative();
            assertThat(cacheLevelInfo.getCacheType()).isNotNull();
            assertThat(cacheLevelInfo.getCacheLevelSizeKB()).isNotNegative();
            assertThat(cacheLevelInfo.getCacheLineSize()).isNotNegative();

            if (cacheLevelInfo.getCacheType().isData() && cacheLevelInfo.getCacheLineSize() > 0) {
                smallestDataLineSize = Math.min(smallestDataLineSize, cacheLevelInfo.getCacheLineSize());
            }
            if (cacheLevelInfo.getCacheType().isInstruction() && cacheLevelInfo.getCacheLineSize() > 0) {
                smallestInstructionLineSize = Math.min(smallestInstructionLineSize, cacheLevelInfo.getCacheLineSize());
            }
        }

        assertThat(CacheInfo.getSmallestDataCacheLineSize())
                .isEqualTo(zeroWhenNoCacheLineWasFound(smallestDataLineSize));
        assertThat(CacheInfo.getSmallestInstructionCacheLineSize())
                .isEqualTo(zeroWhenNoCacheLineWasFound(smallestInstructionLineSize));
    }

    @Test
    void cacheLevelLookupRejectsOutOfRangeIndexes() {
        int levelEntryCount = CacheInfo.getLevelEntryCount();

        assertThatThrownBy(() -> CacheInfo.getCacheLevelInfo(-1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> CacheInfo.getCacheLevelInfo(levelEntryCount))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void cacheLevelLookupReturnsStableSnapshotEntries() {
        int levelEntryCount = CacheInfo.getLevelEntryCount();
        CacheLevelInfo[] firstSnapshot = new CacheLevelInfo[levelEntryCount];

        for (int index = 0; index < levelEntryCount; index++) {
            firstSnapshot[index] = CacheInfo.getCacheLevelInfo(index);
        }

        assertThat(CacheInfo.getLevelEntryCount()).isEqualTo(levelEntryCount);
        for (int index = 0; index < levelEntryCount; index++) {
            CacheLevelInfo cacheLevelInfo = CacheInfo.getCacheLevelInfo(index);

            assertThat(cacheLevelInfo).isSameAs(firstSnapshot[index]);
            assertThat(cacheLevelInfo.getCacheLevel()).isEqualTo(firstSnapshot[index].getCacheLevel());
            assertThat(cacheLevelInfo.getCacheType()).isSameAs(firstSnapshot[index].getCacheType());
            assertThat(cacheLevelInfo.getCacheLevelSizeKB()).isEqualTo(firstSnapshot[index].getCacheLevelSizeKB());
            assertThat(cacheLevelInfo.getCacheLineSize()).isEqualTo(firstSnapshot[index].getCacheLineSize());
        }
    }

    @Test
    void mainPrintsDetectedCacheInformationWithoutChangingCacheState() {
        int levelEntryCountBeforeMain = CacheInfo.getLevelEntryCount();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            CacheInfo.main(new String[] {"ignored"});
        } finally {
            System.setOut(originalOut);
        }

        String printedOutput = output.toString(StandardCharsets.UTF_8);
        assertThat(printedOutput).startsWith("Detected cache info:" + System.lineSeparator());
        for (String cacheLevelDescription : cacheLevelDescriptions()) {
            assertThat(printedOutput).contains(cacheLevelDescription);
        }
        assertThat(CacheInfo.getLevelEntryCount()).isEqualTo(levelEntryCountBeforeMain);
    }

    private static int zeroWhenNoCacheLineWasFound(int cacheLineSize) {
        return cacheLineSize == Integer.MAX_VALUE ? 0 : cacheLineSize;
    }

    private static String[] cacheLevelDescriptions() {
        String[] descriptions = new String[CacheInfo.getLevelEntryCount()];
        for (int index = 0; index < descriptions.length; index++) {
            CacheLevelInfo cacheLevelInfo = CacheInfo.getCacheLevelInfo(index);
            descriptions[index] = String.format(
                    "Level %d cache: type %s, size %d KiB, cache line is %d bytes",
                    cacheLevelInfo.getCacheLevel(),
                    cacheLevelInfo.getCacheType(),
                    cacheLevelInfo.getCacheLevelSizeKB(),
                    cacheLevelInfo.getCacheLineSize());
        }
        return descriptions;
    }
}
