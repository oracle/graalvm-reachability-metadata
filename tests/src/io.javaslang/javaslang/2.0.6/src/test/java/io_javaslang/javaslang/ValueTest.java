/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import static org.assertj.core.api.Assertions.assertThat;

import javaslang.collection.List;
import org.junit.jupiter.api.Test;

public class ValueTest {

    @Test
    void toJavaArrayCreatesTypedArrayUsingComponentType() {
        final List<String> value = List.of("alpha", "beta", "gamma");

        final String[] array = value.toJavaArray(String.class);

        assertThat(array).isExactlyInstanceOf(String[].class);
        assertThat(array).containsExactly("alpha", "beta", "gamma");
    }
}
