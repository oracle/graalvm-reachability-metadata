/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {

    @SuppressWarnings("deprecation")
    @Test
    void addsElementsAtSpecificPositionsForNullAndExistingObjectArrays() {
        final String[] addedToNullArray = ArrayUtils.add((String[]) null, 0, "start");
        final String[] addedIntoExistingArray = ArrayUtils.add(new String[] {"alpha", "gamma"}, 1, "beta");

        assertThat(addedToNullArray).containsExactly("start");
        assertThat(addedToNullArray.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(addedIntoExistingArray).containsExactly("alpha", "beta", "gamma");
        assertThat(addedIntoExistingArray.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void growsObjectArraysWhenAppendingElements() {
        final String[] grownFromNullArray = ArrayUtils.add((String[]) null, "first");
        final String[] grownExistingArray = ArrayUtils.add(new String[] {"first"}, "second");

        assertThat(grownFromNullArray).containsExactly("first");
        assertThat(grownFromNullArray.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(grownExistingArray).containsExactly("first", "second");
        assertThat(grownExistingArray.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void insertsValuesAndConvertsNullArraysToTypedEmptyArrays() {
        final String[] insertedValues = ArrayUtils.insert(
                1,
                new String[] {"alpha", "delta"},
                "beta",
                "gamma"
        );
        final String[] emptyArray = ArrayUtils.nullToEmpty((String[]) null, String[].class);

        assertThat(insertedValues).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(insertedValues.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(emptyArray).isEmpty();
        assertThat(emptyArray.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void removesIndexedAndMatchingElementsFromObjectArrays() {
        final String[] removedByIndex = ArrayUtils.remove(new String[] {"alpha", "beta", "gamma"}, 1);
        final String[] removedByIndices = ArrayUtils.removeAll(
                new String[] {"zero", "one", "two", "three"},
                1,
                3
        );
        final String[] removedByValue = ArrayUtils.removeElements(
                new String[] {"keep", "drop", "keep", "drop"},
                "drop",
                "drop"
        );

        assertThat(removedByIndex).containsExactly("alpha", "gamma");
        assertThat(removedByIndices).containsExactly("zero", "two");
        assertThat(removedByValue).containsExactly("keep", "keep");
        assertThat(removedByIndex.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(removedByIndices.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(removedByValue.getClass().getComponentType()).isEqualTo(String.class);
    }

    @Test
    void createsTypedSubarraysForEmptyAndNonEmptyRanges() {
        final String[] sliced = ArrayUtils.subarray(new String[] {"zero", "one", "two", "three"}, 1, 3);
        final String[] emptySlice = ArrayUtils.subarray(new String[] {"zero", "one", "two"}, 2, 2);

        assertThat(sliced).containsExactly("one", "two");
        assertThat(sliced.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(emptySlice).isEmpty();
        assertThat(emptySlice.getClass().getComponentType()).isEqualTo(String.class);
    }
}
