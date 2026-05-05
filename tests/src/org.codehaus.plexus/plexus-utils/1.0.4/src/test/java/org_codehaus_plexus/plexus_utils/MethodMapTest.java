/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.MethodMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodMapTest {
    @Test
    void findsPrimitiveFormalMethodForBoxedBooleanArgument() throws Exception {
        MethodMap methodMap = new MethodMap();
        methodMap.add(PrimitiveTarget.class.getMethod("accept", Boolean.TYPE));

        Method method = methodMap.find("accept", new Object[] {Boolean.TRUE});

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("accept");
        assertThat(method.getDeclaringClass()).isEqualTo(PrimitiveTarget.class);
        assertThat(method.getParameterTypes()).containsExactly(Boolean.TYPE);
    }

    public static class PrimitiveTarget {
        public void accept(boolean value) {
            // MethodMap only needs the signature for overload resolution.
        }
    }
}
