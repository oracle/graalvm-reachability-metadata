/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {
    @Test
    void createsTypedArraysForGenericNullAndSubarrayOperations() {
        String[] emptyStrings = ArrayUtils.nullToEmpty((String[]) null, String[].class);
        String[] emptySubarray = ArrayUtils.subarray(new String[] { "alpha", "beta" }, 2, 1);
        String[] populatedSubarray = ArrayUtils.subarray(new String[] { "alpha", "beta", "gamma" }, 1, 3);

        assertThat(emptyStrings).isEmpty();
        assertThat(emptySubarray).isEmpty();
        assertThat(populatedSubarray).containsExactly("beta", "gamma");
    }

    @Test
    void createsTypedArraysWhenAddingAndCombiningGenericArrays() {
        String[] combined = ArrayUtils.addAll(new String[] { "left" }, new String[] { "right", "tail" });
        String[] appendedToExisting = ArrayUtils.add(new String[] { "left" }, "right");
        String[] appendedToNull = ArrayUtils.add((String[]) null, "first");
        String[] insertedIntoNull = ArrayUtils.add((String[]) null, 0, "first");
        String[] insertedIntoExisting = ArrayUtils.add(new String[] { "left", "tail" }, 1, "middle");

        assertThat(combined).containsExactly("left", "right", "tail");
        assertThat(appendedToExisting).containsExactly("left", "right");
        assertThat(appendedToNull).containsExactly("first");
        assertThat(insertedIntoNull).containsExactly("first");
        assertThat(insertedIntoExisting).containsExactly("left", "middle", "tail");
    }

    @Test
    void createsTypedArraysWhenRemovingGenericArrayElements() {
        String[] removedSingle = ArrayUtils.remove(new String[] { "zero", "one", "two" }, 1);
        String[] removedByIndices = ArrayUtils.removeAll(new String[] { "zero", "one", "two", "three" }, 1, 3);
        String[] removedByValues = ArrayUtils.removeElements(
                new String[] { "keep", "drop", "drop", "stay" }, "drop", "drop");

        assertThat(removedSingle).containsExactly("zero", "two");
        assertThat(removedByIndices).containsExactly("zero", "two");
        assertThat(removedByValues).containsExactly("keep", "stay");
    }
}
