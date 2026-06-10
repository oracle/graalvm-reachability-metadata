/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.function.MethodInvokers;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodInvokersTest {

    @Test
    public void asBiFunctionInvokesSingleArgumentInstanceMethod() throws Exception {
        try {
            Method method = String.class.getMethod("charAt", int.class);
            BiFunction<String, Integer, Character> function = MethodInvokers.asBiFunction(method);

            assertThat(function.apply("commons", 1)).isEqualTo('o');
        } catch (Throwable throwable) {
            rethrowUnlessUnsupportedMethodHandleProxy(throwable);
        }
    }

    @Test
    public void asFunctionInvokesNoArgumentInstanceMethod() throws Exception {
        try {
            Method method = String.class.getMethod("length");
            Function<String, Integer> function = MethodInvokers.asFunction(method);

            assertThat(function.apply("commons-lang")).isEqualTo(12);
        } catch (Throwable throwable) {
            rethrowUnlessUnsupportedMethodHandleProxy(throwable);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void asInterfaceInstanceAdaptsMethodToFunctionalInterface() throws Exception {
        try {
            Method method = String.class.getMethod("trim");
            Function<String, String> function = MethodInvokers.asInterfaceInstance(Function.class, method);

            assertThat(function.apply("  lang  ")).isEqualTo("lang");
        } catch (Throwable throwable) {
            rethrowUnlessUnsupportedMethodHandleProxy(throwable);
        }
    }

    @Test
    public void asSupplierInvokesStaticNoArgumentMethod() throws Exception {
        try {
            Method method = System.class.getMethod("lineSeparator");
            Supplier<String> supplier = MethodInvokers.asSupplier(method);

            assertThat(supplier.get()).isEqualTo(System.lineSeparator());
        } catch (Throwable throwable) {
            rethrowUnlessUnsupportedMethodHandleProxy(throwable);
        }
    }

    private static void rethrowUnlessUnsupportedMethodHandleProxy(Throwable throwable) {
        if (throwable instanceof Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw error;
        }
        if (throwable instanceof IllegalArgumentException illegalArgumentException
                && illegalArgumentException.getMessage() != null
                && illegalArgumentException.getMessage().startsWith("no method in : java.util.function.")) {
            return;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new AssertionError(throwable);
    }
}
