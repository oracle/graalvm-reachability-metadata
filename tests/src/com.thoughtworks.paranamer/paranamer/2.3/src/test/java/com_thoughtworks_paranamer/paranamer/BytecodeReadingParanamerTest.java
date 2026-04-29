/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_paranamer.paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class BytecodeReadingParanamerTest {
    @Test
    void readsParameterNamesFromAvailableClassResource() throws Exception {
        BytecodeReadingParanamer paranamer = new BytecodeReadingParanamer();
        Method method = ParameterNameFixture.class.getDeclaredMethod("format", String.class, int.class);

        String[] parameterNames = paranamer.lookupParameterNames(method, false);

        assertThat(parameterNames).satisfiesAnyOf(
                names -> assertThat(names).containsExactly("message", "repeatCount"),
                names -> assertThat(names).isEmpty());
    }

    @Test
    void returnsEmptyNamesWhenGeneratedClassHasNoBytecodeResource() throws Exception {
        BytecodeReadingParanamer paranamer = new BytecodeReadingParanamer();
        ParameterizedOperation operation = value -> value.length();
        Method method = operation.getClass().getDeclaredMethod("apply", String.class);
        assertThat(method.getDeclaringClass()).isSameAs(operation.getClass());

        String[] parameterNames = paranamer.lookupParameterNames(method, false);

        assertThat(parameterNames).isEmpty();
    }

    private interface ParameterizedOperation {
        int apply(String value);
    }

    private static final class ParameterNameFixture {
        String format(String message, int repeatCount) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < repeatCount; index++) {
                result.append(message);
            }
            return result.toString();
        }
    }
}
