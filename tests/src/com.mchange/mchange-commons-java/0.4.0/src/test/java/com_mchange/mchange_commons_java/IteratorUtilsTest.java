/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.util.IteratorUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IteratorUtilsTest {
    @Test
    void toArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        Object[] result = IteratorUtils.toArray(List.of("alpha", "beta").iterator(), 2, new String[0]);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).containsExactly("alpha", "beta");
    }
}
