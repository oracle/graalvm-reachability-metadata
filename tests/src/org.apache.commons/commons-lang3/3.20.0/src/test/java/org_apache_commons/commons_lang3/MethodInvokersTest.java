/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.function.MethodInvokers;
import org.junit.jupiter.api.Test;

public class MethodInvokersTest {

    @Test
    public void asFunctionInvokesPublicInstanceMethod() throws Exception {
        assumeFalse(isNativeImageRuntime(), "MethodHandleProxies runtime class definition is not supported in native image");
        Method method = String.class.getMethod("length");

        Function<String, Integer> length = MethodInvokers.asFunction(method);

        assertThat(length.apply("commons")).isEqualTo(7);
    }

    @Test
    public void asInterfaceInstanceInvokesPublicStaticMethod() throws Exception {
        assumeFalse(isNativeImageRuntime(), "MethodHandleProxies runtime class definition is not supported in native image");
        Method method = MethodInvokersTest.class.getMethod("libraryName");

        Supplier<String> supplier = MethodInvokers.asInterfaceInstance(Supplier.class, method);

        assertThat(supplier.get()).isEqualTo("commons-lang3");
    }

    public static String libraryName() {
        return "commons-lang3";
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
