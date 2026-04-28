/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import sun.misc.Unsafe;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.extended.DynamicProxyConverter;
import com.thoughtworks.xstream.converters.reflection.SunLimitedUnsafeReflectionProvider;
import com.thoughtworks.xstream.core.util.Fields;
import com.thoughtworks.xstream.security.ProxyTypePermission;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DynamicProxyConverterTest {
    @Test
    @Order(1)
    @SuppressWarnings("deprecation")
    void roundTripsDynamicProxyAndRestoresInvocationHandler() {
        XStream xstream = configuredXStream();
        GreetingService originalProxy = newGreetingProxy("Hello");
        DynamicProxyConverter converter = new DynamicProxyConverter(xstream.getMapper());

        assertThat(converter.canConvert(originalProxy.getClass())).isTrue();

        String xml = xstream.toXML(originalProxy);
        Object restoredObject = xstream.fromXML(xml);

        assertThat(xml).contains("<interface>" + GreetingService.class.getName() + "</interface>");
        assertThat(restoredObject).isInstanceOf(GreetingService.class).isInstanceOf(CallCounter.class);

        GreetingService restoredGreetingService = (GreetingService)restoredObject;
        CallCounter restoredCallCounter = (CallCounter)restoredObject;
        assertThat(restoredGreetingService.greet("Ada")).isEqualTo("Hello, Ada");
        assertThat(restoredCallCounter.invocationCount()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void unmarshalsDynamicProxyWhenProxyHandlerFieldIsUnavailable() {
        XStream xstream = configuredXStream();
        GreetingService originalProxy = newGreetingProxy("Welcome");
        String xml = xstream.toXML(originalProxy);

        withProxyHandlerField(null, () -> {
            Object restoredObject = xstream.fromXML(xml);

            assertThat(restoredObject).isInstanceOf(GreetingService.class).isInstanceOf(CallCounter.class);
            GreetingService restoredGreetingService = (GreetingService)restoredObject;
            CallCounter restoredCallCounter = (CallCounter)restoredObject;
            assertThat(restoredGreetingService.greet("Grace")).isEqualTo("Welcome, Grace");
            assertThat(restoredCallCounter.invocationCount()).isEqualTo(1);
        });
    }

    private static XStream configuredXStream() {
        XStream xstream = new XStream();
        xstream.addPermission(ProxyTypePermission.PROXIES);
        xstream.allowTypes(new Class[]{
                CallCounter.class,
                CountingInvocationHandler.class,
                GreetingService.class
        });
        return xstream;
    }

    private static GreetingService newGreetingProxy(String greeting) {
        return (GreetingService)Proxy.newProxyInstance(
                DynamicProxyConverterTest.class.getClassLoader(),
                new Class[]{GreetingService.class, CallCounter.class},
                new CountingInvocationHandler(greeting));
    }

    private static void withProxyHandlerField(Object value, Runnable action) {
        Field handlerField = Fields.find(reflectionsClass(), "HANDLER");
        Unsafe unsafe = UnsafeAccess.unsafe();
        Object base = unsafe.staticFieldBase(handlerField);
        long offset = unsafe.staticFieldOffset(handlerField);
        Object previous = unsafe.getObject(base, offset);
        unsafe.putObject(base, offset, value);
        try {
            action.run();
        } finally {
            unsafe.putObject(base, offset, previous);
        }
    }

    private static Class<?> reflectionsClass() {
        for (Class<?> declaredClass : DynamicProxyConverter.class.getDeclaredClasses()) {
            if ("Reflections".equals(declaredClass.getSimpleName())) {
                return declaredClass;
            }
        }
        throw new IllegalStateException("DynamicProxyConverter Reflections class not found");
    }

    private static final class UnsafeAccess extends SunLimitedUnsafeReflectionProvider {
        private static Unsafe unsafe() {
            return unsafe;
        }
    }

    public interface GreetingService {
        String greet(String name);
    }

    public interface CallCounter {
        int invocationCount();
    }

    public static final class CountingInvocationHandler implements InvocationHandler {
        private String greeting;
        private int invocationCount;

        public CountingInvocationHandler() {
        }

        CountingInvocationHandler(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, methodName, args);
            }
            if ("greet".equals(methodName)) {
                invocationCount++;
                return greeting + ", " + args[0];
            }
            if ("invocationCount".equals(methodName)) {
                return invocationCount;
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private static Object invokeObjectMethod(Object proxy, String methodName, Object[] args) {
            if ("toString".equals(methodName)) {
                return "CountingInvocationHandler proxy";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(methodName);
        }
    }
}
