/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.utility.Invoker;

public class JavaDispatcherInnerDirectInvokerTest {
    private static final String DIRECT_INVOKER_TYPE_NAME =
        "org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher$DirectInvoker";

    @Test
    void directInvokerCallsConstructorAndMethodReflectionContracts() throws Exception {
        Invoker invoker = directInvoker();
        Constructor<DispatcherTarget> constructor = DispatcherTarget.class.getConstructor(String.class);
        Method append = DispatcherTarget.class.getMethod("append", String.class);

        DispatcherTarget target = (DispatcherTarget) invoker.newInstance(constructor, new Object[] {"direct"});
        Object returned = invoker.invoke(append, target, new Object[] {"-invoker"});

        assertThat(returned).isSameAs(target);
        assertThat(target.value()).isEqualTo("direct-invoker");
    }

    private static Invoker directInvoker() throws Exception {
        Constructor<?> constructor = Class.forName(DIRECT_INVOKER_TYPE_NAME).getDeclaredConstructor();
        constructor.setAccessible(true);
        return (Invoker) constructor.newInstance();
    }


    public static final class DispatcherTarget {
        private final StringBuilder value;

        public DispatcherTarget(String value) {
            this.value = new StringBuilder(value);
        }

        public DispatcherTarget append(String suffix) {
            value.append(suffix);
            return this;
        }

        private String value() {
            return value.toString();
        }
    }

}
