/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common;

import java.util.List;

import io.helidon.common.GenericType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericTypeUtilTest {
    @Test
    void resolvesRawClassForGenericArrayType() {
        GenericType<List<String>[]> genericArrayType = new GenericType<List<String>[]>() { };

        assertThat(genericArrayType.rawType()).isEqualTo(List[].class);
        assertThat(genericArrayType.isClass()).isFalse();
    }
}
