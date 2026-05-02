/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.util.Collections;
import java.util.EnumSet;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RamUsageEstimatorTest {
    @Test
    void initializesJvmMemoryConstantsAndFeatureSets() {
        EnumSet<RamUsageEstimator.JvmFeature> supportedFeatures = RamUsageEstimator.getSupportedFeatures();
        EnumSet<RamUsageEstimator.JvmFeature> unsupportedFeatures = RamUsageEstimator.getUnsupportedFeatures();
        EnumSet<RamUsageEstimator.JvmFeature> allFeatures = EnumSet.allOf(RamUsageEstimator.JvmFeature.class);
        EnumSet<RamUsageEstimator.JvmFeature> detectedFeatures = EnumSet.copyOf(supportedFeatures);
        detectedFeatures.addAll(unsupportedFeatures);

        assertThat(detectedFeatures).containsExactlyInAnyOrderElementsOf(allFeatures);
        assertThat(Collections.disjoint(supportedFeatures, unsupportedFeatures)).isTrue();
        assertThat(RamUsageEstimator.NUM_BYTES_OBJECT_REF).isPositive();
        assertThat(RamUsageEstimator.NUM_BYTES_OBJECT_HEADER).isPositive();
        assertThat(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER).isPositive();
        assertThat(RamUsageEstimator.NUM_BYTES_OBJECT_ALIGNMENT).isPositive();
        assertThat(RamUsageEstimator.JVM_INFO_STRING).startsWith("[JVM: ").endsWith("]");
    }

    @Test
    void estimatesShallowSizeForLuceneClassWithPrimitiveAndReferenceFields() {
        BytesRef bytesRef = new BytesRef("dynamic-access");

        long sizeFromClass = RamUsageEstimator.shallowSizeOfInstance(BytesRef.class);
        long sizeFromObject = RamUsageEstimator.shallowSizeOf(bytesRef);

        assertThat(sizeFromClass).isEqualTo(sizeFromObject);
        assertThat(sizeFromClass).isGreaterThanOrEqualTo(RamUsageEstimator.NUM_BYTES_OBJECT_HEADER);
        assertThat(sizeFromClass % RamUsageEstimator.NUM_BYTES_OBJECT_ALIGNMENT).isZero();
    }

    @Test
    void estimatesArraySizesUsingPublicHelpers() {
        byte[] bytes = new byte[] {1, 2, 3, 4};
        int[] ints = new int[] {1, 2, 3};
        Object[] references = new Object[] {new BytesRef("one"), new BytesRef("two")};

        assertThat(RamUsageEstimator.sizeOf(bytes))
                .isEqualTo(RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + bytes.length));
        assertThat(RamUsageEstimator.sizeOf(ints))
                .isEqualTo(RamUsageEstimator.alignObjectSize(
                        RamUsageEstimator.NUM_BYTES_ARRAY_HEADER
                                + (long) RamUsageEstimator.NUM_BYTES_INT * ints.length));
        assertThat(RamUsageEstimator.shallowSizeOf(references))
                .isEqualTo(RamUsageEstimator.shallowSizeOf(new Object[references.length]));
    }

    @Test
    void reportsLongAndHumanReadableSizes() {
        long uncachedLong = Long.MAX_VALUE;

        assertThat(RamUsageEstimator.sizeOf(Long.valueOf(uncachedLong)))
                .isEqualTo(RamUsageEstimator.shallowSizeOfInstance(Long.class));
        assertThat(RamUsageEstimator.humanReadableUnits(RamUsageEstimator.ONE_KB)).isEqualTo("1 KB");
        assertThat(RamUsageEstimator.humanReadableUnits(RamUsageEstimator.ONE_MB)).isEqualTo("1 MB");
        assertThat(RamUsageEstimator.humanReadableUnits(512)).isEqualTo("512 bytes");
    }

    @Test
    void rejectsArrayClassesForInstanceSizing() {
        assertThatThrownBy(() -> RamUsageEstimator.shallowSizeOfInstance(byte[].class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("array classes");
    }
}
