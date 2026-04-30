/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.util.reflect.Classes;
import org.codehaus.commons.compiler.util.reflect.Methods;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodsTest {
    @Test
    void invokesReflectedMethodAndReturnsTypedValue() {
        InvocationTarget target = new InvocationTarget();
        Method method = Classes.getDeclaredMethod(InvocationTarget.class, "formatMessage", String.class, int.class);

        String result = Methods.invoke(method, target, "value-", 42);

        assertThat(result).isEqualTo("value-42");
        assertThat(target.invocationCount()).isEqualTo(1);
    }

    public static final class InvocationTarget {
        private int invocationCount;

        public String formatMessage(String prefix, int value) {
            this.invocationCount++;
            return prefix + value;
        }

        int invocationCount() {
            return this.invocationCount;
        }
    }
}
