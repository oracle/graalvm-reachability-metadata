/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

public class SurefireReflectorInnerClassLoaderProxyTest {

    @Test
    public void invocationHandlerDispatchesCallsToDelegateByPublicMethodSignature() throws Exception {
        final SurefireReflector reflector = new SurefireReflector(getClass().getClassLoader());
        final InvocationHandler handler = newClassLoaderProxy(reflector, new GreetingDelegate());
        final GreetingService proxy = (GreetingService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] {GreetingService.class}, handler);

        final String greeting = proxy.greet("Maven Surefire");

        assertThat(greeting).isEqualTo("Hello, Maven Surefire");
    }

    private static InvocationHandler newClassLoaderProxy(final SurefireReflector reflector, final Object delegate)
            throws Exception {
        final Class<?> proxyClass = Arrays.stream(SurefireReflector.class.getDeclaredClasses())
                .filter(candidate -> "ClassLoaderProxy".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        final Constructor<?> constructor = proxyClass.getDeclaredConstructor(SurefireReflector.class, Object.class);
        constructor.setAccessible(true);
        return (InvocationHandler) constructor.newInstance(reflector, delegate);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class GreetingDelegate implements GreetingService {
        @Override
        public String greet(final String name) {
            return "Hello, " + name;
        }
    }
}
