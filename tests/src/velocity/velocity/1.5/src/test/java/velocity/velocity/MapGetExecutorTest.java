/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.parser.node.MapGetExecutor;
import org.junit.jupiter.api.Test;

public class MapGetExecutorTest {
    @Test
    void discoversMapGetMethodForDirectMapImplementations() throws Exception {
        final DirectMap values = new DirectMap();
        values.put("answer", "forty-two");
        final MapGetExecutor executor = new MapGetExecutor(
                new Log(new NullLogChute()), DirectMap.class, "answer");

        final Object result = executor.execute(values);

        assertThat(executor.isAlive()).isTrue();
        assertThat(executor.getMethod().getName()).isEqualTo("get");
        assertThat(result).isEqualTo("forty-two");
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
