/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.collection.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueTest {

    @Test
    void toJavaArrayCreatesTypedReferenceArray() {
        final List<String> values = List.of("alpha", "beta", "gamma");

        final String[] array = values.toJavaArray(String.class);

        assertThat(array)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }
}
