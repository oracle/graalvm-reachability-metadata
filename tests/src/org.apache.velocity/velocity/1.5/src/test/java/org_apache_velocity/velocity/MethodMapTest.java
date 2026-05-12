/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.util.introspection.MethodMap;
import org.junit.jupiter.api.Test;

public class MethodMapTest {
    @Test
    void resolvesPrimitiveMethodForBoxedArgument() throws Exception {
        MethodMap methodMap = new MethodMap();
        methodMap.add(PrimitiveTarget.class.getMethod("describe", Integer.TYPE));

        Method method = methodMap.find("describe", new Object[] {Integer.valueOf(42)});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isSameAs(PrimitiveTarget.class);
        assertThat(method.getParameterTypes()).containsExactly(Integer.TYPE);
        assertThat(method.invoke(new PrimitiveTarget(), Integer.valueOf(42))).isEqualTo("int:42");
    }

    public static final class PrimitiveTarget {
        public String describe(final int value) {
            return "int:" + value;
        }
    }
}
