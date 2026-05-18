/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelMethod;
import org.apache.velocity.util.introspection.VelPropertySet;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void resolvesMethodUsingConfiguredIntrospector() throws Exception {
        final UberspectImpl uberspect = newUberspect();
        final Info info = new Info("method.vm", 1, 1);

        final VelMethod method = uberspect.getMethod(new Greeter(), "greet", new Object[] {"Ada"}, info);

        assertThat(method).isNotNull();
        assertThat(method.getMethodName()).isEqualTo("greet");
        assertThat(method.invoke(new Greeter(), new Object[] {"Ada"})).isEqualTo("Hello Ada");
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

    public static final class Greeter {
        public String greet(final String name) {
            return "Hello " + name;
        }
    }

    public static final class RecordingMap extends HashMap<Object, Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public Object put(final Object key, final Object value) {
            return super.put(key, value);
        }
    }
}
