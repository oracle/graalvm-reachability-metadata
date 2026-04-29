/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.utility.dispatcher.JavaDispatcher;

public class JavaDispatcherInnerDirectInvokerTest {
    @Test
    void dispatchesConstructorAndInstanceMethodThroughInvoker() {
        DispatcherTargetDispatcher dispatcher = new TestJavaDispatcher<>(
            DispatcherTargetDispatcher.class).run();

        DispatcherTarget target = dispatcher.make("direct");
        DispatcherTarget returned = dispatcher.appendValue(target, "-invoker");

        assertThat(returned).isSameAs(target);
        assertThat(target.value()).isEqualTo("direct-invoker");
    }

    @JavaDispatcher.Proxied("org_modelmapper.modelmapper.JavaDispatcherInnerDirectInvokerTest$DispatcherTarget")
    public interface DispatcherTargetDispatcher {
        @JavaDispatcher.IsConstructor
        DispatcherTarget make(String value);

        @JavaDispatcher.Proxied("append")
        DispatcherTarget appendValue(DispatcherTarget target, String suffix);
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

    private static final class TestJavaDispatcher<T> extends JavaDispatcher<T> {
        private TestJavaDispatcher(Class<T> proxy) {
            super(proxy, JavaDispatcherInnerDirectInvokerTest.class.getClassLoader(), false);
        }
    }
}
