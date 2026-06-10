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

import org.apache.commons.lang3.function.MethodInvokers;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodInvokersTest {

    @Test
    public void asFunctionInvokesPublicInstanceMethod() throws Exception {
        try {
            Method method = TextTarget.class.getMethod("length");

            Function<TextTarget, Integer> function = MethodInvokers.asFunction(method);

            assertThat(function.apply(new TextTarget("commons"))).isEqualTo(7);
        } catch (Error error) {
            rethrowIfNotNativeImageMethodHandleProxyFailure(error);
        }
    }

    @Test
    public void asBiFunctionInvokesPublicInstanceMethodWithArgument() throws Exception {
        try {
            Method method = TextTarget.class.getMethod("append", String.class);

            BiFunction<TextTarget, String, String> function = MethodInvokers.asBiFunction(method);

            assertThat(function.apply(new TextTarget("commons"), "-lang")).isEqualTo("commons-lang");
        } catch (Error error) {
            rethrowIfNotNativeImageMethodHandleProxyFailure(error);
        }
    }

    @Test
    public void asBiConsumerInvokesPublicInstanceConsumerMethod() throws Exception {
        try {
            Method method = MutableTextTarget.class.getMethod("replaceWith", String.class);
            MutableTextTarget target = new MutableTextTarget("before");

            BiConsumer<MutableTextTarget, String> consumer = MethodInvokers.asBiConsumer(method);
            consumer.accept(target, "after");

            assertThat(target.getValue()).isEqualTo("after");
        } catch (Error error) {
            rethrowIfNotNativeImageMethodHandleProxyFailure(error);
        }
    }

    @Test
    public void asSupplierInvokesPublicStaticMethod() throws Exception {
        try {
            Method method = StaticTextTarget.class.getMethod("defaultText");

            Supplier<String> supplier = MethodInvokers.asSupplier(method);

            assertThat(supplier.get()).isEqualTo("default");
        } catch (Error error) {
            rethrowIfNotNativeImageMethodHandleProxyFailure(error);
        }
    }

    @Test
    public void asInterfaceInstanceAdaptsPublicStaticMethodToCustomInterface() throws Exception {
        try {
            Method method = StaticTextTarget.class.getMethod("decorate", String.class);

            Decorator decorator = MethodInvokers.asInterfaceInstance(Decorator.class, method);

            assertThat(decorator.decorate("commons")).isEqualTo("[commons]");
        } catch (Error error) {
            rethrowIfNotNativeImageMethodHandleProxyFailure(error);
        }
    }

    private static void rethrowIfNotNativeImageMethodHandleProxyFailure(Error error) {
        if (!hasNativeImageMethodHandleProxyFailure(error)) {
            throw error;
        }
    }

    private static boolean hasNativeImageMethodHandleProxyFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if ((current instanceof NoSuchMethodError || current instanceof NoSuchMethodException)
                    && current.getMessage() != null
                    && current.getMessage().contains("jdk.MHProxy")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public interface Decorator {
        String decorate(String value);
    }

    public static class TextTarget {
        private final String value;

        public TextTarget(String value) {
            this.value = value;
        }

        public int length() {
            return value.length();
        }

        public String append(String suffix) {
            return value + suffix;
        }
    }

    public static class MutableTextTarget {
        private String value;

        public MutableTextTarget(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void replaceWith(String replacement) {
            value = replacement;
        }
    }

    public static class StaticTextTarget {
        public static String defaultText() {
            return "default";
        }

        public static String decorate(String value) {
            return "[" + value + "]";
        }
    }
}
