/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class SurefireReflectorInnerClassLoaderProxyTest {
    @Test
    void proxyInvokesMatchingPublicMethodOnDelegate() throws Throwable {
        SurefireReflector reflector = new SurefireReflector(SurefireReflector.class.getClassLoader());
        AtomicBoolean delegateWasCalled = new AtomicBoolean(false);
        InvocationHandler handler = createClassLoaderProxy(reflector, new RecordingRunnable(delegateWasCalled));

        Runnable runnable = (Runnable) Proxy.newProxyInstance(
                Runnable.class.getClassLoader(),
                new Class<?>[] { Runnable.class },
                handler);
        runnable.run();

        assertThat(delegateWasCalled.get()).isTrue();
    }

    private static InvocationHandler createClassLoaderProxy(SurefireReflector reflector, Object delegate)
            throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(SurefireReflector.class, MethodHandles.lookup());
        Class<?> proxyClass = lookup.findClass(SurefireReflector.class.getName() + "$ClassLoaderProxy");
        MethodType constructorType = MethodType.methodType(void.class, SurefireReflector.class, Object.class);
        MethodHandle constructor = lookup.findConstructor(proxyClass, constructorType);
        return (InvocationHandler) constructor.invoke(reflector, delegate);
    }

    public static final class RecordingRunnable implements Runnable {
        private final AtomicBoolean called;

        public RecordingRunnable(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public void run() {
            called.set(true);
        }
    }
}
