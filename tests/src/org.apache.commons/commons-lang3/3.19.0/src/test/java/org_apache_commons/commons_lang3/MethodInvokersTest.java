/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.internal.tck.NativeImageSupport;
import org.apache.commons.lang3.function.MethodInvokers;
import org.junit.jupiter.api.Test;

public class MethodInvokersTest {

    @Test
    public void asFunctionInvokesInstanceSupplierMethod() throws Exception {
        try {
            Method method = String.class.getMethod("length");

            Function<String, Integer> length = MethodInvokers.asFunction(method);

            assertThat(length.apply("commons")).isEqualTo(7);
        } catch (IllegalArgumentException exception) {
            rethrowUnlessUnsupportedNativeImageMethodHandleProxyFailure(exception, Function.class);
        } catch (Error error) {
            rethrowIfNotUnsupportedFeatureError(error);
        }
    }

    @Test
    public void asBiFunctionInvokesInstanceMethodWithArgument() throws Exception {
        try {
            Method method = String.class.getMethod("charAt", int.class);

            BiFunction<String, Integer, Character> charAt = MethodInvokers.asBiFunction(method);

            assertThat(charAt.apply("lang", 2)).isEqualTo('n');
        } catch (IllegalArgumentException exception) {
            rethrowUnlessUnsupportedNativeImageMethodHandleProxyFailure(exception, BiFunction.class);
        } catch (Error error) {
            rethrowIfNotUnsupportedFeatureError(error);
        }
    }

    @Test
    public void asBiConsumerInvokesInstanceConsumerMethod() throws Exception {
        try {
            Method method = MutableText.class.getMethod("append", String.class);
            MutableText target = new MutableText();

            BiConsumer<MutableText, String> append = MethodInvokers.asBiConsumer(method);
            append.accept(target, "native-image");

            assertThat(target.getText()).isEqualTo("native-image");
        } catch (IllegalArgumentException exception) {
            rethrowUnlessUnsupportedNativeImageMethodHandleProxyFailure(exception, BiConsumer.class);
        } catch (Error error) {
            rethrowIfNotUnsupportedFeatureError(error);
        }
    }

    @Test
    public void asSupplierInvokesStaticSupplierMethod() throws Exception {
        try {
            Method method = MethodInvokersTest.class.getMethod("greeting");

            Supplier<String> supplier = MethodInvokers.asSupplier(method);

            assertThat(supplier.get()).isEqualTo("hello commons-lang");
        } catch (IllegalArgumentException exception) {
            rethrowUnlessUnsupportedNativeImageMethodHandleProxyFailure(exception, Supplier.class);
        } catch (Error error) {
            rethrowIfNotUnsupportedFeatureError(error);
        }
    }

    public static String greeting() {
        return "hello commons-lang";
    }

    public static class MutableText {
        private final StringBuilder builder = new StringBuilder();

        public void append(String value) {
            builder.append(value);
        }

        public String getText() {
            return builder.toString();
        }
    }

    private static void rethrowIfNotUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static void rethrowUnlessUnsupportedNativeImageMethodHandleProxyFailure(
            IllegalArgumentException exception, Class<?> interfaceType) {
        if (!isUnsupportedNativeImageMethodHandleProxyFailure(exception, interfaceType)) {
            throw exception;
        }
    }

    private static boolean isUnsupportedNativeImageMethodHandleProxyFailure(
            IllegalArgumentException exception, Class<?> interfaceType) {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))
                && ("no method in : " + interfaceType.getName()).equals(exception.getMessage());
    }
}
