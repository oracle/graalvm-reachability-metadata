/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.ReflectionHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @Test
    void packagesTrailingArgumentsIntoVarargsArray() {
        Class<?>[] requiredParameterTypes = {String.class, String[].class};

        Object[] arguments = ReflectionHelper.setupArgumentsForVarargsInvocation(
                requiredParameterTypes,
                "message",
                "hello",
                "world");

        assertThat(arguments)
                .hasSize(2);
        assertThat(arguments[0])
                .isEqualTo("message");
        assertThat(arguments[1])
                .isInstanceOf(String[].class);
        assertThat((String[]) arguments[1])
                .containsExactly("hello", "world");
    }
}
