/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.parser.node.GetExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class GetExecutorTest {
    @Test
    void executeInvokesResolvedGetMethod() throws Exception {
        final GetExecutor executor = newExecutor("answer");
        final KeyValueStore store = new KeyValueStore();

        final Object result = executor.execute(store);

        assertThat(result).isEqualTo("value:answer");
        assertThat(store.getLastKey()).isEqualTo("answer");
    }

    @Test
    void oldExecuteInvokesResolvedGetMethod() throws Exception {
        final GetExecutor executor = newExecutor("legacy");
        final KeyValueStore store = new KeyValueStore();

        final Object result = executor.OLDexecute(store, null);

        assertThat(result).isEqualTo("value:legacy");
        assertThat(store.getLastKey()).isEqualTo("legacy");
    }

    @Test
    void oldExecuteWrapsGetMethodExceptions() throws Exception {
        final NoOpRuntimeLogger logger = new NoOpRuntimeLogger();
        final GetExecutor executor = new GetExecutor(logger, new Introspector(logger), FailingKeyValueStore.class,
                "boom");

        assertThatThrownBy(() -> executor.OLDexecute(new FailingKeyValueStore(), null))
                .isInstanceOf(MethodInvocationException.class)
                .hasMessageContaining("Invocation of method 'get(\"boom\")'");
    }

    private static GetExecutor newExecutor(final String key) throws Exception {
        final NoOpRuntimeLogger logger = new NoOpRuntimeLogger();
        return new GetExecutor(logger, new Introspector(logger), KeyValueStore.class, key);
    }

    public static final class KeyValueStore {
        private String lastKey;

        public String get(final String key) {
            lastKey = key;
            return "value:" + key;
        }

        String getLastKey() {
            return lastKey;
        }
    }

    public static final class FailingKeyValueStore {
        public String get(final String key) {
            throw new IllegalStateException("Cannot resolve " + key);
        }
    }

    private static final class NoOpRuntimeLogger implements RuntimeLogger {
        @Override
        public void warn(final Object message) {
        }

        @Override
        public void info(final Object message) {
        }

        @Override
        public void error(final Object message) {
        }

        @Override
        public void debug(final Object message) {
        }
    }
}
