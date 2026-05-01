/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.lang.reflect.Method;
import java.util.List;

import com.diffplug.common.base.Throwables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowablesTest {
    @Test
    public void lazyStackTraceReturnsIndexedStackFrames() {
        IllegalStateException failure = createFailure();

        List<StackTraceElement> stackTrace = Throwables.lazyStackTrace(failure);

        assertThat(stackTrace).isNotEmpty();
        assertThat(stackTrace.size()).isEqualTo(failure.getStackTrace().length);
        assertThat(stackTrace.get(0).getMethodName()).isEqualTo("createFailure");
    }

    @Test
    public void privateInvokerDelegatesToSuppliedNonThrowingMethod() throws Exception {
        Method targetMethod = Throwables.class.getMethod("lazyStackTraceIsLazy");
        Method invokerMethod = Throwables.class.getDeclaredMethod(
                "invokeAccessibleNonThrowingMethod",
                Method.class,
                Object.class,
                Object[].class);
        invokerMethod.setAccessible(true);

        Object result = invokerMethod.invoke(null, targetMethod, null, new Object[0]);

        assertThat(result).isEqualTo(Throwables.lazyStackTraceIsLazy());
    }

    private static IllegalStateException createFailure() {
        return new IllegalStateException("lazy stack trace probe");
    }
}
