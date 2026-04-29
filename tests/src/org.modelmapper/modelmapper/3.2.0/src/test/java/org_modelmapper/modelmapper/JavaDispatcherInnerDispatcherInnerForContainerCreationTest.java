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

public class JavaDispatcherInnerDispatcherInnerForContainerCreationTest {
    @Test
    void createsContainerForProxiedType() {
        StringContainerDispatcher dispatcher = new TestJavaDispatcher<>(StringContainerDispatcher.class).run();

        String[] container = dispatcher.create(3);

        assertThat(container).hasSize(3).containsOnlyNulls();
        assertThat(container.getClass().getComponentType()).isEqualTo(String.class);
    }

    @JavaDispatcher.Proxied("java.lang.String")
    public interface StringContainerDispatcher {
        @JavaDispatcher.Container
        String[] create(int size);
    }

    private static final class TestJavaDispatcher<T> extends JavaDispatcher<T> {
        private TestJavaDispatcher(Class<T> proxy) {
            super(proxy, JavaDispatcherInnerDispatcherInnerForContainerCreationTest.class.getClassLoader(), false);
        }
    }
}
