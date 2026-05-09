/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import javassist.util.proxy.MethodHandler;
import org.jboss.interceptor.proxy.javassist.CompositeHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeHandlerTest {

    @Test
    void invokesProceedMethodForObjectMethod() throws Throwable {
        final CompositeHandler compositeHandler = new CompositeHandler(List.of(failingMethodHandler()));
        final SampleTarget target = new SampleTarget();
        final Method thisMethod = Object.class.getMethod("toString");
        final Method proceedMethod = SampleTarget.class.getMethod("proceedToString");

        final Object result = compositeHandler.invoke(target, thisMethod, proceedMethod, new Object[] {});

        assertThat(result).isEqualTo("proceeded-to-string");
    }

    @Test
    void invokesProceedMethodWhenHandlerChainIsExhausted() throws Throwable {
        final CompositeHandler compositeHandler = new CompositeHandler(Collections.emptyList());
        final SampleTarget target = new SampleTarget();
        final Method thisMethod = SampleTarget.class.getMethod("echo", String.class);
        final Method proceedMethod = SampleTarget.class.getMethod("proceedEcho", String.class);

        final Object result = compositeHandler.invoke(target, thisMethod, proceedMethod, new Object[] {"value"});

        assertThat(result).isEqualTo("proceeded-value");
    }

    private static MethodHandler failingMethodHandler() {
        return (self, thisMethod, proceed, args) -> {
            throw new AssertionError("Object methods should proceed without invoking the handler chain");
        };
    }

    public static class SampleTarget {

        public String echo(String value) {
            return "echo-" + value;
        }

        public String proceedEcho(String value) {
            return "proceeded-" + value;
        }

        public String proceedToString() {
            return "proceeded-to-string";
        }
    }
}
