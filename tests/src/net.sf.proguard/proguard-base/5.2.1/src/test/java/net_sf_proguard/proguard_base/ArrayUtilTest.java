/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_proguard.proguard_base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import proguard.util.ArrayUtil;

public class ArrayUtilTest {
    @Test
    void extendArrayCreatesLargerArrayWithSameComponentTypeAndContents() {
        String[] original = new String[] {"alpha", "beta"};

        Object[] extended = ArrayUtil.extendArray(original, 5);

        assertThat(extended).isInstanceOf(String[].class);
        assertThat(extended).isNotSameAs(original);
        assertThat(extended).hasSize(5);
        assertThat(extended).containsExactly("alpha", "beta", null, null, null);
    }

    @Test
    void ensureArraySizeCreatesAndInitializesLargerArrayWithSameComponentType() {
        StringBuilder initialValue = new StringBuilder("initial");
        StringBuilder[] original = new StringBuilder[] {new StringBuilder("existing")};

        Object[] ensured = ArrayUtil.ensureArraySize(original, 4, initialValue);

        assertThat(ensured).isInstanceOf(StringBuilder[].class);
        assertThat(ensured).isNotSameAs(original);
        assertThat(ensured).hasSize(4);
        assertThat(ensured).containsExactly(initialValue, initialValue, initialValue, initialValue);
    }

    @Test
    void ensureArraySizeReinitializesExistingArrayWhenAlreadyLargeEnough() {
        String[] original = new String[] {"first", "second", "third"};

        Object[] ensured = ArrayUtil.ensureArraySize(original, 2, "reset");

        assertThat(ensured).isSameAs(original);
        assertThat(original).containsExactly("reset", "reset", "third");
    }
}
