/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.thymeleaf.util.ArrayUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {

    @Test
    void toArrayCreatesAnArrayForIterableInput() {
        Object[] result = ArrayUtils.toArray(Arrays.asList("alpha", "beta"));

        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).containsExactly("alpha", "beta");
    }

    @Test
    void copyOfCreatesTheRequestedArrayType() {
        String[] original = {"alpha", "beta"};

        CharSequence[] result = ArrayUtils.copyOf(original, 3, CharSequence[].class);

        assertThat(result).isInstanceOf(CharSequence[].class);
        assertThat(result).containsExactly("alpha", "beta", null);
    }
}
