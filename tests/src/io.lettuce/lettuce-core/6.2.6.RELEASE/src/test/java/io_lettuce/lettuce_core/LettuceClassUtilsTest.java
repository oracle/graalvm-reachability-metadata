/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.internal.LettuceClassUtils;
import org.junit.jupiter.api.Test;

public class LettuceClassUtilsTest {

    @Test
    void forNameResolvesNestedClassesWrittenWithSourceNameSeparators() throws ClassNotFoundException {
        Class<?> resolvedClass = LettuceClassUtils.forName(
                "io_lettuce.lettuce_core.LettuceClassUtilsTest.NestedFixture");

        assertThat(resolvedClass).isEqualTo(NestedFixture.class);
    }

    public static final class NestedFixture {
    }
}
