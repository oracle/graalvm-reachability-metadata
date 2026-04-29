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

public class JavaDispatcherInnerDispatcherInnerForDefaultValueInnerOfNonPrimitiveArrayTest {
    @Test
    void returnsEmptyArrayDefaultForUnavailableProxiedType() {
        ArrayReturningDispatcher dispatcher = new NonGeneratingJavaDispatcher<>(ArrayReturningDispatcher.class).run();

        String[] values = dispatcher.values();

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).isEmpty();
    }

    @JavaDispatcher.Defaults
    @JavaDispatcher.Proxied("org.modelmapper.dynamicaccess.MissingArrayDispatcherTarget")
    public interface ArrayReturningDispatcher {
        String[] values();
    }

    private static final class NonGeneratingJavaDispatcher<T> extends JavaDispatcher<T> {
        private NonGeneratingJavaDispatcher(Class<T> proxy) {
            super(proxy, JavaDispatcherInnerDispatcherInnerForDefaultValueInnerOfNonPrimitiveArrayTest.class.getClassLoader(), false);
        }
    }
}
