/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.GenericArrayType;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeUtilsTest {
    @Test
    void resolvesTheRawClassOfAGenericArray() {
        GenericArrayType genericArray = TypeUtils.genericArrayType(String.class);

        assertThat(TypeUtils.getRawType(genericArray, null)).isEqualTo(String[].class);
    }
}
