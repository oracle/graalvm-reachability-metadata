/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.BytecodeReadingParanamer;
import java.io.InputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class BytecodeReadingParanamerTest {
    @Test
    void readsParameterNamesFromAvailableClassResource() throws Exception {
        BytecodeReadingParanamer paranamer = new BytecodeReadingParanamer();
        Method method = ParameterNameFixture.class.getDeclaredMethod("format", String.class, int.class);
        assertThatClassResourceIsAvailable(method.getDeclaringClass());

        String[] parameterNames = paranamer.lookupParameterNames(method, false);

        assertThat(parameterNames).containsExactly("message", "repeatCount");
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

    private static void assertThatClassResourceIsAvailable(Class<?> type) throws Exception {
        try (InputStream resource = type.getClassLoader().getResourceAsStream(classResourceName(type))) {
            assertThat(resource).isNotNull();
        }
    }

    private static String classResourceName(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
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
