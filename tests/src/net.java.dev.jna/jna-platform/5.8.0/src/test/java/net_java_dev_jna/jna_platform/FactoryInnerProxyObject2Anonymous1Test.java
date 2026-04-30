/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.COM.util.Factory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

public class FactoryInnerProxyObject2Anonymous1Test {

    @Test
    void invokesDelegateMethodThroughProxyObject2Callable() throws Throwable {
        RecordingComInterface delegate = new RecordingComInterface();
        Callable<?> callable = createProxyObject2Callable(delegate, "native-image");

        Object result = callable.call();

        assertThat(result).isEqualTo("handled native-image");
        assertThat(delegate.lastValue).isEqualTo("native-image");
        assertThat(delegate.invocationCount).isEqualTo(1);
    }

    private static Callable<?> createProxyObject2Callable(RecordingComInterface delegate, String value)
            throws Throwable {
        ClassLoader classLoader = FactoryInnerProxyObject2Anonymous1Test.class.getClassLoader();
        Class<?> proxyObjectClass = Class.forName(
            "com.sun.jna.platform.win32.COM.util.Factory$ProxyObject2",
            false,
            classLoader
        );
        Class<?> callableClass = Class.forName(
            "com.sun.jna.platform.win32.COM.util.Factory$ProxyObject2$1",
            false,
            classLoader
        );

        MethodHandles.Lookup proxyLookup = MethodHandles.privateLookupIn(proxyObjectClass, MethodHandles.lookup());
        MethodType proxyConstructorType = MethodType.methodType(void.class, Factory.class, Object.class);
        MethodHandle proxyConstructor = proxyLookup.findConstructor(proxyObjectClass, proxyConstructorType);
        Object proxyObject = proxyConstructor.invoke(null, delegate);

        Method method = SampleComInterface.class.getMethod("describe", String.class);
        MethodHandles.Lookup callableLookup = MethodHandles.privateLookupIn(callableClass, MethodHandles.lookup());
        MethodType callableConstructorType = MethodType.methodType(
            void.class,
            proxyObjectClass,
            Method.class,
            Object[].class
        );
        MethodHandle callableConstructor = callableLookup.findConstructor(callableClass, callableConstructorType);
        return (Callable<?>) callableConstructor.invoke(proxyObject, method, new Object[] { value });
    }

    public interface SampleComInterface {

        String describe(String value);

    }

    public static class RecordingComInterface implements SampleComInterface {

        private String lastValue;
        private int invocationCount;

        @Override
        public String describe(String value) {
            this.lastValue = value;
            this.invocationCount++;
            return "handled " + value;
        }
    }

}
