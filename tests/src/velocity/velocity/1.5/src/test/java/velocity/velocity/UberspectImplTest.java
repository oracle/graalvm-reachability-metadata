/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelPropertySet;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void initializesIntrospectorAndReturnsIteratorForArrays() throws Exception {
        final UberspectImpl uberspect = newUberspect();
        final Info info = new Info("array.vm", 1, 1);

        final Iterator<?> iterator = uberspect.getIterator(new String[] {"a", "b"}, info);

        assertThat(iterator).isNotNull();
        assertThat(iterator.next()).isEqualTo("a");
        assertThat(iterator.next()).isEqualTo("b");
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void resolvesMapClassLiteralWhenCreatingMapBackedPropertySetter() throws Exception {
        final UberspectImpl uberspect = newUberspect();
        final RecordingMap values = new RecordingMap();
        final Info info = new Info("map-put.vm", 1, 1);

        final VelPropertySet setter = uberspect.getPropertySet(values, "answer", "forty-two", info);

        assertThat(setter).isNotNull();
        assertThat(setter.getMethodName()).isEqualTo("put");
        assertThat(setter.invoke(values, "forty-two")).isNull();
        assertThat(values).containsEntry("answer", "forty-two");
    }

    private static UberspectImpl newUberspect() {
        final UberspectImpl uberspect = new UberspectImpl();
        uberspect.setLog(new Log(new NullLogChute()));
        uberspect.init();
        return uberspect;
    }

    public static final class RecordingMap extends HashMap<Object, Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public Object put(final Object key, final Object value) {
            return super.put(key, value);
        }
    }
}
