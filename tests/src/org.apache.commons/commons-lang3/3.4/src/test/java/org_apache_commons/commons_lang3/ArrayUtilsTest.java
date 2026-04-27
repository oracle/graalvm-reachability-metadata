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

    @Test
    public void addAtIndexCreatesTypedArrayWhenSourceArrayIsNull() {
        final String[] result = ArrayUtils.add((String[]) null, 0, "alpha");

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha");
    }

    @Test
    public void addAtIndexInsertsIntoExistingObjectArray() {
        final String[] result = ArrayUtils.add(new String[]{"alpha", "gamma"}, 1, "beta");

        assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    public void addAllJoinsObjectArraysIntoTypedResult() {
        final String[] result = ArrayUtils.addAll(new String[]{"alpha", "beta"}, new String[]{"gamma", "delta"});

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "beta", "gamma", "delta");
    }

    @Test
    public void addAppendsToExistingObjectArray() {
        final String[] result = ArrayUtils.add(new String[]{"alpha"}, "beta");

        assertThat(result).containsExactly("alpha", "beta");
    }

    @Test
    public void addCreatesTypedArrayWhenAppendingToNullArray() {
        final String[] result = ArrayUtils.add((String[]) null, "alpha");

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha");
    }

    @Test
    public void removeDeletesElementFromObjectArray() {
        final String[] result = ArrayUtils.remove(new String[]{"alpha", "beta", "gamma"}, 1);

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "gamma");
    }

    @Test
    public void removeAllByIndexCreatesCompactedObjectArray() {
        final String[] result = ArrayUtils.removeAll(new String[]{"alpha", "beta", "gamma", "delta"}, 1, 3, 1);

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "gamma");
    }

    @Test
    public void removeElementsUsesOccurrenceBasedRemovalForObjectArrays() {
        final String[] result = ArrayUtils.removeElements(new String[]{"alpha", "beta", "gamma", "beta", "delta"},
                "beta", "beta", "missing");

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "gamma", "delta");
    }

    @Test
    public void subarrayReturnsTypedEmptyArrayWhenRequestedRangeIsEmpty() {
        final String[] result = ArrayUtils.subarray(new String[]{"alpha", "beta"}, 2, 1);

        assertThat(result).isInstanceOf(String[].class).isEmpty();
    }

    @Test
    public void subarrayCopiesRequestedObjectRange() {
        final String[] result = ArrayUtils.subarray(new String[]{"alpha", "beta", "gamma"}, 1, 3);

        assertThat(result).isInstanceOf(String[].class).containsExactly("beta", "gamma");
    }
}
