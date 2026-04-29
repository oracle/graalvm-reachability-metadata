/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.UDP;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.MulticastSocket;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPTest {
    @Test
    void findMethodLocatesDeclaredUdpMethod() {
        Method method = UdpHarness.findUdpMethod("setTimeToLive", int.class, MulticastSocket.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(UDP.class);
        assertThat(method.getName()).isEqualTo("setTimeToLive");
    }

    private static final class UdpHarness extends UDP {
        private static Method findUdpMethod(String methodName, Class<?>... parameters) {
            return findMethod(UDP.class, methodName, parameters);
        }
    }
}
