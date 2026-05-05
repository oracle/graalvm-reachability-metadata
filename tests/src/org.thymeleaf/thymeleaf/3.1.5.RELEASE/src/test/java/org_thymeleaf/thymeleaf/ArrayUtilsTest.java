/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.ArrayUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {

    @Test
    void toArrayCreatesTypedArrayFromIterableElements() {
        List<String> values = List.of("alpha", "beta", "gamma");

        Object[] result = ArrayUtils.toArray(values);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void copyOfPreservesComponentTypeWhenGrowingArray() {
        String[] original = {"alpha", "beta"};

        String[] result = ArrayUtils.copyOf(original, 3);

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "beta", null);
    }
}
