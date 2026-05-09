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
    void convertsNullArrayToTypedEmptyArray() {
        String[] empty = ArrayUtils.nullToEmpty(null, String[].class);

        assertThat(empty).isEmpty();
        assertThat(empty).isInstanceOf(String[].class);
    }

    @Test
    void createsTypedSubarraysForEmptyAndNonEmptyRanges() {
        String[] source = {"alpha", "beta", "gamma"};

        String[] empty = ArrayUtils.subarray(source, 2, 1);
        String[] middle = ArrayUtils.subarray(source, 1, 3);

        assertThat(empty).isEmpty();
        assertThat(empty).isInstanceOf(String[].class);
        assertThat(middle).containsExactly("beta", "gamma");
        assertThat(middle).isInstanceOf(String[].class);
    }

    @Test
    void combinesObjectArraysIntoTypedArray() {
        String[] combined = ArrayUtils.addAll(new String[] {"alpha"}, "beta", "gamma");

        assertThat(combined).containsExactly("alpha", "beta", "gamma");
        assertThat(combined).isInstanceOf(String[].class);
    }

    @Test
    void appendsElementsToNullAndExistingArrays() {
        String[] appendedToNullArray = ArrayUtils.add((String[]) null, "alpha");
        String[] appendedToExistingArray = ArrayUtils.add(new String[] {"beta"}, "gamma");

        assertThat(appendedToNullArray).containsExactly("alpha");
        assertThat(appendedToNullArray).isInstanceOf(String[].class);
        assertThat(appendedToExistingArray).containsExactly("beta", "gamma");
        assertThat(appendedToExistingArray).isInstanceOf(String[].class);
    }

    @Test
    void insertsElementsIntoNullAndExistingArrays() {
        String[] insertedIntoNullArray = ArrayUtils.add((String[]) null, 0, "alpha");
        String[] insertedIntoExistingArray = ArrayUtils.add(new String[] {"beta", "delta"}, 1, "gamma");

        assertThat(insertedIntoNullArray).containsExactly("alpha");
        assertThat(insertedIntoNullArray).isInstanceOf(String[].class);
        assertThat(insertedIntoExistingArray).containsExactly("beta", "gamma", "delta");
        assertThat(insertedIntoExistingArray).isInstanceOf(String[].class);
    }

    @Test
    void removesByIndexAndByElementsUsingTypedArrays() {
        String[] removedSingleIndex = ArrayUtils.remove(new String[] {"alpha", "beta", "gamma"}, 1);
        String[] removedMultipleIndices = ArrayUtils.removeAll(
                new String[] {"alpha", "beta", "gamma", "delta"}, 1, 3, 1);
        String[] removedMatchingValues = ArrayUtils.removeElements(
                new String[] {"alpha", "beta", "gamma", "beta", "delta"}, "beta", "delta");

        assertThat(removedSingleIndex).containsExactly("alpha", "gamma");
        assertThat(removedSingleIndex).isInstanceOf(String[].class);
        assertThat(removedMultipleIndices).containsExactly("alpha", "gamma");
        assertThat(removedMultipleIndices).isInstanceOf(String[].class);
        assertThat(removedMatchingValues).containsExactly("alpha", "gamma", "beta");
        assertThat(removedMatchingValues).isInstanceOf(String[].class);
    }
}
