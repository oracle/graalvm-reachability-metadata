/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.ArrayUtils;
import org.junit.jupiter.api.Test;

public class CommonsLangArrayUtilsTest {

    @Test
    public void addAppendsObjectElementsAndCreatesArraysWhenInputIsNull() {
        Object[] objectArray = ArrayUtils.add((Object[]) null, null);
        int[] primitiveArray = ArrayUtils.add((int[]) null, 7);
        String[] grownArray = (String[]) ArrayUtils.add(new String[] {"alpha"}, "beta");

        assertThat(objectArray).containsExactly((Object) null);
        assertThat(objectArray).isExactlyInstanceOf(Object[].class);
        assertThat(primitiveArray).containsExactly(7);
        assertThat(grownArray).containsExactly("alpha", "beta");
    }

    @Test
    public void addInsertsObjectElementsIntoNullAndExistingArrays() {
        String[] insertedIntoNullArray = (String[]) ArrayUtils.add((Object[]) null, 0, "alpha");
        String[] insertedIntoExistingArray =
                (String[]) ArrayUtils.add(new String[] {"alpha", "gamma"}, 1, "beta");

        assertThat(insertedIntoNullArray).containsExactly("alpha");
        assertThat(insertedIntoExistingArray).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    public void addAllConcatenatesObjectArraysUsingTheFirstArrayComponentType() {
        Number[] joinedArray = (Number[]) ArrayUtils.addAll(new Integer[] {1, 2}, new Integer[] {3, 4});

        assertThat(joinedArray).containsExactly(1, 2, 3, 4);
        assertThat(joinedArray).isExactlyInstanceOf(Integer[].class);
    }

    @Test
    public void removeCreatesArrayWithTheOriginalComponentType() {
        String[] result = (String[]) ArrayUtils.remove(new String[] {"alpha", "beta", "gamma"}, 1);

        assertThat(result).containsExactly("alpha", "gamma");
        assertThat(result).isExactlyInstanceOf(String[].class);
    }

    @Test
    public void subarrayReturnsTypedEmptyAndPopulatedObjectArrays() {
        String[] emptySlice = (String[]) ArrayUtils.subarray(new String[] {"alpha"}, 1, 1);
        String[] populatedSlice = (String[]) ArrayUtils.subarray(new String[] {"alpha", "beta", "gamma"}, 1, 3);

        assertThat(emptySlice).isEmpty();
        assertThat(emptySlice).isExactlyInstanceOf(String[].class);
        assertThat(populatedSlice).containsExactly("beta", "gamma");
        assertThat(populatedSlice).isExactlyInstanceOf(String[].class);
    }
}
