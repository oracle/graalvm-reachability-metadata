/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.dubbo.common.bytecode.Proxy;
import com_alibaba.dubbo.visibility.HiddenInterfaceProvider;
import org.junit.jupiter.api.Test;

public class ProxyTest {
    @Test
    void rejectsNonPublicInterfacesFromDifferentPackages() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                Proxy.getProxy(
                                        ProxyTest.class.getClassLoader(),
                                        Service.class,
                                        HiddenInterfaceProvider.type()));

        assertThat(failure).hasMessage("non-public interfaces from different packages");
    }

    private interface Service {
        String call(String value);
    }
}
