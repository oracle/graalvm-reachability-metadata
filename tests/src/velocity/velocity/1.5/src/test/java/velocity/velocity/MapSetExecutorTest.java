/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

public class MapSetExecutorTest {
    @Test
    void setsDirectMapImplementationEntriesFromTemplatePropertyAssignment() throws Exception {
        final VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        velocityEngine.init();

        final DirectMap values = new DirectMap();
        values.put("answer", "old-value");
        final VelocityContext context = new VelocityContext();
        context.put("values", values);

        final StringWriter writer = new StringWriter();
        final boolean evaluated = velocityEngine.evaluate(
                context,
                writer,
                "MapSetExecutorTest",
                "#set($values.answer = 'forty-two')$values.answer");

        assertThat(evaluated).isTrue();
        assertThat(writer).hasToString("forty-two");
        assertThat(values).containsEntry("answer", "forty-two");
    }

    public static final class DirectMap implements Map<Object, Object> {
        private final Map<Object, Object> delegate = new LinkedHashMap<>();

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(final Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public boolean containsValue(final Object value) {
            return delegate.containsValue(value);
        }

        @Override
        public Object get(final Object key) {
            return delegate.get(key);
        }

        @Override
        public Object put(final Object key, final Object value) {
            return delegate.put(key, value);
        }

        @Override
        public Object remove(final Object key) {
            return delegate.remove(key);
        }

        @Override
        public void putAll(final Map<?, ?> values) {
            delegate.putAll(values);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Set<Object> keySet() {
            return delegate.keySet();
        }

        @Override
        public Collection<Object> values() {
            return delegate.values();
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return delegate.entrySet();
        }
    }
}
