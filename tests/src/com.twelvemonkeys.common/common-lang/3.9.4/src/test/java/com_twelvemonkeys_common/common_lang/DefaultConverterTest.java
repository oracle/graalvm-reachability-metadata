/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.util.convert.DefaultConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConverterTest {
    @Test
    void convertsDelimitedValuesToPrimitiveArray() {
        int[] values = (int[]) new DefaultConverter().toObject("3, 5, 8", int[].class, null);

        assertThat(values).containsExactly(3, 5, 8);
    }
}
