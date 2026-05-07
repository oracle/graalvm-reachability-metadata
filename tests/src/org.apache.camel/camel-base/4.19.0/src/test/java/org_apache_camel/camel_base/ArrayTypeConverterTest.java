/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base;

import java.util.List;

import org.apache.camel.impl.converter.ArrayTypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeConverterTest {
    private final ArrayTypeConverter converter = new ArrayTypeConverter();

    @Test
    void convertsCollectionToPrimitiveArray() {
        int[] converted = converter.convertTo(int[].class, null, List.of(1, 2, 3));

        assertThat(converted).containsExactly(1, 2, 3);
    }

    @Test
    void convertsArrayToCompatibleArrayType() {
        CharSequence[] converted = converter.convertTo(
                CharSequence[].class, null, new String[] { "alpha", "beta" });

        assertThat(converted).containsExactly("alpha", "beta");
        assertThat(converted.getClass()).isEqualTo(CharSequence[].class);
    }
}
