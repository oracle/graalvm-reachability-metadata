/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_paranamer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.module.paranamer.shaded.BytecodeReadingParanamer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class BytecodeReadingParanamerTest {
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
}
