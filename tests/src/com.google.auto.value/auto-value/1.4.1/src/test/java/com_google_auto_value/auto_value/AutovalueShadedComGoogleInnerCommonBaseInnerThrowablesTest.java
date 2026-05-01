/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.base.$Throwables;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonBaseInnerThrowablesTest {
    private static final MethodHandle INVOKE_ACCESSIBLE_NON_THROWING_METHOD = throwablesMethod(
            "invokeAccessibleNonThrowingMethod",
            MethodType.methodType(Object.class, Method.class, Object.class, Object[].class)
    );

    @Test
    void lazyStackTraceExposesThrowableFrames() {
        Throwable throwable = new Throwable("stack trace source");

        List<StackTraceElement> stackTrace = $Throwables.lazyStackTrace(throwable);

        assertThat(stackTrace).containsExactly(throwable.getStackTrace());
    }

    @Test
    void accessibleMethodInvokerReturnsUnderlyingMethodResult() throws Throwable {
        Method substringMethod = String.class.getMethod("substring", int.class, int.class);
        Object target = "reachability";
        Object[] arguments = {Integer.valueOf(0), Integer.valueOf(5)};

        Object result = INVOKE_ACCESSIBLE_NON_THROWING_METHOD.invokeExact(substringMethod, target, arguments);

        assertThat(result).isEqualTo("reach");
    }

    private static MethodHandle throwablesMethod(String methodName, MethodType methodType) {
        try {
            return MethodHandles.privateLookupIn($Throwables.class, MethodHandles.lookup())
                    .findStatic($Throwables.class, methodName, methodType);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
