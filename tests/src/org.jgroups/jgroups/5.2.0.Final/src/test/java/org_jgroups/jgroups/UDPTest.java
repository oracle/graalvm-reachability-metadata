/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.UDP;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.MulticastSocket;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void subclassCanUseProtectedMethodLookup() {
        Method method = ExposedUDP.lookup(MulticastSocket.class, "setTimeToLive", int.class);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("setTimeToLive");
        assertThat(method.getParameterTypes()).containsExactly(int.class);
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class ExposedUDP extends UDP {
        public static Method lookup(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
            return findMethod(targetClass, methodName, parameterTypes);
        }
    }
}
