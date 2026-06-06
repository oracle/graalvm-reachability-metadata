/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;

public class JdkDynamicAopProxyTest {
    @Test
    void getProxyClassCreatesJdkProxyClassForConfiguredInterfaces() {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(AopProxyUtilsTest.VarargsService.class);

        Class<?> proxyClass = proxyFactory.getProxyClass(AopProxyUtilsTest.VarargsService.class.getClassLoader());

        assertThat(Proxy.isProxyClass(proxyClass)).isTrue();
        assertThat(AopProxyUtilsTest.VarargsService.class).isAssignableFrom(proxyClass);
        assertThat(Advised.class).isAssignableFrom(proxyClass);
    }
}
