/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.internal.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {
    @Test
    public void addCreatesArraysForNullAndExistingInputs() {
        String[] insertedIntoNullArray = ArrayUtils.add((String[]) null, 0, "alpha");
        String[] insertedIntoExistingArray = ArrayUtils.add(new String[] {"alpha", "gamma"}, 1, "beta");
        String[] appendedToNullArray = ArrayUtils.add((String[]) null, "delta");
        String[] appendedToExistingArray = ArrayUtils.add(new String[] {"epsilon"}, "zeta");

        assertThat(insertedIntoNullArray).containsExactly("alpha");
        assertThat(insertedIntoExistingArray).containsExactly("alpha", "beta", "gamma");
        assertThat(appendedToNullArray).containsExactly("delta");
        assertThat(appendedToExistingArray).containsExactly("epsilon", "zeta");
    }

    @Test
    public void addAllAndInsertCreateArraysWithSourceComponentType() {
        String[] joined = ArrayUtils.addAll(new String[] {"alpha"}, "beta", "gamma");
        String[] inserted = ArrayUtils.insert(1, new String[] {"alpha", "delta"}, "beta", "gamma");

        assertThat(joined).containsExactly("alpha", "beta", "gamma");
        assertThat(inserted).containsExactly("alpha", "beta", "gamma", "delta");
    }

    @Test
    public void nullToEmptyCreatesTypedEmptyArray() {
        String[] empty = ArrayUtils.nullToEmpty(null, String[].class);
        String[] original = new String[] {"alpha"};

        assertThat(empty).isEmpty();
        assertThat(empty).isInstanceOf(String[].class);
        assertThat(ArrayUtils.nullToEmpty(original, String[].class)).isSameAs(original);
    }

    @Test
    public void removeAndRemoveAllCreateArraysWithRemainingElements() {
        String[] removedSingleIndex = ArrayUtils.remove(new String[] {"alpha", "beta", "gamma"}, 1);
        String[] removedMultipleIndices = ArrayUtils.removeAll(
                new String[] {"alpha", "beta", "gamma", "delta"}, 1, 3);
        String[] removedMatchingValues = ArrayUtils.removeElements(
                new String[] {"alpha", "beta", "alpha", "gamma"}, "alpha", "gamma");

        assertThat(removedSingleIndex).containsExactly("alpha", "gamma");
        assertThat(removedMultipleIndices).containsExactly("alpha", "gamma");
        assertThat(removedMatchingValues).containsExactly("beta", "alpha");
    }

    @Test
    public void subarrayCreatesEmptyAndNonEmptyTypedArrays() {
        String[] source = new String[] {"alpha", "beta", "gamma"};
        String[] empty = ArrayUtils.subarray(source, 2, 1);
        String[] middle = ArrayUtils.subarray(source, 1, 3);

        assertThat(empty).isEmpty();
        assertThat(empty).isInstanceOf(String[].class);
        assertThat(middle).containsExactly("beta", "gamma");
    }
}
