/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelPropertySet;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void resolvesVelocityClassThroughCompilerGeneratedClassLiteralHelper() throws Exception {
        final Method classResolver = UberspectImpl.class.getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);

        final Object resolvedClass = classResolver.invoke(null, "org.apache.velocity.util.introspection.Info");

        assertThat(resolvedClass).isSameAs(Info.class);
    }

    @Test
    void resolvesMapClassLiteralWhenCreatingMapBackedPropertySetter() throws Exception {
        final UberspectImpl uberspect = new UberspectImpl();
        uberspect.setRuntimeLogger(new NoOpRuntimeLogger());
        final RecordingMap values = new RecordingMap();
        final Info info = new Info("map-put.vm", 1, 1);

        final VelPropertySet setter = uberspect.getPropertySet(values, "answer", "forty-two", info);

        assertThat(setter).isNotNull();
        assertThat(setter.getMethodName()).isEqualTo("put");
        assertThat(setter.invoke(values, "forty-two")).isNull();
        assertThat(values).containsEntry("answer", "forty-two");
    }

    public static final class RecordingMap extends HashMap<Object, Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public Object put(final Object key, final Object value) {
            return super.put(key, value);
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
