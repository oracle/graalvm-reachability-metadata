/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.EnumSet;

import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.RamUsageEstimator.JvmFeature;
import org.junit.jupiter.api.Test;

public class RamUsageEstimatorTest {
    @Test
    public void estimatesShallowObjectSizesFromDeclaredFields() {
        long mixedFieldSize = RamUsageEstimator.shallowSizeOfInstance(MixedFieldObject.class);
        long mixedObjectSize = RamUsageEstimator.shallowSizeOf(new MixedFieldObject());
        long inheritedFieldSize = RamUsageEstimator.shallowSizeOfInstance(ChildFieldObject.class);

        assertThat(mixedObjectSize).isEqualTo(mixedFieldSize);
        assertThat(mixedFieldSize).isGreaterThan(RamUsageEstimator.NUM_BYTES_OBJECT_HEADER);
        assertThat(inheritedFieldSize).isGreaterThan(mixedFieldSize);
        assertThat(mixedFieldSize % RamUsageEstimator.NUM_BYTES_OBJECT_ALIGNMENT).isZero();
        assertThat(inheritedFieldSize % RamUsageEstimator.NUM_BYTES_OBJECT_ALIGNMENT).isZero();
    }

    @Test
    public void exposesDetectedJvmSizingFeatures() {
        EnumSet<JvmFeature> detectedFeatures = RamUsageEstimator.getSupportedFeatures();
        EnumSet<JvmFeature> missingFeatures = RamUsageEstimator.getUnsupportedFeatures();
        EnumSet<JvmFeature> allFeatures = EnumSet.copyOf(detectedFeatures);
        allFeatures.addAll(missingFeatures);

        assertThat(allFeatures).containsExactlyInAnyOrder(JvmFeature.values());
        assertThat(Collections.disjoint(detectedFeatures, missingFeatures)).isTrue();
        assertThat(RamUsageEstimator.JVM_INFO_STRING).startsWith("[JVM: ").endsWith("]");
    }

    @Test
    public void estimatesPrimitiveArrayAndBoxedLongSizes() {
        int byteCount = 3;
        int intCount = 4;
        int referenceCount = 5;

        assertThat(RamUsageEstimator.sizeOf(new byte[byteCount]))
                .isEqualTo(RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + byteCount));
        assertThat(RamUsageEstimator.sizeOf(new int[intCount]))
                .isEqualTo(RamUsageEstimator.alignObjectSize(
                        RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + (long) RamUsageEstimator.NUM_BYTES_INT * intCount));
        assertThat(RamUsageEstimator.shallowSizeOf(new Object[referenceCount]))
                .isEqualTo(RamUsageEstimator.alignObjectSize(
                        RamUsageEstimator.NUM_BYTES_ARRAY_HEADER
                                + (long) RamUsageEstimator.NUM_BYTES_OBJECT_REF * referenceCount));
        assertThat(RamUsageEstimator.sizeOf(Long.valueOf(Long.MAX_VALUE))).isGreaterThan(0L);
    }

    @Test
    public void rejectsArrayClassesForInstanceSizing() {
        assertThat(RamUsageEstimator.shallowSizeOfInstance(int.class)).isEqualTo(RamUsageEstimator.NUM_BYTES_INT);
        assertThatThrownBy(() -> RamUsageEstimator.shallowSizeOfInstance(String[].class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("array classes");
    }

    private static class ParentFieldObject {
        private long parentTimestamp;
        private Object parentReference = new Object();
    }

    private static final class ChildFieldObject extends ParentFieldObject {
        private long childOne;
        private long childTwo;
        private long childThree;
        private Object childReference = new Object();
    }

    private static final class MixedFieldObject {
        private int id;
        private long timestamp;
        private byte flags;
        private Object reference = new Object();
    }
}
