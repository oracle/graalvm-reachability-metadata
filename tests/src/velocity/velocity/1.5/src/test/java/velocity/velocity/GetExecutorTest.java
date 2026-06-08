/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
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
    void executeReturnsNullWhenNoGetMethodIsAvailable() throws Exception {
        final Log log = new Log(new NullLogChute());
        final GetExecutor executor = new GetExecutor(
                log, new Introspector(log), Object.class, "missing");

        assertThat(executor.execute(new Object())).isNull();
    }

    @Test
    void executePropagatesGetMethodInvocationExceptions() throws Exception {
        final Log log = new Log(new NullLogChute());
        final GetExecutor executor = new GetExecutor(
                log, new Introspector(log), FailingKeyValueStore.class, "boom");

        assertThatThrownBy(() -> executor.execute(new FailingKeyValueStore()))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Cannot resolve boom");
    }

    private static GetExecutor newExecutor(final String key) throws Exception {
        final Log log = new Log(new NullLogChute());
        return new GetExecutor(log, new Introspector(log), KeyValueStore.class, key);
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
}
