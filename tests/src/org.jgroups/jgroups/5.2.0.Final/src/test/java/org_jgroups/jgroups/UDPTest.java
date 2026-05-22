/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.lang.reflect.Method;

import org.jgroups.protocols.UDP;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPTest {
    @Test
    void exposesDeclaredMethodsToTransportSubclasses() {
        Method method = ExposedUDP.findDeclaredMethod(UDP.class, "setTos", int.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(UDP.class);
        assertThat(method.getName()).isEqualTo("setTos");
        assertThat(method.getParameterTypes()).containsExactly(int.class);
    }

    private static final class ExposedUDP extends UDP {
        private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... parameters) {
            return findMethod(type, name, parameters);
        }
    }
}
