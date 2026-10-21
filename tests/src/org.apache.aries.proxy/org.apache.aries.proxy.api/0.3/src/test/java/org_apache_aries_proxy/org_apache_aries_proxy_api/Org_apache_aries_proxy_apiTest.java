/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_proxy.org_apache_aries_proxy_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.aries.proxy.FinalModifierException;
import org.apache.aries.proxy.InvocationHandlerWrapper;
import org.apache.aries.proxy.ProxyManager;
import org.apache.aries.proxy.UnableToProxyException;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

public class Org_apache_aries_proxy_apiTest {
    @Test
    void proxyManagerCreatesAndRecognizesADelegatingProxy() throws Exception {
        SimpleProxyManager manager = new SimpleProxyManager();
        GreetingService target = name -> "Hello, " + name;

        GreetingService proxy =
                (GreetingService) manager.createProxy(null, List.of(GreetingService.class), () -> target);

        assertThat(proxy.greet("Aries")).isEqualTo("Hello, Aries");
        assertThat(manager.isProxy(proxy)).isTrue();
        assertThat(manager.isProxy(target)).isFalse();
        assertThat(manager.unwrap(proxy).call()).isSameAs(target);
        assertThat(manager.unwrap(target)).isNull();
    }

    @Test
    void invocationHandlerWrapperDecoratesCallsToTheDelegate() throws Exception {
        SimpleProxyManager manager = new SimpleProxyManager();
        GreetingService target = name -> "Hello, " + name;
        DecoratingInvocationHandlerWrapper wrapper = new DecoratingInvocationHandlerWrapper();

        GreetingService proxy =
                (GreetingService) manager.createProxy(null, List.of(GreetingService.class), () -> target, wrapper);

        assertThat(proxy.greet("OSGi")).isEqualTo("[Hello, OSGi]");
        assertThat(wrapper.getInvocationCount()).isOne();
        assertThat(wrapper.getLastMethodName()).isEqualTo("greet");
    }

    @Test
    void finalModifierExceptionDescribesFinalClassesAndMethods() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);

            FinalModifierException finalClass = new FinalModifierException(FinalService.class);
            assertThat(finalClass.isFinalClass()).isTrue();
            assertThat(finalClass.getFinalMethods()).isNull();
            assertThat(finalClass.getClassName()).isEqualTo(FinalService.class.getName());
            assertThat(finalClass.getMessage())
                    .isEqualTo("The class " + FinalService.class.getName() + " is final.");

            FinalModifierException finalMethods =
                    new FinalModifierException(ServiceWithFinalMethod.class, "finish()");
            assertThat(finalMethods.isFinalClass()).isFalse();
            assertThat(finalMethods.getFinalMethods()).isEqualTo("finish()");
            assertThat(finalMethods.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
            assertThat(finalMethods.getMessage())
                    .isEqualTo("The methods finish() in class "
                            + ServiceWithFinalMethod.class.getName()
                            + " are final.");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void unableToProxyExceptionPreservesClassNamesAndCauses() {
        UnableToProxyException classOnly = new UnableToProxyException(ServiceWithFinalMethod.class);
        assertThat(classOnly.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
        assertThat(classOnly.getMessage()).isEqualTo(ServiceWithFinalMethod.class.getName());

        Exception classCause = new Exception("class cause");
        UnableToProxyException fromClass = new UnableToProxyException(ServiceWithFinalMethod.class, classCause);
        assertThat(fromClass.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
        assertThat(fromClass.getCause()).isSameAs(classCause);

        Throwable namedCause = new IllegalStateException("named cause");
        UnableToProxyException fromName = new UnableToProxyException("example.Service", namedCause);
        assertThat(fromName.getClassName()).isEqualTo("example.Service");
        assertThat(fromName.getCause()).isSameAs(namedCause);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class FinalService {}

    public static class ServiceWithFinalMethod {
        public final void finish() {}
    }

    private static final class DecoratingInvocationHandlerWrapper implements InvocationHandlerWrapper {
        private int invocationCount;
        private String lastMethodName;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments, InvocationHandler delegate)
                throws Throwable {
            invocationCount++;
            lastMethodName = method.getName();
            return "[" + delegate.invoke(proxy, method, arguments) + "]";
        }

        int getInvocationCount() {
            return invocationCount;
        }

        String getLastMethodName() {
            return lastMethodName;
        }
    }

    private static final class SimpleProxyManager implements ProxyManager {
        private final Map<Object, Callable<Object>> dispatchers = new IdentityHashMap<>();

        @Override
        public Object createProxy(Bundle clientBundle, Collection<Class<?>> classes, Callable<Object> dispatcher)
                throws UnableToProxyException {
            return createProxy(clientBundle, classes, dispatcher, null);
        }

        @Override
        public Object createProxy(
                Bundle clientBundle,
                Collection<Class<?>> classes,
                Callable<Object> dispatcher,
                InvocationHandlerWrapper wrapper)
                throws UnableToProxyException {
            InvocationHandler delegate = (proxy, method, arguments) ->
                    invokeGreetingService(dispatcher.call(), proxy, method, arguments);
            InvocationHandler handler = wrapper == null
                    ? delegate
                    : (proxy, method, arguments) -> wrapper.invoke(proxy, method, arguments, delegate);
            Object proxy = Proxy.newProxyInstance(
                    Org_apache_aries_proxy_apiTest.class.getClassLoader(), classes.toArray(new Class<?>[0]), handler);
            dispatchers.put(proxy, dispatcher);
            return proxy;
        }

        @Override
        public Callable<Object> unwrap(Object proxy) {
            return dispatchers.get(proxy);
        }

        @Override
        public boolean isProxy(Object proxy) {
            return dispatchers.containsKey(proxy);
        }

        private static Object invokeGreetingService(Object target, Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals":
                        return proxy == arguments[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "GreetingService proxy";
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            }
            return ((GreetingService) target).greet((String) arguments[0]);
        }
    }
}
